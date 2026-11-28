package com.mohdev.faiptv.record

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.mohdev.faiptv.MainActivity
import com.mohdev.faiptv.R
import com.mohdev.faiptv.core.AppLog
import com.mohdev.faiptv.core.Prefs
import com.mohdev.faiptv.core.Reminder
import com.mohdev.faiptv.ui.Nav
import com.mohdev.faiptv.ui.UiEnv

/** Programme reminders and "open this channel at …". Exact alarms, re-armed after a reboot. */
object Reminders {
    private const val CHANNEL = "reminders"
    const val EXTRA_LIVE = "play_live"
    const val EXTRA_ACCOUNT = "play_account"

    fun add(ctx: Context, r: Reminder) {
        Prefs.addReminder(r)
        arm(ctx, r)
    }

    fun cancel(ctx: Context, id: String) {
        ctx.getSystemService(AlarmManager::class.java).cancel(pi(ctx, id))
        Prefs.removeReminder(id)
    }

    /** Fires [Prefs.s.remindMin] minutes before for reminders, exactly on time for automatic start. */
    fun fireAt(r: Reminder) = if (r.open) r.at else r.at - Prefs.s.remindMin * 60_000L

    private fun arm(ctx: Context, r: Reminder) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val at = fireAt(r).coerceAtLeast(System.currentTimeMillis() + 2_000)
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi(ctx, r.id))
        else am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi(ctx, r.id))
    }

    fun rearmAll(ctx: Context) {
        val now = System.currentTimeMillis()
        Prefs.data.reminders.forEach { if (it.at > now - 60_000) arm(ctx, it) else Prefs.removeReminder(it.id) }
        Prefs.data.scheduled.forEach { if (it.end > now) RecordService.schedule(ctx, it, store = false) }
    }

    private fun pi(ctx: Context, id: String): PendingIntent = PendingIntent.getBroadcast(
        ctx, ("rem" + id).hashCode(), Intent(ctx, ReminderReceiver::class.java).putExtra("id", id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun playIntent(ctx: Context, r: Reminder): Intent = Intent(ctx, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        .putExtra(EXTRA_LIVE, r.liveId).putExtra(EXTRA_ACCOUNT, r.accountId)

    fun notify(ctx: Context, r: Reminder) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, ctx.getString(R.string.rem_channel), NotificationManager.IMPORTANCE_HIGH))
        }
        val open = PendingIntent.getActivity(ctx, r.id.hashCode(), playIntent(ctx, r), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(r.channel)
            .setContentText(r.title)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        runCatching { nm.notify(r.id.hashCode(), n) }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val id = intent.getStringExtra("id") ?: return
        val r = Prefs.data.reminders.firstOrNull { it.id == id } ?: return
        Prefs.removeReminder(id)
        AppLog.i("reminder", if (r.open) "auto start" else "reminder")
        Handler(Looper.getMainLooper()).post {
            if (UiEnv.foreground) {
                // app is open: switch directly (auto start) or ask (reminder)
                if (r.open) Nav.pendingPlay = r.accountId to r.liveId else Nav.reminder = r
            } else {
                Reminders.notify(ctx, r)
                // automatic start: bring the app to the front (allowed on TVs / when the app may draw over others)
                if (r.open) runCatching { ctx.startActivity(Reminders.playIntent(ctx, r)) }
            }
        }
    }
}

/** Re-arms reminders and scheduled recordings after the device restarted. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            Reminders.rearmAll(ctx)
        }
    }
}
