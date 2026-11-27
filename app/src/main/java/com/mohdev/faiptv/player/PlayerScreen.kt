package com.mohdev.faiptv.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.os.Build
import android.util.Rational
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.mohdev.faiptv.R
import com.mohdev.faiptv.core.AppLog
import com.mohdev.faiptv.core.Prefs
import com.mohdev.faiptv.core.Resume
import com.mohdev.faiptv.data.Kind
import com.mohdev.faiptv.data.Net
import com.mohdev.faiptv.data.PlayRequest
import com.mohdev.faiptv.data.Programme
import com.mohdev.faiptv.data.Repo
import com.mohdev.faiptv.record.RecordService
import com.mohdev.faiptv.ui.Brand
import com.mohdev.faiptv.ui.NetImage
import com.mohdev.faiptv.ui.Nav
import com.mohdev.faiptv.ui.UiEnv
import com.mohdev.faiptv.ui.fmtDur
import com.mohdev.faiptv.ui.fmtTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/** True while the internal player is on screen and playing (used for auto picture-in-picture). */
object PlayerState {
    var playing = false
}

object PlayerFactory {
    fun create(ctx: Context): ExoPlayer {
        val (min, max, play, rebuf) = when (Prefs.s.buffer) {
            "small" -> listOf(5_000, 20_000, 1_000, 2_000)
            "large" -> listOf(30_000, 120_000, 3_000, 8_000)
            else -> listOf(15_000, 50_000, 2_000, 4_000)
        }
        val load = DefaultLoadControl.Builder().setBufferDurationsMs(min, max, play, rebuf).build()
        val renderers = DefaultRenderersFactory(ctx)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            .setEnableDecoderFallback(true)
        val audioAttr = androidx.media3.common.AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build()
        return ExoPlayer.Builder(ctx, renderers)
            .setLoadControl(load)
            .setAudioAttributes(audioAttr, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
    }

    fun load(ctx: Context, p: ExoPlayer, r: PlayRequest, forceHls: Boolean = false) {
        val ds = OkHttpDataSource.Factory(Net.streamClient).setUserAgent(Net.userAgent(r.userAgent))
        if (r.referrer.isNotBlank()) ds.setDefaultRequestProperties(mapOf("Referer" to r.referrer))
        val b = MediaItem.Builder().setUri(r.url)
        val lower = r.url.lowercase(Locale.US)
        if (forceHls || lower.contains(".m3u8")) b.setMimeType(MimeTypes.APPLICATION_M3U8)
        else if (lower.substringBefore('?').endsWith(".mpd")) b.setMimeType(MimeTypes.APPLICATION_MPD)
        p.setMediaSource(DefaultMediaSourceFactory(DefaultDataSource.Factory(ctx, ds)).createMediaSource(b.build()))
        p.prepare()
        if (r.startAtMs > 0) p.seekTo(r.startAtMs)
        p.playWhenReady = true
    }
}

private val resizeModes = listOf(
    AspectRatioFrameLayout.RESIZE_MODE_FIT to R.string.aspect_fit,
    AspectRatioFrameLayout.RESIZE_MODE_ZOOM to R.string.aspect_zoom,
    AspectRatioFrameLayout.RESIZE_MODE_FILL to R.string.aspect_fill,
)

@Composable
fun PlayerScreen(req: PlayRequest) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val player = remember { PlayerFactory.create(ctx) }
    var view by remember { mutableStateOf<PlayerView?>(null) }
    var controls by remember { mutableStateOf(true) }
    var lastInput by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var isPlaying by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(true) }
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }
    var error by remember { mutableStateOf<String?>(null) }
    var retries by remember { mutableIntStateOf(0) }
    var triedHls by remember { mutableStateOf(false) }
    var resizeIdx by remember { mutableIntStateOf(0) }
    var showTracks by remember { mutableStateOf(false) }
    var showSpeed by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf<String?>(null) }
    var epgNow by remember { mutableStateOf<Programme?>(null) }
    var epgNext by remember { mutableStateOf<Programme?>(null) }
    val rootFocus = remember { FocusRequester() }
    val cur by rememberUpdatedState(req)
    val isVod = req.kind != Kind.LIVE || req.isCatchup
    val acc = Prefs.active

    fun poke() { lastInput = System.currentTimeMillis() }
    fun showHint(s: String) { hint = s; poke() }

    fun saveResume() {
        if (cur.kind == Kind.LIVE || acc == null) return
        val d = player.duration
        if (d <= 0 || d == C.TIME_UNSET) return
        Prefs.saveResume(
            Resume(
                key = cur.key, accountId = acc.id, kind = cur.kind.name, title = cur.title, poster = cur.poster,
                posMs = player.currentPosition, durMs = d, updated = System.currentTimeMillis(),
                seriesId = cur.seriesId ?: "", episodeId = cur.episodeId ?: "",
                itemId = if (cur.kind == Kind.MOVIE) cur.key.removePrefix("movie:") else "",
            )
        )
    }

    fun zap(step: Int) {
        val q = Nav.liveQueue
        val a = acc ?: return
        if (q.isEmpty() || cur.liveId == null || cur.isCatchup) return
        val i = q.indexOfFirst { it.id == cur.liveId }
        val next = q[((if (i < 0) 0 else i) + step).mod(q.size)]
        Prefs.pushRecent(a.id, next.id)
        Nav.replaceTop(com.mohdev.faiptv.ui.Route.Player(Repo.playLive(a, next)))
    }

    fun nextEpisode(): Boolean {
        val a = acc ?: return false
        val q = Nav.episodeQueue
        val i = q.indexOfFirst { it.second.id == cur.episodeId }
        if (i < 0 || i + 1 >= q.size) return false
        val (s, e) = q[i + 1]
        saveResume()
        Nav.replaceTop(com.mohdev.faiptv.ui.Route.Player(Repo.playEpisode(a, s, e).copy(startAtMs = 0)))
        return true
    }

    // (Re)load when the request changes (zapping / next episode keep the same player)
    LaunchedEffect(req) {
        error = null; retries = 0; triedHls = false; epgNow = null; epgNext = null
        controls = true; poke()
        runCatching { PlayerFactory.load(ctx, player, req) }.onFailure { error = it.message }
        runCatching { rootFocus.requestFocus() }
        if (req.kind == Kind.LIVE && !req.isCatchup && acc != null) {
            val ch = Repo.lib?.liveById?.get(req.liveId)
            if (ch != null) {
                val list = Repo.epg(acc, ch, full = false)
                val now = System.currentTimeMillis()
                epgNow = list.firstOrNull { it.isNow(now) }
                epgNext = list.firstOrNull { it.start >= now }
            }
        }
    }

    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onIsPlayingChanged(p: Boolean) { isPlaying = p; PlayerState.playing = p }
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) { retries = 0; error = null }
                if (state == Player.STATE_ENDED) {
                    saveResume()
                    if (cur.kind == Kind.SERIES && Prefs.s.autoNext && nextEpisode()) return
                    if (cur.kind == Kind.LIVE && !cur.isCatchup) { player.seekToDefaultPosition(); player.prepare() }
                }
            }
            override fun onPlayerError(e: PlaybackException) {
                AppLog.w("player", "error ${e.errorCodeName}: ${e.message}")
                val parse = e.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ||
                    e.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED
                when {
                    parse && !triedHls -> { triedHls = true; PlayerFactory.load(ctx, player, cur, forceHls = true) }
                    e.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> { player.seekToDefaultPosition(); player.prepare() }
                    !isVod && retries < 5 -> {
                        retries++
                        scope.launch {
                            hint = ctx.getString(R.string.player_reconnecting)
                            delay(1500L * retries)
                            PlayerFactory.load(ctx, player, cur, forceHls = triedHls)
                        }
                    }
                    else -> error = e.errorCodeName
                }
            }
        }
        player.addListener(l)
        onDispose {
            saveResume()
            player.removeListener(l)
            player.release()
            PlayerState.playing = false
        }
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        (ctx as? com.mohdev.faiptv.MainActivity)?.setBarsHidden(true)
        var resumeOnStart = false
        val obs = androidx.lifecycle.LifecycleEventObserver { _, ev ->
            if (ev == androidx.lifecycle.Lifecycle.Event.ON_STOP) { resumeOnStart = player.playWhenReady; player.pause(); saveResume() }
            if (ev == androidx.lifecycle.Lifecycle.Event.ON_START && resumeOnStart) player.play()
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(obs)
            (ctx as? com.mohdev.faiptv.MainActivity)?.setBarsHidden(false)
        }
    }

    // progress ticker, resume saving, auto-hide controls
    LaunchedEffect(player) {
        var tick = 0
        while (true) {
            pos = player.currentPosition
            dur = player.duration.takeIf { it > 0 && it != C.TIME_UNSET } ?: 0
            if (++tick % 20 == 0) saveResume()
            if (controls && isPlaying && System.currentTimeMillis() - lastInput > 5000 && !showTracks && !showSpeed) controls = false
            if (hint != null && System.currentTimeMillis() - lastInput > 2500) hint = null
            delay(500)
        }
    }

    // When the controls disappear the focused button is gone: give focus back to the player surface,
    // otherwise the remote's keys would no longer reach it.
    LaunchedEffect(controls) { if (!controls) runCatching { rootFocus.requestFocus() } }

    BackHandler {
        if (controls && !UiEnv.inPip) controls = false else Nav.pop()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                poke()
                when (e.key) {
                    Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause, Key.Spacebar -> {
                        if (player.isPlaying) player.pause() else player.play(); true
                    }
                    Key.ChannelUp, Key.PageUp -> { zap(-1); true }
                    Key.ChannelDown, Key.PageDown -> { zap(1); true }
                    Key.MediaFastForward -> { player.seekTo(player.currentPosition + 30_000); true }
                    Key.MediaRewind -> { player.seekTo((player.currentPosition - 30_000).coerceAtLeast(0)); true }
                    Key.MediaNext -> { if (req.kind == Kind.SERIES) nextEpisode() else zap(1); true }
                    Key.MediaPrevious -> { zap(-1); true }
                    else -> if (!controls) {
                        when (e.key) {
                            Key.DirectionLeft -> { if (isVod) { player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0)); showHint("-10s") } else controls = true; true }
                            Key.DirectionRight -> { if (isVod) { player.seekTo(player.currentPosition + 10_000); showHint("+10s") } else controls = true; true }
                            Key.DirectionUp -> { if (!isVod) zap(-1) else controls = true; true }
                            Key.DirectionDown -> { if (!isVod) zap(1) else controls = true; true }
                            Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.Menu -> { controls = true; true }
                            else -> false
                        }
                    } else false
                }
            }
            .focusable()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                controls = !controls; poke()
            },
    ) {
        AndroidView(
            factory = { c ->
                PlayerView(c).apply {
                    layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    useController = false
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    keepScreenOn = true
                    isFocusable = false
                    this.player = player
                    view = this
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { it.resizeMode = resizeModes[resizeIdx].first },
        )

        if (buffering && error == null) {
            androidx.compose.material3.CircularProgressIndicator(Modifier.align(Alignment.Center).size(56.dp), color = Brand.Cyan)
        }

        hint?.let {
            Text(
                it, color = Color.White, fontSize = 18.sp,
                modifier = Modifier.align(Alignment.Center).padding(top = 120.dp)
                    .clip(RoundedCornerShape(10.dp)).background(Color(0xAA000000)).padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        error?.let { err ->
            Column(
                Modifier.align(Alignment.Center).clip(RoundedCornerShape(16.dp)).background(Color(0xDD0E1430)).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.player_error, err), color = Color.White)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { error = null; retries = 0; PlayerFactory.load(ctx, player, req, triedHls) }) {
                        Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.retry))
                    }
                    OutlinedButton(onClick = { External.open(ctx, req.copy(startAtMs = player.currentPosition)) }) {
                        Text(stringResource(R.string.action_external))
                    }
                }
            }
        }

        if (!UiEnv.inPip) {
            AnimatedVisibility(controls, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
                Controls(
                    req = req, isVod = isVod, isPlaying = isPlaying, pos = pos, dur = dur,
                    epgNow = epgNow, epgNext = epgNext, poke = ::poke,
                    onPlayPause = { if (player.isPlaying) player.pause() else player.play() },
                    onSeek = { player.seekTo(it) },
                    onZap = ::zap,
                    onNext = { nextEpisode() },
                    hasNext = req.kind == Kind.SERIES && Nav.episodeQueue.indexOfFirst { it.second.id == req.episodeId }.let { it >= 0 && it + 1 < Nav.episodeQueue.size },
                    onTracks = { showTracks = true },
                    onSpeed = { showSpeed = true },
                    onAspect = {
                        resizeIdx = (resizeIdx + 1) % resizeModes.size
                        showHint(ctx.getString(resizeModes[resizeIdx].second))
                    },
                    onPip = { enterPip(ctx) },
                    onExternal = { player.pause(); External.open(ctx, req.copy(startAtMs = player.currentPosition)) },
                    onRecord = {
                        val r = req
                        RecordService.startNow(ctx, r.url, r.title, r.userAgent, 0L)
                        showHint(ctx.getString(R.string.rec_started, r.title))
                    },
                    onBack = { Nav.pop() },
                )
            }
        }
    }

    if (showTracks) TracksDialog(player) { showTracks = false; poke() }
    if (showSpeed) {
        val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
        com.mohdev.faiptv.ui.ChoiceDialog(
            stringResource(R.string.player_speed), speeds.map { it to "${it}x" }, player.playbackParameters.speed,
            onDismiss = { showSpeed = false },
        ) { player.setPlaybackSpeed(it); showSpeed = false }
    }
}

fun enterPip(ctx: Context) {
    val a = ctx as? Activity ?: return
    if (Build.VERSION.SDK_INT < 26 || !UiEnv.pipSupported) return
    runCatching {
        a.enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build())
    }.onFailure { AppLog.w("pip", "failed: ${it.message}") }
}

@Composable
private fun Controls(
    req: PlayRequest, isVod: Boolean, isPlaying: Boolean, pos: Long, dur: Long,
    epgNow: Programme?, epgNext: Programme?, poke: () -> Unit,
    onPlayPause: () -> Unit, onSeek: (Long) -> Unit, onZap: (Int) -> Unit, onNext: () -> Unit, hasNext: Boolean,
    onTracks: () -> Unit, onSpeed: () -> Unit, onAspect: () -> Unit, onPip: () -> Unit, onExternal: () -> Unit,
    onRecord: () -> Unit, onBack: () -> Unit,
) {
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { playFocus.requestFocus() } }
    val acc = Prefs.active
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(0f to Color(0xCC000000), 0.3f to Color.Transparent, 0.6f to Color.Transparent, 1f to Color(0xE6000000))
        )
    ) {
        // top bar
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            CtlButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_close), poke, onBack)
            Spacer(Modifier.width(12.dp))
            if (req.poster.isNotBlank()) {
                NetImage(req.poster, Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)), androidx.compose.ui.layout.ContentScale.Fit)
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (req.kind == Kind.LIVE && !req.isCatchup) {
                        Text(
                            stringResource(R.string.player_live), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Brand.Live).padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(req.title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                val sub = epgNow?.let { "${fmtTime(it.start)}–${fmtTime(it.end)}  ${it.title}" } ?: req.subtitle
                if (sub.isNotBlank()) Text(sub, color = Color(0xFFCBD5E1), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                epgNow?.let { p ->
                    val now = System.currentTimeMillis()
                    LinearProgressIndicator(
                        progress = { ((now - p.start).toFloat() / (p.end - p.start)).coerceIn(0f, 1f) },
                        modifier = Modifier.padding(top = 4.dp).widthIn(max = 320.dp).fillMaxWidth().height(3.dp),
                        color = Brand.Cyan, trackColor = Color(0x44FFFFFF),
                    )
                }
                epgNext?.let { Text("${stringResource(R.string.epg_next)}: ${fmtTime(it.start)} ${it.title}", color = Color(0xFF94A3B8), fontSize = 12.sp, maxLines = 1) }
            }
            Text(fmtTime(System.currentTimeMillis()), color = Color.White, fontSize = 18.sp)
        }

        // center
        Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            if (isVod) CtlButton(Icons.Default.FastRewind, "-10", poke, { onSeek((pos - 10_000).coerceAtLeast(0)) }, big = true)
            else CtlButton(Icons.Default.KeyboardArrowUp, "-", poke, { onZap(-1) }, big = true)
            CtlButton(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "play", poke, onPlayPause, big = true, focus = playFocus, primary = true)
            if (isVod) CtlButton(Icons.Default.FastForward, "+10", poke, { onSeek(pos + 10_000) }, big = true)
            else CtlButton(Icons.Default.KeyboardArrowDown, "+", poke, { onZap(1) }, big = true)
        }

        // bottom
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
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
                Row(Modifier.fillMaxWidth()) {
                    Text(fmtDur(if (dragging) dragValue.toLong() else pos), color = Color.White, fontSize = 13.sp)
                    Spacer(Modifier.weight(1f))
                    Text("-" + fmtDur(dur - pos), color = Color.White, fontSize = 13.sp)
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                CtlButton(Icons.Default.Subtitles, stringResource(R.string.player_tracks), poke, onTracks, label = true)
                CtlButton(Icons.Default.AspectRatio, stringResource(R.string.player_aspect), poke, onAspect, label = true)
                if (isVod) CtlButton(Icons.Default.Speed, stringResource(R.string.player_speed), poke, onSpeed, label = true)
                if (hasNext) CtlButton(Icons.Default.SkipNext, stringResource(R.string.player_next_ep), poke, onNext, label = true)
                if (req.kind == Kind.LIVE && !req.isCatchup) {
                    CtlButton(Icons.Default.FiberManualRecord, stringResource(R.string.action_record), poke, onRecord, label = true, tint = Brand.Live)
                    if (acc != null && req.liveId != null) {
                        val key = "live:${req.liveId}"
                        val fav = Prefs.isFav(acc.id, key)
                        CtlButton(
                            if (fav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            stringResource(if (fav) R.string.action_fav_remove else R.string.action_fav_add), poke,
                            { Prefs.toggleFav(acc.id, key) }, label = true, tint = if (fav) Brand.Pink else Color.White,
                        )
                    }
                }
                if (UiEnv.pipSupported) CtlButton(Icons.Default.PictureInPictureAlt, stringResource(R.string.player_pip), poke, onPip, label = true)
                CtlButton(Icons.Default.OpenInNew, stringResource(R.string.action_external), poke, onExternal, label = true)
            }
        }
    }
}

@Composable
private fun CtlButton(
    icon: ImageVector, desc: String, poke: () -> Unit, onClick: () -> Unit,
    big: Boolean = false, focus: FocusRequester? = null, primary: Boolean = false, label: Boolean = false,
    tint: Color = Color.White,
) {
    var focused by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 6.dp)) {
        IconButton(
            onClick = { poke(); onClick() },
            modifier = Modifier
                .then(if (focus != null) Modifier.focusRequester(focus) else Modifier)
                .onFocusChanged { focused = it.isFocused; if (it.isFocused) poke() }
                .size(if (big) 68.dp else 48.dp)
                .clip(CircleShape)
                .background(
                    when {
                        focused -> Brand.Cyan
                        primary -> Color(0x55FFFFFF)
                        else -> Color(0x33000000)
                    }
                ),
        ) {
            Icon(icon, desc, tint = if (focused) Color.Black else tint, modifier = Modifier.size(if (big) 40.dp else 26.dp))
        }
        if (label && focused) Text(desc, color = Color.White, fontSize = 11.sp, maxLines = 1)
    }
}

private class Opt(val label: String, val selected: Boolean, val apply: () -> Unit)

private fun trackLabel(f: Format, type: Int): String {
    val parts = ArrayList<String>()
    val lang = f.language
    if (!lang.isNullOrBlank() && lang != "und") parts += Locale(lang).getDisplayLanguage(Locale.getDefault()).ifBlank { lang }
    f.label?.takeIf { it.isNotBlank() && it !in parts }?.let { parts += it }
    when (type) {
        C.TRACK_TYPE_AUDIO -> {
            if (f.channelCount == 6) parts += "5.1" else if (f.channelCount == 8) parts += "7.1" else if (f.channelCount == 2) parts += "Stereo"
            f.sampleMimeType?.substringAfter('/')?.uppercase()?.let { parts += it }
        }
        C.TRACK_TYPE_VIDEO -> {
            if (f.height > 0) parts.add(0, "${f.height}p")
            if (f.bitrate > 0) parts += "${f.bitrate / 1000} kbps"
        }
    }
    return parts.joinToString(" · ").ifBlank { "#${f.id ?: "?"}" }
}

@Composable
private fun TracksDialog(player: ExoPlayer, onDismiss: () -> Unit) {
    val tracks: Tracks = player.currentTracks
    fun section(type: Int): List<Opt> {
        val out = ArrayList<Opt>()
        tracks.groups.filter { it.type == type }.forEach { g ->
            for (i in 0 until g.length) {
                if (!g.isTrackSupported(i)) continue
                out += Opt(trackLabel(g.getTrackFormat(i), type), g.isTrackSelected(i)) {
                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(type, false)
                        .setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, i))
                        .build()
                }
            }
        }
        return out
    }
    val audio = section(C.TRACK_TYPE_AUDIO)
    val text = section(C.TRACK_TYPE_TEXT)
    val video = section(C.TRACK_TYPE_VIDEO)
    val textOff = player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT) || text.none { it.selected }
    val videoAuto = player.trackSelectionParameters.overrides.values.none { it.type == C.TRACK_TYPE_VIDEO }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.player_tracks)) },
        text = {
            LazyColumn(Modifier.height(380.dp)) {
                item { TrackHeader(stringResource(R.string.player_audio)) }
                items(audio) { o -> TrackRow(o.label, o.selected) { o.apply(); onDismiss() } }
                item { TrackHeader(stringResource(R.string.player_subtitles)) }
                item {
                    TrackRow(stringResource(R.string.player_off), textOff) {
                        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
                        onDismiss()
                    }
                }
                items(text) { o -> TrackRow(o.label, o.selected && !textOff) { o.apply(); onDismiss() } }
                if (video.size > 1) {
                    item { TrackHeader(stringResource(R.string.player_video)) }
                    item {
                        TrackRow(stringResource(R.string.player_auto), videoAuto) {
                            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                                .clearOverridesOfType(C.TRACK_TYPE_VIDEO).build()
                            onDismiss()
                        }
                    }
                    items(video) { o -> TrackRow(o.label, o.selected && !videoAuto) { o.apply(); onDismiss() } }
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
    com.mohdev.faiptv.ui.FocusCard(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), color = Color.Transparent, zoom = 1f) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.RadioButton(selected = selected, onClick = null)
            Spacer(Modifier.width(8.dp))
            Text(label)
        }
    }
}
