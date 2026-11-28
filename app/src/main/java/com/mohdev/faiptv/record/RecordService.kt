package com.mohdev.faiptv.record

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Environment
import android.os.IBinder
import androidx.compose.runtime.mutableStateListOf
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.mohdev.faiptv.MainActivity
import com.mohdev.faiptv.R
import com.mohdev.faiptv.core.AppLog
import com.mohdev.faiptv.core.Prefs
import com.mohdev.faiptv.core.ScheduledRec
import com.mohdev.faiptv.data.Net
import com.mohdev.faiptv.data.Repo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.coroutineContext

data class ActiveRec(val id: String, val title: String, val file: File, val started: Long, val endAt: Long)

/**
 * Records live streams to app storage (Movies/FA-IPTV). Plain MPEG-TS streams are copied byte by byte,
 * HLS (m3u8) streams are recorded by appending their segments. Runs as a foreground service.
 */
class RecordService : Service() {
    companion object {
        private const val CHANNEL = "recordings"
        private const val NOTIF_ID = 4711
        const val ACTION_START = "start"
        const val ACTION_STOP = "stop"

        val active = mutableStateListOf<ActiveRec>()

        fun dir(ctx: Context): File =
            File(ctx.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: ctx.filesDir, "FA-IPTV").apply { mkdirs() }

        fun startNow(ctx: Context, url: String, title: String, ua: String, endAt: Long, schedId: String = "") {
            val i = Intent(ctx, RecordService::class.java).setAction(ACTION_START)
                .putExtra("url", url).putExtra("title", title).putExtra("ua", ua).putExtra("end", endAt).putExtra("sched", schedId)
            ContextCompat.startForegroundService(ctx, i)
        }

        fun stop(ctx: Context, id: String) {
            ctx.startService(Intent(ctx, RecordService::class.java).setAction(ACTION_STOP).putExtra("id", id))
        }

        /** Schedules a programme recording with an exact alarm (1 min before start, 2 min after end). */
        fun schedule(ctx: Context, rec: ScheduledRec, store: Boolean = true) {
            if (store) Prefs.addScheduled(rec)
            val am = ctx.getSystemService(AlarmManager::class.java)
            val pi = alarmIntent(ctx, rec.id)
            val at = (rec.start - 60_000).coerceAtLeast(System.currentTimeMillis() + 2_000)
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            AppLog.i("rec", "scheduled ${rec.title} at $at")
        }

        fun cancelScheduled(ctx: Context, id: String) {
            ctx.getSystemService(AlarmManager::class.java).cancel(alarmIntent(ctx, id))
            Prefs.removeScheduled(id)
        }

        private fun alarmIntent(ctx: Context, id: String): PendingIntent =
            PendingIntent.getBroadcast(
                ctx, id.hashCode(), Intent(ctx, RecordAlarmReceiver::class.java).putExtra("sched", id),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        fun safeName(s: String) = s.replace(Regex("""[\\/:*?"<>|\s]+"""), "_").take(60).trim('_').ifBlank { "record" }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = HashMap<String, Job>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground()
        when (intent?.action) {
            ACTION_START -> {
                val url = intent.getStringExtra("url") ?: return START_NOT_STICKY
                val title = intent.getStringExtra("title") ?: "record"
                val ua = intent.getStringExtra("ua") ?: ""
                val end = intent.getLongExtra("end", 0L)
                intent.getStringExtra("sched")?.takeIf { it.isNotBlank() }?.let { Prefs.removeScheduled(it) }
                begin(url, title, ua, end)
            }
            ACTION_STOP -> {
                val id = intent.getStringExtra("id")
                if (id == null) jobs.values.forEach { it.cancel() } else jobs[id]?.cancel()
            }
        }
        if (jobs.isEmpty()) stopSelfSafely()
        return START_NOT_STICKY
    }

    private fun begin(url: String, title: String, ua: String, endAt: Long) {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        val file = File(dir(this), "${safeName(title)}_$stamp.ts")
        val rec = ActiveRec(UUID.randomUUID().toString(), title, file, System.currentTimeMillis(), endAt)
        active.add(rec)
        AppLog.i("rec", "start ${rec.title}")
        val job = scope.launch {
            try {
                FileOutputStream(file, true).use { out -> record(url, ua, endAt, out) }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) AppLog.e("rec", "recording failed", e)
            } finally {
                AppLog.i("rec", "stop ${rec.title} (${file.length()} bytes)")
                if (file.length() == 0L) file.delete()
                active.removeAll { it.id == rec.id }
                synchronized(jobs) { jobs.remove(rec.id) }
                updateNotification()
                if (synchronized(jobs) { jobs.isEmpty() }) stopSelfSafely()
            }
        }
        synchronized(jobs) { jobs[rec.id] = job }
        updateNotification()
    }

    private fun timeUp(endAt: Long) = endAt > 0 && System.currentTimeMillis() >= endAt

    private suspend fun record(url: String, ua: String, endAt: Long, out: OutputStream) {
        var failures = 0
        while (coroutineContext.isActive && !timeUp(endAt) && failures < 8) {
            try {
                val req = Request.Builder().url(url).header("User-Agent", Net.userAgent(ua)).build()
                val resp = Net.streamClient.newCall(req).execute()
                resp.use { r ->
                    if (!r.isSuccessful) throw Net.HttpError(r.code)
                    val body = r.body!!
                    val src = body.byteStream()
                    val head = ByteArray(7)
                    val n = src.read(head)
                    val isHls = (n >= 7 && String(head, 0, 7, Charsets.US_ASCII) == "#EXTM3U") ||
                        (body.contentType()?.subtype?.contains("mpegurl", true) == true)
                    if (isHls) {
                        val text = (if (n > 0) String(head, 0, n) else "") + src.bufferedReader().readText()
                        recordHls(r.request.url.toString(), text, ua, endAt, out)
                        return
                    }
                    if (n > 0) out.write(head, 0, n)
                    val buf = ByteArray(64 * 1024)
                    while (coroutineContext.isActive && !timeUp(endAt)) {
                        val k = src.read(buf)
                        if (k < 0) break
                        out.write(buf, 0, k)
                        failures = 0
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                failures++
                AppLog.w("rec", "stream interrupted ($failures): ${e.message}")
                delay(2000L * failures)
            }
        }
    }

    private suspend fun recordHls(playlistUrl: String, first: String, ua: String, endAt: Long, out: OutputStream) {
        var mediaUrl = playlistUrl
        var text = first
        if (text.contains("#EXT-X-STREAM-INF")) {
            // master playlist: pick the highest bandwidth variant
            val lines = text.lines()
            var best = -1L
            var bestUri: String? = null
            for (i in lines.indices) {
                val l = lines[i]
                if (l.startsWith("#EXT-X-STREAM-INF")) {
                    val bw = Regex("BANDWIDTH=(\\d+)").find(l)?.groupValues?.get(1)?.toLongOrNull() ?: 0
                    val uri = lines.drop(i + 1).firstOrNull { it.isNotBlank() && !it.startsWith("#") }
                    if (uri != null && bw > best) { best = bw; bestUri = uri.trim() }
                }
            }
            mediaUrl = resolve(playlistUrl, bestUri ?: return)
            text = Net.get(mediaUrl, ua)
        }
        val seen = LinkedHashSet<String>()
        var failures = 0
        while (coroutineContext.isActive && !timeUp(endAt)) {
            if (text.contains("#EXT-X-KEY") && !text.contains("METHOD=NONE")) {
                AppLog.w("rec", "encrypted HLS cannot be recorded"); return
            }
            val target = Regex("#EXT-X-TARGETDURATION:(\\d+)").find(text)?.groupValues?.get(1)?.toLongOrNull() ?: 6
            for (line in text.lines()) {
                val l = line.trim()
                if (l.isEmpty() || l.startsWith("#")) continue
                val seg = resolve(mediaUrl, l)
                if (!seen.add(seg)) continue
                Net.open(seg, ua).use { r -> r.body!!.byteStream().copyTo(out, 64 * 1024) }
                if (!coroutineContext.isActive || timeUp(endAt)) return
            }
            while (seen.size > 500) seen.remove(seen.first())
            if (text.contains("#EXT-X-ENDLIST")) return
            delay((target * 1000 / 2).coerceAtLeast(1000))
            text = try { Net.get(mediaUrl, ua).also { failures = 0 } } catch (e: Exception) {
                if (++failures > 8) throw e
                delay(2000); continue
            }
        }
    }

    private fun resolve(base: String, ref: String): String =
        if (ref.startsWith("http://") || ref.startsWith("https://")) ref
        else base.toHttpUrl().resolve(ref)?.toString() ?: ref

    private fun startInForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.rec_notif_channel), NotificationManager.IMPORTANCE_LOW))
        }
        val n = notification()
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else startForeground(NOTIF_ID, n)
    }

    private fun notification(): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(
            this, 1, Intent(this, RecordService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE,
        )
        val titles = active.joinToString(", ") { it.title }
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setContentTitle(getString(R.string.rec_active))
            .setContentText(titles)
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, getString(R.string.action_stop), stop)
            .build()
    }

    private fun updateNotification() {
        runCatching { getSystemService(NotificationManager::class.java).notify(NOTIF_ID, notification()) }
    }

    private fun stopSelfSafely() {
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE) else @Suppress("DEPRECATION") stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}

/** Fired by AlarmManager shortly before a scheduled programme starts. */
class RecordAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val id = intent.getStringExtra("sched") ?: return
        val rec = Prefs.data.scheduled.firstOrNull { it.id == id } ?: return
        val acc = Prefs.data.accounts.firstOrNull { it.id == rec.accountId } ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                var lib = Repo.lib
                if (lib == null || lib.raw.accountId != acc.id) {
                    Repo.loadCache(acc)
                    lib = Repo.lib
                }
                val ch = lib?.liveById?.get(rec.liveId)
                if (ch == null) { AppLog.w("rec", "scheduled channel not found"); Prefs.removeScheduled(id); return@launch }
                RecordService.startNow(ctx, Repo.liveUrl(acc, ch), "${rec.channel} - ${rec.title}", ch.userAgent.ifBlank { acc.userAgent }, rec.end + 120_000, id)
            } catch (e: Exception) {
                AppLog.e("rec", "alarm failed", e)
            } finally {
                pending.finish()
            }
        }
    }
}
