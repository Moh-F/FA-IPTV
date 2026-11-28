package com.mohdev.faiptv.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.os.Build
import android.util.Rational
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.CalendarViewWeek
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mohdev.faiptv.MainActivity
import com.mohdev.faiptv.R
import com.mohdev.faiptv.core.AppLog
import com.mohdev.faiptv.core.Prefs
import com.mohdev.faiptv.core.Resume
import com.mohdev.faiptv.data.Kind
import com.mohdev.faiptv.data.Live
import com.mohdev.faiptv.data.PlayRequest
import com.mohdev.faiptv.data.Programme
import com.mohdev.faiptv.data.Repo
import com.mohdev.faiptv.data.Quality
import com.mohdev.faiptv.data.Alternatives
import com.mohdev.faiptv.record.RecordService
import com.mohdev.faiptv.ui.Brand
import com.mohdev.faiptv.ui.ChoiceDialog
import com.mohdev.faiptv.ui.FocusCard
import com.mohdev.faiptv.ui.ItemFocus
import com.mohdev.faiptv.ui.Nav
import com.mohdev.faiptv.ui.NetImage
import com.mohdev.faiptv.ui.Route
import com.mohdev.faiptv.ui.UiEnv
import com.mohdev.faiptv.ui.Vis
import com.mohdev.faiptv.ui.channelsOf
import com.mohdev.faiptv.ui.fmtDur
import com.mohdev.faiptv.ui.fmtSize
import com.mohdev.faiptv.ui.fmtTime
import com.mohdev.faiptv.ui.liveGroups
import com.mohdev.faiptv.ui.enterAtFirstVisible
import com.mohdev.faiptv.ui.rememberVis
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/** True while the internal player is on screen and playing (used for auto picture-in-picture). */
object PlayerState {
    var playing = false
}

fun enterPip(ctx: Context) {
    val a = ctx as? Activity ?: return
    if (Build.VERSION.SDK_INT < 26 || !UiEnv.pipSupported) return
    runCatching {
        a.enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build())
    }.onFailure { AppLog.w("pip", "failed: ${it.message}") }
}

private val digitKeys = mapOf(
    Key.Zero to 0, Key.One to 1, Key.Two to 2, Key.Three to 3, Key.Four to 4, Key.Five to 5, Key.Six to 6, Key.Seven to 7, Key.Eight to 8, Key.Nine to 9,
    Key.NumPad0 to 0, Key.NumPad1 to 1, Key.NumPad2 to 2, Key.NumPad3 to 3, Key.NumPad4 to 4,
    Key.NumPad5 to 5, Key.NumPad6 to 6, Key.NumPad7 to 7, Key.NumPad8 to 8, Key.NumPad9 to 9,
)

private fun engineFor(req: PlayRequest): String = when (Prefs.s.engine) {
    "vlc" -> "vlc"
    "exo" -> "exo"
    else -> if (req.key in Prefs.s.vlcKeys) "vlc" else "exo"
}

@Composable
fun PlayerScreen(req: PlayRequest) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cur by rememberUpdatedState(req)
    val acc = Prefs.active
    val lib = Repo.lib
    val vis: Vis? = if (lib != null && acc != null) rememberVis(lib, acc.id) else null
    val isVod = req.kind != Kind.LIVE || req.isCatchup
    val isLive = !isVod

    // ---------------- state
    var engineKind by remember { mutableStateOf(engineFor(req)) }
    var isPlaying by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(true) }
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }
    var error by remember { mutableStateOf<String?>(null) }
    var retries by remember { mutableIntStateOf(0) }
    var triedHls by remember { mutableStateOf(false) }
    var resizeIdx by remember { mutableIntStateOf(0) }
    var controls by remember { mutableStateOf(false) }
    var infoUntil by remember { mutableLongStateOf(System.currentTimeMillis() + 5000) }
    var overlay by remember { mutableStateOf(false) }
    var lastInput by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var hint by remember { mutableStateOf<String?>(null) }
    var dialog by remember { mutableStateOf<String?>(null) }  // tracks | quality | speed
    var stats by remember { mutableStateOf(Stats()) }
    var rateKbps by remember { mutableIntStateOf(0) }
    var sessionBytes by remember { mutableLongStateOf(0L) }
    var txKbps by remember { mutableIntStateOf(0) }
    var epgNow by remember { mutableStateOf<Programme?>(null) }
    var epgNext by remember { mutableStateOf<Programme?>(null) }
    var zapTarget by remember { mutableStateOf<Live?>(null) }
    var digits by remember { mutableStateOf("") }
    val rootFocus = remember { FocusRequester() }

    fun poke() { lastInput = System.currentTimeMillis() }
    fun showInfo() {
        val secs = Prefs.s.infoSeconds
        infoUntil = if (secs == 0) Long.MAX_VALUE else System.currentTimeMillis() + secs * 1000L
    }
    fun showHint(s: String) { hint = s; poke() }

    // ---------------- engine
    val loadStart = remember { longArrayOf(0L, 0L) }     // [load time, 1 = start already counted]
    val fx = remember { AudioFx() }
    var pausedAt by remember { mutableLongStateOf(0L) }
    var behindMs by remember { mutableLongStateOf(0L) }   // live pause: how far behind the live picture
    val triedAlt = remember { HashSet<String>() }
    val listener = remember {
        object : EngineListener {
            var onErr: (String, Boolean) -> Unit = { _, _ -> }
            var onEnd: () -> Unit = {}
            var onReadyHook: () -> Unit = {}
            override fun onState(playing: Boolean, buffering0: Boolean) {
                if (buffering0 && !buffering && loadStart[1] == 1L && playing) Quality.rebuffer(cur.key)
                isPlaying = playing; buffering = buffering0; PlayerState.playing = playing
            }
            override fun onReady() { retries = 0; error = null; buffering = false; onReadyHook() }
            override fun onEnded() = onEnd()
            override fun onError(code: String, unsupported: Boolean) = onErr(code, unsupported)
        }
    }
    val engine: Engine = remember(engineKind) {
        if (engineKind == "vlc") runCatching { VlcEngine(ctx, listener) as Engine }.getOrElse {
            AppLog.e("player", "VLC engine failed", it); ExoEngine(ctx, listener, isVod)
        } else ExoEngine(ctx, listener, isVod)
    }
    DisposableEffect(engine) { onDispose { engine.release() } }

    fun saveResume() {
        val r = cur
        if (r.kind == Kind.LIVE || acc == null) return
        val d = engine.duration()
        if (d <= 0) return
        Prefs.saveResume(
            Resume(
                key = r.key, accountId = acc.id, kind = r.kind.name, title = r.title, poster = r.poster,
                posMs = engine.position(), durMs = d, updated = System.currentTimeMillis(),
                seriesId = r.seriesId ?: "", episodeId = r.episodeId ?: "",
                itemId = if (r.kind == Kind.MOVIE) r.key.removePrefix("movie:") else "",
            )
        )
    }

    val queue: List<Live> = Nav.liveQueue.ifEmpty { vis?.allLive ?: emptyList() }
    fun currentLive(): Live? = cur.liveId?.let { id -> lib?.liveById?.get(id) }

    fun playLive(ch: Live) {
        val a = acc ?: return
        zapTarget = null
        Prefs.pushRecent(a.id, ch.id)
        Nav.replaceTop(Route.Player(Repo.playLive(a, ch)))
    }

    /** Back to the channel watched before (like the "last" key of a receiver). */
    fun lastChannel() {
        val a = acc ?: return
        val prev = Prefs.pd.recentLive[a.id].orEmpty().firstOrNull { it != cur.liveId } ?: return
        lib?.liveById?.get(prev)?.let { playLive(it) }
    }

    /** Zapping: the info bar shows the target at once, the stream opens after a short pause (saves connections). */
    fun zap(step: Int) {
        if (!isLive || queue.isEmpty()) return
        val from = zapTarget?.id ?: cur.liveId
        val i = queue.indexOfFirst { it.id == from }
        zapTarget = queue[((if (i < 0) 0 else i) + step).mod(queue.size)]
        showInfo()
    }

    fun nextEpisode(): Boolean {
        val a = acc ?: return false
        val q = Nav.episodeQueue
        val i = q.indexOfFirst { it.second.id == cur.episodeId }
        if (i < 0 || i + 1 >= q.size) return false
        saveResume()
        val (s, e) = q[i + 1]
        Nav.replaceTop(Route.Player(Repo.playEpisode(a, s, e).copy(startAtMs = 0)))
        return true
    }

    listener.onEnd = {
        saveResume()
        if (cur.kind == Kind.SERIES && Prefs.s.autoNext) nextEpisode()
        else if (isLive) engine.load(cur)
    }
    listener.onReadyHook = {
        if (loadStart[1] == 0L) {
            loadStart[1] = 1L
            Quality.started(cur.key, System.currentTimeMillis() - loadStart[0])
        }
        fx.apply(engine.audioSession(), Prefs.s.nightMode, Prefs.s.leveling)
    }
    listener.onErr = { code, unsupported ->
        val r = cur
        val alt = if (isLive && Prefs.s.altSource && retries >= 3) currentLive()?.let { ch ->
            Alternatives.find(vis?.allLive ?: emptyList(), ch) { Quality["live:${it.id}"].score }.firstOrNull { it.id !in triedAlt }
        } else null
        when {
            engineKind == "exo" && unsupported && !triedHls && !r.url.contains(".m3u8") -> { triedHls = true; engine.load(r, forceHls = true) }
            engineKind == "exo" && Prefs.s.engine == "auto" && (unsupported || retries >= 2) -> {
                Prefs.update { s -> s.copy(vlcKeys = (listOf(r.key) + (s.vlcKeys - r.key)).take(300)) }
                showHint(ctx.getString(R.string.player_switch_vlc))
                engineKind = "vlc"
            }
            alt != null -> {
                // same channel from another source (FHD → HD → SD / backup feed)
                Quality.failed(r.key)
                triedAlt += alt.id
                showHint(ctx.getString(R.string.player_alt_source, vis?.liveName(alt) ?: alt.name))
                playLive(alt)
            }
            isLive && retries < 5 -> {
                retries++
                scope.launch {
                    hint = ctx.getString(R.string.player_reconnecting)
                    delay(1200L * retries)
                    engine.load(cur, forceHls = triedHls)
                }
            }
            else -> { Quality.failed(r.key); error = code }
        }
    }

    // (re)load on a new request or engine
    LaunchedEffect(engine, req) {
        error = null; retries = 0; triedHls = false; epgNow = null; epgNext = null; sessionBytes = 0
        loadStart[0] = System.currentTimeMillis(); loadStart[1] = 0L; pausedAt = 0L; behindMs = 0L
        engine.setResize(resizeIdx)
        runCatching { engine.load(req) }.onFailure { error = it.message }
        showInfo()
        runCatching { rootFocus.requestFocus() }
        if (isLive && !req.isCatchup && acc != null) {
            val ch = currentLive()
            if (ch != null) {
                val list = Repo.epg(acc, ch, full = false)
                val now = System.currentTimeMillis()
                epgNow = list.firstOrNull { it.isNow(now) }
                epgNext = list.firstOrNull { it.start >= now }
            }
        }
    }

    // zapping debounce
    LaunchedEffect(zapTarget) {
        val t = zapTarget ?: return@LaunchedEffect
        delay(Prefs.s.zapDelayMs.toLong())
        playLive(t)
    }

    // number entry
    LaunchedEffect(digits) {
        if (digits.isEmpty()) return@LaunchedEffect
        delay(1500)
        val n = digits.toIntOrNull()
        val zero = digits == "0"
        digits = ""
        if (zero) { lastChannel(); return@LaunchedEffect }
        val target = n?.let { num -> queue.firstOrNull { it.num == num } ?: vis?.allLive?.firstOrNull { it.num == num } }
        if (target != null) playLive(target) else showHint("—")
    }

    val behindMsBase = remember { longArrayOf(0L) }
    fun togglePause() {
        if (engine.isPlaying()) {
            engine.pause()
            if (isLive && !cur.isCatchup) pausedAt = System.currentTimeMillis()
        } else {
            if (pausedAt > 0) { behindMsBase[0] += System.currentTimeMillis() - pausedAt; behindMs = behindMsBase[0]; pausedAt = 0 }
            engine.play()
        }
    }
    fun backToLive() { behindMsBase[0] = 0; behindMs = 0; pausedAt = 0; engine.load(cur) }

    // ticker: progress, stats, auto-hide
    LaunchedEffect(engine) {
        var tick = 0
        var lastBytes = 0L
        var lastT = System.currentTimeMillis()
        val uid = android.os.Process.myUid()
        var lastRx = android.net.TrafficStats.getUidRxBytes(uid)
        var lastTx = android.net.TrafficStats.getUidTxBytes(uid)
        while (true) {
            pos = engine.position()
            dur = engine.duration()
            val now = System.currentTimeMillis()
            if (tick % 2 == 0) {
                val st = engine.stats()
                val dt = (now - lastT).coerceAtLeast(1)
                val rx = android.net.TrafficStats.getUidRxBytes(uid)
                val tx = android.net.TrafficStats.getUidTxBytes(uid)
                if (rx >= 0 && lastRx >= 0) {
                    // whole app traffic (works for both engines): receive and send speed
                    val d = (rx - lastRx).coerceAtLeast(0)
                    rateKbps = (d * 8 / dt).toInt()
                    txKbps = ((tx - lastTx).coerceAtLeast(0) * 8 / dt).toInt()
                    sessionBytes += d
                } else {
                    val delta = (st.bytes - lastBytes).coerceAtLeast(0)
                    if (lastBytes > 0) rateKbps = (delta * 8 / dt).toInt()
                    if (st.bytes >= lastBytes) sessionBytes += delta
                }
                lastRx = rx; lastTx = tx
                lastBytes = st.bytes; lastT = now
                stats = st
            }
            if (++tick % 20 == 0) { saveResume(); if (isLive && rateKbps > 0) Quality.rate(cur.key, rateKbps) }
            if (pausedAt > 0) behindMs = System.currentTimeMillis() - pausedAt + behindMsBase[0]
            val idle = now - lastInput
            if (controls && isPlaying && idle > 6000 && dialog == null) controls = false
            if (overlay && idle > 15000 && dialog == null) overlay = false
            if (hint != null && idle > 2500) hint = null
            delay(500)
        }
    }

    // pause in background, immersive while open
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        (ctx as? MainActivity)?.setBarsHidden(true)
        var resumeOnStart = false
        val obs = androidx.lifecycle.LifecycleEventObserver { _, ev ->
            if (ev == androidx.lifecycle.Lifecycle.Event.ON_STOP) { resumeOnStart = engine.playWhenReady(); engine.pause(); saveResume() }
            if (ev == androidx.lifecycle.Lifecycle.Event.ON_START && resumeOnStart) engine.play()
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose {
            saveResume()
            fx.release()
            Quality.save()
            lifecycleOwner.lifecycle.removeObserver(obs)
            (ctx as? MainActivity)?.setBarsHidden(false)
            PlayerState.playing = false
        }
    }

    // match the display refresh rate to the video (50/60 Hz)
    val fpsKnown = stats.fps
    LaunchedEffect(fpsKnown, req.key) {
        if (!Prefs.s.afr) return@LaunchedEffect
        val fps = if (fpsKnown > 0) fpsKnown else if (isLive) Prefs.s.afrFallback.toFloat() else 0f
        (ctx as? Activity)?.let { DisplayRate.match(it, fps) }
    }
    DisposableEffect(Unit) { onDispose { (ctx as? Activity)?.let { DisplayRate.reset(it) } } }

    LaunchedEffect(controls, overlay) { if (!controls && !overlay) runCatching { rootFocus.requestFocus() } }

    BackHandler {
        when {
            UiEnv.inPip -> Nav.pop()
            digits.isNotEmpty() -> digits = ""
            overlay -> overlay = false
            controls -> controls = false
            else -> Nav.pop()
        }
    }

    val infoVisible = isLive && (System.currentTimeMillis() < infoUntil || controls || zapTarget != null) && !overlay
    // re-evaluate info visibility every second
    var clock by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { while (true) { delay(1000); clock = System.currentTimeMillis() } }
    @Suppress("UNUSED_EXPRESSION") clock

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                poke()
                val d = digitKeys[e.key]
                when {
                    e.key == Key.MediaPlayPause || e.key == Key.MediaPlay || e.key == Key.MediaPause || e.key == Key.Spacebar -> {
                        togglePause(); true
                    }
                    e.key == Key.ChannelUp || e.key == Key.PageUp -> { zap(-1); true }
                    e.key == Key.ChannelDown || e.key == Key.PageDown -> { zap(1); true }
                    e.key == Key.MediaFastForward -> { engine.seekTo(engine.position() + 30_000); true }
                    e.key == Key.MediaRewind -> { engine.seekTo((engine.position() - 30_000).coerceAtLeast(0)); true }
                    e.key == Key.MediaNext -> { if (cur.kind == Kind.SERIES) nextEpisode() else zap(1); true }
                    e.key == Key.MediaPrevious -> { zap(-1); true }
                    e.key == Key.Info || e.key == Key.Guide -> { if (System.currentTimeMillis() < infoUntil) infoUntil = 0 else showInfo(); true }
                    d != null && isLive && !overlay && dialog == null -> { digits = (digits + d).take(4); true }
                    overlay || dialog != null -> false
                    controls -> when {
                        // live: up/down always switch channels, even with the buttons visible
                        isLive && (e.key == Key.DirectionUp || e.key == Key.DirectionDown) -> { zap(if (e.key == Key.DirectionUp) -1 else 1); true }
                        else -> false
                    }
                    isLive -> when (e.key) {
                        Key.DirectionUp -> { zap(-1); true }
                        Key.DirectionDown -> { zap(1); true }
                        Key.DirectionLeft, Key.DirectionRight -> { overlay = true; true }
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.Menu, Key.ButtonA -> { controls = true; showInfo(); true }
                        else -> false
                    }
                    else -> when (e.key) {
                        Key.DirectionLeft -> { engine.seekTo((engine.position() - 10_000).coerceAtLeast(0)); showHint("-10s"); true }
                        Key.DirectionRight -> { engine.seekTo(engine.position() + 10_000); showHint("+10s"); true }
                        Key.DirectionUp, Key.DirectionDown, Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.Menu, Key.ButtonA -> { controls = true; true }
                        else -> false
                    }
                }
            }
            .focusable()
            .pointerInput(isLive) {
                detectTapGestures(
                    onTap = { controls = !controls; if (controls) showInfo(); poke() },
                    onDoubleTap = { o ->
                        if (!isLive) {
                            val fwd = o.x > size.width / 2
                            engine.seekTo((engine.position() + if (fwd) 10_000 else -10_000).coerceAtLeast(0))
                            showHint(if (fwd) "+10s" else "-10s")
                        }
                    },
                )
            }
            .pointerInput(isLive) {
                if (isLive) {
                    var total = 0f
                    detectVerticalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = { if (total < -120) zap(1) else if (total > 120) zap(-1) },
                        onVerticalDrag = { _, dy -> total += dy },
                    )
                }
            },
    ) {
        androidx.compose.runtime.key(engine) { engine.Surface(Modifier.fillMaxSize()) }

        if (buffering && error == null) {
            CircularProgressIndicator(Modifier.align(Alignment.Center).size(56.dp), color = Brand.Cyan)
        }

        hint?.let {
            Text(
                it, color = Color.White, fontSize = 18.sp,
                modifier = Modifier.align(Alignment.Center).padding(top = 130.dp)
                    .clip(RoundedCornerShape(10.dp)).background(Color(0xAA000000)).padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        if (isLive && behindMs > 2000 && !UiEnv.inPip) {
            Text(
                "⏸ -" + fmtDur(behindMs), color = Color.White, fontSize = 14.sp, style = com.mohdev.faiptv.ui.tabular,
                modifier = Modifier.align(Alignment.TopStart).padding(20.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xCC000000)).padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        if (digits.isNotEmpty()) {
            Text(
                digits, color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopEnd).padding(28.dp)
                    .clip(RoundedCornerShape(12.dp)).background(Color(0xCC000000)).padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }

        error?.let { err ->
            Column(
                Modifier.align(Alignment.Center).clip(RoundedCornerShape(16.dp)).background(Color(0xE6111A2B)).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.player_error, err), color = Color.White)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { error = null; retries = 0; engine.load(cur, triedHls) }) {
                        Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.retry))
                    }
                    OutlinedButton(onClick = { error = null; engineKind = if (engineKind == "vlc") "exo" else "vlc" }) {
                        Text(if (engineKind == "vlc") "ExoPlayer" else "VLC")
                    }
                    OutlinedButton(onClick = { External.open(ctx, cur.copy(startAtMs = engine.position())) }) {
                        Text(stringResource(R.string.action_external))
                    }
                }
            }
        }

        if (!UiEnv.inPip) {
            val opacity = Prefs.s.infoOpacity / 100f
            val scale = when (Prefs.s.infoSize) { "s" -> 0.85f; "l" -> 1.2f; else -> 1f }
            val shownLive = zapTarget ?: currentLive()
            val info: @Composable () -> Unit = {
                InfoBar(
                    ch = shownLive, title = if (shownLive != null && vis != null) vis.liveName(shownLive) else cur.title,
                    sub = cur.subtitle, now = if (zapTarget == null) epgNow else null, next = if (zapTarget == null) epgNext else null,
                    stats = if (zapTarget == null) stats else null, rateKbps = rateKbps, txKbps = txKbps, sessionBytes = sessionBytes,
                    engine = engine.name, opacity = opacity, scale = scale, catchup = cur.isCatchup,
                )
            }
            if (Prefs.s.infoTop) {
                AnimatedVisibility(infoVisible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) { info() }
            }
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                if (!Prefs.s.infoTop) AnimatedVisibility(infoVisible, enter = fadeIn(), exit = fadeOut()) { info() }
                AnimatedVisibility(controls && !overlay, enter = fadeIn(), exit = fadeOut()) {
                    Controls(
                        req = cur, isVod = isVod, isPlaying = isPlaying, pos = pos, dur = dur, engineName = engine.name, poke = ::poke,
                        onPlayPause = { togglePause() },
                        behindMs = behindMs, onLive = { backToLive() }, onLast = { lastChannel() },
                        onMulti = { cur.liveId?.let { id -> Nav.push(Route.Multi(listOf(id) + queue.map { it.id }.filter { it != id }.take(1))) } },
                        onSeek = { engine.seekTo(it) },
                        onZap = ::zap,
                        onChannels = { controls = false; overlay = true },
                        onNext = { nextEpisode() },
                        hasNext = cur.kind == Kind.SERIES && Nav.episodeQueue.indexOfFirst { it.second.id == cur.episodeId }.let { it >= 0 && it + 1 < Nav.episodeQueue.size },
                        onDialog = { dialog = it },
                        onAspect = { resizeIdx = (resizeIdx + 1) % 3; engine.setResize(resizeIdx); showHint(ctx.getString(listOf(R.string.aspect_fit, R.string.aspect_zoom, R.string.aspect_fill)[resizeIdx])) },
                        onEngine = { engineKind = if (engineKind == "vlc") "exo" else "vlc"; showHint(if (engineKind == "vlc") "VLC" else "ExoPlayer") },
                        onPip = { enterPip(ctx) },
                        onExternal = { engine.pause(); External.open(ctx, cur.copy(startAtMs = engine.position())) },
                        onRecord = {
                            (ctx as? MainActivity)?.askNotificationPermission()
                            RecordService.startNow(ctx, cur.url, cur.title, cur.userAgent, 0L)
                            showHint(ctx.getString(R.string.rec_started, cur.title))
                        },
                        onInfo = { if (System.currentTimeMillis() < infoUntil) infoUntil = 0 else showInfo() },
                        onBack = { Nav.pop() },
                        onGuide = { Nav.push(Route.Guide(Nav.liveGroup)) },
                        tech = buildList {
                            if (stats.width > 0) add("${stats.width}×${stats.height} ${qualityLabel(stats.height)}")
                            if (Prefs.s.showSpeeds) { add(String.format(Locale.US, "▼ %.1f Mb/s", rateKbps / 1000f)); add(String.format(Locale.US, "▲ %.0f kb/s", txKbps.toFloat())) }
                            add(engine.name)
                        }.joinToString("  ·  "),
                    )
                }
            }
            if (isVod && dur > 0 && !controls && Prefs.s.vodMiniBar) {
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp).background(Color(0x33FFFFFF))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth((pos.toFloat() / dur).coerceIn(0f, 1f)).background(Brand.Cyan.copy(alpha = 0.8f)))
                }
            }
            if (isLive && vis != null) {
                AnimatedVisibility(
                    overlay, enter = slideInHorizontally { -it } + fadeIn(), exit = slideOutHorizontally { -it } + fadeOut(),
                    modifier = Modifier.align(Alignment.CenterStart).fillMaxHeight(),
                ) {
                    ChannelOverlay(vis, cur.liveId, Prefs.s.overlayOpacity / 100f, poke = ::poke, onPick = { ch, list, g ->
                        if (ch.id == cur.liveId) overlay = false else { Nav.liveQueue = list; Nav.liveGroup = g; playLive(ch) }
                    })
                }
            }
        }
    }

    when (dialog) {
        "tracks" -> TracksDialog(engine) { dialog = null; poke() }
        "quality" -> QualityDialog(engine) { dialog = null; poke() }
        "speed" -> {
            val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
            ChoiceDialog(stringResource(R.string.player_speed), speeds.map { it to "${it}x" }, engine.speed(), onDismiss = { dialog = null }) {
                engine.setSpeed(it); dialog = null
            }
        }
    }
}

/** Bottom (or top) bar while zapping: channel, programme, real picture/stream data. */
@Composable
private fun InfoBar(
    ch: Live?, title: String, sub: String, now: Programme?, next: Programme?, stats: Stats?, rateKbps: Int, txKbps: Int, sessionBytes: Long,
    engine: String, opacity: Float, scale: Float, catchup: Boolean,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(16.dp)).background(Color.Black.copy(alpha = opacity)).padding((14 * scale).dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (ch != null && ch.num > 0) {
                Text(ch.num.toString(), color = Brand.Cyan, fontSize = (30 * scale).sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width((12 * scale).dp))
            }
            if (ch != null) {
                Box(Modifier.size(width = (78 * scale).dp, height = (48 * scale).dp).clip(RoundedCornerShape(8.dp)).background(Color(0x22FFFFFF))) {
                    NetImage(ch.logo, Modifier.fillMaxSize().padding(4.dp), ContentScale.Fit, fallback = ch.name)
                }
                Spacer(Modifier.width((12 * scale).dp))
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!catchup && ch != null) {
                        Text(
                            stringResource(R.string.player_live), color = Color.White, fontSize = (10 * scale).sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Brand.Live).padding(horizontal = 6.dp, vertical = 1.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(title, color = Color.White, fontSize = (20 * scale).sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                val line = now?.let { "${fmtTime(it.start)}–${fmtTime(it.end)}  ${it.title}" } ?: sub
                if (line.isNotBlank()) Text(line, color = Color(0xFFD1D9E6), fontSize = (14 * scale).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                now?.let { p ->
                    val t = System.currentTimeMillis()
                    LinearProgressIndicator(
                        progress = { ((t - p.start).toFloat() / (p.end - p.start)).coerceIn(0f, 1f) },
                        modifier = Modifier.padding(top = 4.dp).widthIn(max = 420.dp).fillMaxWidth().height(3.dp),
                        color = Brand.Cyan, trackColor = Color(0x44FFFFFF),
                    )
                }
                next?.let { Text("${stringResource(R.string.epg_next)}: ${fmtTime(it.start)}  ${it.title}", color = Color(0xFF9FB0C8), fontSize = (12 * scale).sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            com.mohdev.faiptv.ui.ClockView(Color.White, 18 * scale)
        }
        if (Prefs.s.infoTech && stats != null) {
            // fixed slots and widths: nothing jumps while the numbers change
            val dash = "—"
            val chips = buildList<Pair<String, Int>> {
                add((if (stats.width > 0) "${stats.width}×${stats.height}" else dash) to 86)
                add(qualityLabel(stats.height).ifBlank { dash } to 44)
                add((if (stats.fps > 0) String.format(Locale.US, "%.0f fps", stats.fps) else dash) to 56)
                add(stats.vcodec.ifBlank { dash } to 60)
                add((if (stats.acodec.isNotBlank()) stats.acodec + when (stats.channels) { 6 -> " 5.1"; 8 -> " 7.1"; 2 -> " 2.0"; 1 -> " 1.0"; else -> "" } else dash) to 76)
                if (Prefs.s.showSpeeds) {
                    add(String.format(Locale.US, "▼ %.1f Mb/s", rateKbps / 1000f) to 96)
                    add(String.format(Locale.US, "▲ %d kb/s", txKbps) to 88)
                    add((if (stats.netEstimateKbps > 0) String.format(Locale.US, "⇅ %.1f Mb/s", stats.netEstimateKbps / 1000f) else "⇅ $dash") to 96)
                }
                add((if (sessionBytes > 0) fmtSize(sessionBytes) else dash) to 70)
                add(engine to 76)
                if (stats.dropped > 0) add("⚠ ${stats.dropped}" to 56)
            }
            Row(Modifier.padding(top = (8 * scale).dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                chips.forEach { (text, w) ->
                    Box(
                        Modifier.width((w * scale).dp).clip(RoundedCornerShape(6.dp)).background(Color(0x33FFFFFF)).padding(vertical = 2.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text, color = Color.White, fontSize = (11 * scale).sp, maxLines = 1, style = com.mohdev.faiptv.ui.tabular)
                    }
                }
            }
        }
    }
}

/** Semi-transparent channel list over the running picture: categories + channels, navigable with the remote. */
@Composable
private fun ChannelOverlay(vis: Vis, currentId: String?, opacity: Float, poke: () -> Unit, onPick: (Live, List<Live>, String) -> Unit) {
    val groups = liveGroups(vis, withNew = false)
    var group by remember { mutableStateOf(Nav.liveGroup.takeIf { g -> groups.any { it.id == g } } ?: com.mohdev.faiptv.ui.CAT_ALL) }
    val channels = remember(vis, group) { channelsOf(vis, group) }
    val chState = rememberLazyListState()
    val chFocus = remember { ItemFocus() }
    val gState = rememberLazyListState()
    val gFocus = remember { ItemFocus() }
    LaunchedEffect(channels) {
        val idx = channels.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
        if (idx > 3) chState.scrollToItem(idx - 3)
        delay(80)
        runCatching { chFocus.req(channels.getOrNull(idx)?.id ?: "").requestFocus() }
    }
    val bg = Color(0xFF0B1220).copy(alpha = opacity)
    Column(Modifier.fillMaxHeight().background(bg)) {
    Row(Modifier.width(540.dp).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.player_channels), color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        com.mohdev.faiptv.ui.ClockView(Color.White, 16f)
    }
    Row(Modifier.weight(1f)) {
        LazyColumn(
            Modifier.width(210.dp).fillMaxHeight().enterAtFirstVisible(gState, gFocus),
            state = gState, contentPadding = PaddingValues(8.dp),
        ) {
            items(groups, key = { it.id }) { g ->
                FocusCard(
                    onClick = { group = g.id; poke() }, onFocus = poke, modifier = Modifier.fillMaxWidth(),
                    selected = g.id == group, color = Color.Transparent, zoom = 1f, focusRequester = gFocus.req(g.id),
                ) {
                    Text(
                        g.label, color = if (g.id == group) Brand.Cyan else Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        fontSize = 13.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                    )
                }
            }
        }
        LazyColumn(
            Modifier.width(330.dp).fillMaxHeight().enterAtFirstVisible(chState, chFocus),
            state = chState, contentPadding = PaddingValues(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(channels, key = { it.id }) { ch ->
                FocusCard(
                    onClick = { onPick(ch, channels, group) }, onFocus = poke, modifier = Modifier.fillMaxWidth(),
                    selected = ch.id == currentId, color = Color.Transparent, zoom = 1f, focusRequester = chFocus.req(ch.id),
                ) {
                    Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (ch.num > 0) ch.num.toString() else "", color = Color(0xFF9FB0C8), fontSize = 12.sp, modifier = Modifier.width(36.dp))
                        Box(Modifier.size(width = 48.dp, height = 30.dp).clip(RoundedCornerShape(4.dp)).background(Color(0x22FFFFFF))) {
                            NetImage(ch.logo, Modifier.fillMaxSize().padding(2.dp), ContentScale.Fit, fallback = ch.name)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(vis.liveName(ch), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                            Repo.nowTitle(ch)?.let { Text(it, color = Color(0xFF9FB0C8), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        }
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun Controls(
    req: PlayRequest, isVod: Boolean, isPlaying: Boolean, pos: Long, dur: Long, engineName: String, poke: () -> Unit,
    onPlayPause: () -> Unit, onSeek: (Long) -> Unit, onZap: (Int) -> Unit, onChannels: () -> Unit, onNext: () -> Unit, hasNext: Boolean,
    onDialog: (String) -> Unit, onAspect: () -> Unit, onEngine: () -> Unit, onPip: () -> Unit, onExternal: () -> Unit,
    onRecord: () -> Unit, onInfo: () -> Unit, onBack: () -> Unit, onGuide: () -> Unit, tech: String,
    behindMs: Long, onLive: () -> Unit, onLast: () -> Unit, onMulti: () -> Unit,
) {
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { playFocus.requestFocus() } }
    val acc = Prefs.active
    val isLive = !isVod
    Column(
        Modifier.fillMaxWidth().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000))))
            .padding(horizontal = 24.dp, vertical = 14.dp),
    ) {
        if (isVod && dur > 0) {
            var dragging by remember { mutableStateOf(false) }
            var dragValue by remember { mutableFloatStateOf(0f) }
            Slider(
                value = if (dragging) dragValue else pos.toFloat().coerceIn(0f, dur.toFloat()),
                onValueChange = { dragging = true; dragValue = it; poke() },
                onValueChangeFinished = { onSeek(dragValue.toLong()); dragging = false },
                valueRange = 0f..dur.toFloat(),
                colors = SliderDefaults.colors(thumbColor = Brand.Cyan, activeTrackColor = Brand.Cyan, inactiveTrackColor = Color(0x55FFFFFF)),
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(fmtDur(if (dragging) dragValue.toLong() else pos) + " / " + fmtDur(dur), color = Color.White, fontSize = 13.sp, style = com.mohdev.faiptv.ui.tabular)
                Spacer(Modifier.weight(1f))
                Box(Modifier.width(380.dp), contentAlignment = Alignment.Center) {
                    Text(tech, color = Color(0xFFCBD5E1), fontSize = 12.sp, maxLines = 1, style = com.mohdev.faiptv.ui.tabular)
                }
                Spacer(Modifier.width(12.dp))
                Text("-" + fmtDur(dur - pos), color = Color.White, fontSize = 13.sp, style = com.mohdev.faiptv.ui.tabular)
            }
        }
        LazyRow(
            Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
        ) {
            item { CtlButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_close), poke, onBack) }
            if (isLive) item { CtlButton(Icons.AutoMirrored.Filled.List, stringResource(R.string.player_channels), poke, onChannels, label = true) }
            if (isLive) item { CtlButton(Icons.Default.KeyboardArrowUp, "-", poke, { onZap(-1) }) }
            if (isVod) item { CtlButton(Icons.Default.FastRewind, "-10", poke, { onSeek((pos - 10_000).coerceAtLeast(0)) }) }
            item { CtlButton(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "play", poke, onPlayPause, big = true, focus = playFocus) }
            if (isVod) item { CtlButton(Icons.Default.FastForward, "+10", poke, { onSeek(pos + 10_000) }) }
            if (isLive) item { CtlButton(Icons.Default.KeyboardArrowDown, "+", poke, { onZap(1) }) }
            if (hasNext) item { CtlButton(Icons.Default.SkipNext, stringResource(R.string.player_next_ep), poke, onNext, label = true) }
            item { CtlButton(Icons.Default.Subtitles, stringResource(R.string.player_tracks), poke, { onDialog("tracks") }, label = true) }
            item { CtlButton(Icons.Default.HighQuality, stringResource(R.string.player_video), poke, { onDialog("quality") }, label = true) }
            item { CtlButton(Icons.Default.AspectRatio, stringResource(R.string.player_aspect), poke, onAspect, label = true) }
            if (isVod) item { CtlButton(Icons.Default.Speed, stringResource(R.string.player_speed), poke, { onDialog("speed") }, label = true) }
            if (isLive && behindMs > 2000) item { CtlButton(Icons.Default.SkipNext, stringResource(R.string.player_back_live) + " (-" + fmtDur(behindMs) + ")", poke, onLive, label = true, tint = Brand.Live) }
            if (isLive) item { CtlButton(Icons.Default.History, stringResource(R.string.player_last), poke, onLast, label = true) }
            if (isLive) item { CtlButton(Icons.Default.GridView, stringResource(R.string.player_multi), poke, onMulti, label = true) }
            if (isLive) item { CtlButton(Icons.Default.Info, stringResource(R.string.player_info), poke, onInfo, label = true) }
            if (isLive) item { CtlButton(Icons.Default.CalendarViewWeek, stringResource(R.string.guide), poke, onGuide, label = true) }
            if (isLive && !req.isCatchup) {
                item { CtlButton(Icons.Default.FiberManualRecord, stringResource(R.string.action_record), poke, onRecord, label = true, tint = Brand.Live) }
                if (acc != null && req.liveId != null && !Prefs.isKids) item {
                    val key = "live:${req.liveId}"
                    val fav = Prefs.isFav(acc.id, key)
                    CtlButton(
                        if (fav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        stringResource(if (fav) R.string.action_fav_remove else R.string.action_fav_add), poke,
                        { Prefs.toggleFav(acc.id, key) }, label = true, tint = if (fav) Brand.Pink else Color.White,
                    )
                }
            }
            item { CtlButton(Icons.Default.Memory, engineName, poke, onEngine, label = true) }
            if (UiEnv.pipSupported) item { CtlButton(Icons.Default.PictureInPictureAlt, stringResource(R.string.player_pip), poke, onPip, label = true) }
            item { CtlButton(Icons.Default.OpenInNew, stringResource(R.string.action_external), poke, onExternal, label = true) }
        }
    }
}

@Composable
private fun CtlButton(
    icon: ImageVector, desc: String, poke: () -> Unit, onClick: () -> Unit,
    big: Boolean = false, focus: FocusRequester? = null, label: Boolean = false, tint: Color = Color.White,
) {
    var focused by remember { mutableStateOf(false) }
    // fixed width: the label under the focused button must not push the other buttons around
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(if (big) 76.dp else 64.dp)) {
        IconButton(
            onClick = { poke(); onClick() },
            modifier = Modifier
                .then(if (focus != null) Modifier.focusRequester(focus) else Modifier)
                .onFocusChanged { focused = it.isFocused; if (it.isFocused) poke() }
                .size(if (big) 60.dp else 46.dp)
                .clip(CircleShape)
                .background(if (focused) Brand.Cyan else Color(0x40FFFFFF)),
        ) {
            Icon(icon, desc, tint = if (focused) Color.Black else tint, modifier = Modifier.size(if (big) 34.dp else 24.dp))
        }
        Text(
            if (label && focused) desc else " ", color = Color.White, fontSize = 11.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun TracksDialog(engine: Engine, onDismiss: () -> Unit) {
    val t = remember { engine.tracks() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.player_tracks)) },
        text = {
            LazyColumn(Modifier.height(380.dp)) {
                item { TrackHeader(stringResource(R.string.player_audio)) }
                if (t.audio.isEmpty()) item { Text("—") }
                items(t.audio.size) { i -> val o = t.audio[i]; TrackRow(o.label, o.selected) { o.apply(); onDismiss() } }
                item { TrackHeader(stringResource(R.string.player_subtitles)) }
                item { TrackRow(stringResource(R.string.player_off), t.textIsOff) { t.textOff(); onDismiss() } }
                items(t.text.size) { i -> val o = t.text[i]; TrackRow(o.label, o.selected && !t.textIsOff) { o.apply(); onDismiss() } }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}

/** Like YouTube: automatic, maximum, or a fixed resolution (helps on weak connections). */
@Composable
private fun QualityDialog(engine: Engine, onDismiss: () -> Unit) {
    val t = remember { engine.tracks() }
    val presets = listOf(
        "auto" to stringResource(R.string.quality_auto), "max" to stringResource(R.string.quality_max),
        "1080" to "1080p", "720" to "720p", "480" to "480p", "360" to "360p",
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.player_video)) },
        text = {
            LazyColumn(Modifier.height(380.dp)) {
                items(presets.size) { i ->
                    val (k, label) = presets[i]
                    TrackRow(label, false) { engine.setQuality(k); if (k == "auto") t.videoAuto(); onDismiss() }
                }
                if (t.video.size > 1) {
                    item { TrackHeader(stringResource(R.string.quality_stream)) }
                    items(t.video.size) { i -> val o = t.video[i]; TrackRow(o.label, o.selected && !t.videoIsAuto) { o.apply(); onDismiss() } }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}

@Composable
private fun TrackHeader(t: String) {
    Text(t, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
}

@Composable
private fun TrackRow(label: String, selected: Boolean, onClick: () -> Unit) {
    FocusCard(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), color = Color.Transparent, zoom = 1f) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = null)
            Spacer(Modifier.width(8.dp))
            Text(label)
        }
    }
}
