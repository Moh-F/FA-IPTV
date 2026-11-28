package com.mohdev.faiptv.player

import android.content.Context
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.mohdev.faiptv.core.AppLog
import com.mohdev.faiptv.core.Prefs
import com.mohdev.faiptv.data.Net
import com.mohdev.faiptv.data.PlayRequest
import java.util.Locale

/** Media3 ExoPlayer: fastest start, adaptive quality (HLS/DASH), hardware decoding. */
class ExoEngine(private val ctx: Context, private val l: EngineListener, private val vod: Boolean = false) : Engine {
    override val name = "ExoPlayer"
    private val bandwidth = DefaultBandwidthMeter.getSingletonInstance(ctx)
    private val selector = DefaultTrackSelector(ctx)
    @Volatile private var bytes = 0L
    private var dropped = 0
    private var view: PlayerView? = null
    private var resize = AspectRatioFrameLayout.RESIZE_MODE_FIT

    private val transfer = object : TransferListener {
        override fun onTransferInitializing(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {}
        override fun onTransferStart(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {}
        override fun onBytesTransferred(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean, bytesTransferred: Int) {
            if (isNetwork) bytes += bytesTransferred
        }
        override fun onTransferEnd(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {}
    }

    val player: ExoPlayer = run {
        val s = Prefs.s
        // Live: small buffer = fast start. Films/series: read far ahead like YouTube so playback never stops.
        val (min, max, play, rebuf) = if (vod) when (s.vodBuffer) {
            "max" -> listOf(60_000, 240_000, 1_500, 4_000)
            "large" -> listOf(30_000, 150_000, 1_500, 3_000)
            else -> listOf(15_000, 60_000, 1_500, 3_000)
        } else when (s.buffer) {
            "small" -> listOf(4_000, 20_000, 700, 1_500)
            "large" -> listOf(30_000, 120_000, 2_500, 6_000)
            else -> listOf(12_000, 50_000, 1_500, 3_000)
        }
        val startMs = if (s.fastStart) minOf(play, 700) else play
        val load = DefaultLoadControl.Builder()
            .setBufferDurationsMs(min, max, startMs, rebuf)
            .setPrioritizeTimeOverSizeThresholds(true)
            .setBackBuffer(if (vod) 30_000 else 0, true)
            .build()
        val renderers = DefaultRenderersFactory(ctx)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            .setEnableDecoderFallback(true)
        if (s.tunneling) selector.setParameters(selector.buildUponParameters().setTunnelingEnabled(true))
        val audioAttr = androidx.media3.common.AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build()
        ExoPlayer.Builder(ctx, renderers)
            .setTrackSelector(selector)
            .setBandwidthMeter(bandwidth)
            .setLoadControl(load)
            .setAudioAttributes(audioAttr, true)
            .setHandleAudioBecomingNoisy(true)
            .build().also { p ->
                p.videoChangeFrameRateStrategy = C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS
                applyQuality(p, s.quality)
                p.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) = l.onState(isPlaying, p.playbackState == Player.STATE_BUFFERING)
                    override fun onPlaybackStateChanged(state: Int) {
                        l.onState(p.isPlaying, state == Player.STATE_BUFFERING)
                        if (state == Player.STATE_READY) l.onReady()
                        if (state == Player.STATE_ENDED) l.onEnded()
                    }
                    override fun onPlayerError(e: PlaybackException) {
                        AppLog.w("exo", "error ${e.errorCodeName}: ${e.message}")
                        if (e.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                            p.seekToDefaultPosition(); p.prepare(); return
                        }
                        val unsupported = e.errorCode in setOf(
                            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
                            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
                            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
                            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
                            PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
                            PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
                        )
                        l.onError(e.errorCodeName, unsupported)
                    }
                })
                p.addAnalyticsListener(object : AnalyticsListener {
                    override fun onDroppedVideoFrames(eventTime: AnalyticsListener.EventTime, droppedFrames: Int, elapsedMs: Long) {
                        dropped += droppedFrames
                    }
                })
            }
    }

    private fun applyQuality(p: ExoPlayer, q: String) {
        val b = p.trackSelectionParameters.buildUpon()
        when (q) {
            "max" -> b.setForceHighestSupportedBitrate(true)
            "auto" -> b.setForceHighestSupportedBitrate(false).clearVideoSizeConstraints()
            else -> q.toIntOrNull()?.let { h -> b.setForceHighestSupportedBitrate(false).setMaxVideoSize(Int.MAX_VALUE, h) }
        }
        p.trackSelectionParameters = b.build()
    }

    override fun load(r: PlayRequest, forceHls: Boolean) {
        val ds = OkHttpDataSource.Factory(Net.streamClient).setUserAgent(Net.userAgent(r.userAgent)).setTransferListener(transfer)
        if (r.referrer.isNotBlank()) ds.setDefaultRequestProperties(mapOf("Referer" to r.referrer))
        val b = MediaItem.Builder().setUri(r.url)
        val lower = r.url.lowercase(Locale.US)
        if (forceHls || lower.contains(".m3u8")) b.setMimeType(MimeTypes.APPLICATION_M3U8)
        else if (lower.substringBefore('?').endsWith(".mpd")) b.setMimeType(MimeTypes.APPLICATION_MPD)
        player.setMediaSource(DefaultMediaSourceFactory(DefaultDataSource.Factory(ctx, ds)).createMediaSource(b.build()))
        player.prepare()
        if (r.startAtMs > 0) player.seekTo(r.startAtMs)
        player.playWhenReady = true
    }

    override fun play() = player.play()
    override fun pause() = player.pause()
    override fun isPlaying() = player.isPlaying
    override fun playWhenReady() = player.playWhenReady
    override fun position() = player.currentPosition
    override fun duration() = player.duration.takeIf { it > 0 && it != C.TIME_UNSET } ?: 0L
    override fun seekTo(ms: Long) = player.seekTo(ms)
    override fun setSpeed(f: Float) = player.setPlaybackSpeed(f)
    override fun speed() = player.playbackParameters.speed

    override fun setResize(mode: Int) {
        resize = when (mode) { 1 -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM; 2 -> AspectRatioFrameLayout.RESIZE_MODE_FILL; else -> AspectRatioFrameLayout.RESIZE_MODE_FIT }
        view?.resizeMode = resize
    }

    override fun setQuality(q: String) = applyQuality(player, q)

    override fun stats(): Stats {
        val v: Format? = player.videoFormat
        val a: Format? = player.audioFormat
        return Stats(
            width = v?.width?.takeIf { it > 0 } ?: 0, height = v?.height?.takeIf { it > 0 } ?: 0,
            fps = v?.frameRate?.takeIf { it > 0 } ?: 0f,
            vcodec = codecName(v?.sampleMimeType ?: v?.codecs), acodec = codecName(a?.sampleMimeType ?: a?.codecs),
            channels = a?.channelCount?.takeIf { it > 0 } ?: 0,
            declaredKbps = ((v?.bitrate ?: -1).takeIf { it > 0 } ?: 0) / 1000,
            netEstimateKbps = (bandwidth.bitrateEstimate / 1000).toInt(),
            bytes = bytes, dropped = dropped,
        )
    }

    override fun tracks(): TrackSet {
        val tracks = player.currentTracks
        fun section(type: Int): List<TrackOpt> {
            val out = ArrayList<TrackOpt>()
            tracks.groups.filter { it.type == type }.forEach { g ->
                for (i in 0 until g.length) {
                    if (!g.isTrackSupported(i)) continue
                    out += TrackOpt(label(g.getTrackFormat(i), type), g.isTrackSelected(i)) {
                        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                            .setTrackTypeDisabled(type, false)
                            .setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, i))
                            .build()
                    }
                }
            }
            return out
        }
        val text = section(C.TRACK_TYPE_TEXT)
        return TrackSet(
            audio = section(C.TRACK_TYPE_AUDIO), text = text,
            video = section(C.TRACK_TYPE_VIDEO).sortedByDescending { it.label.substringBefore('p').toIntOrNull() ?: 0 },
            textIsOff = player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT) || text.none { it.selected },
            videoIsAuto = player.trackSelectionParameters.overrides.values.none { it.type == C.TRACK_TYPE_VIDEO },
            textOff = { player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build() },
            videoAuto = { player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_VIDEO).build() },
        )
    }

    private fun label(f: Format, type: Int): String {
        val parts = ArrayList<String>()
        when (type) {
            C.TRACK_TYPE_VIDEO -> {
                if (f.height > 0) parts += "${f.height}p"
                if (f.frameRate > 0) parts += "${f.frameRate.toInt()}fps"
                if (f.bitrate > 0) parts += String.format(Locale.US, "%.1f Mb/s", f.bitrate / 1_000_000f)
                codecName(f.sampleMimeType).takeIf { it.isNotBlank() }?.let { parts += it }
            }
            else -> {
                val lang = f.language
                if (!lang.isNullOrBlank() && lang != "und") parts += Locale(lang).getDisplayLanguage(Locale.getDefault()).ifBlank { lang }
                f.label?.takeIf { it.isNotBlank() && it !in parts }?.let { parts += it }
                if (type == C.TRACK_TYPE_AUDIO) {
                    when (f.channelCount) { 6 -> parts += "5.1"; 8 -> parts += "7.1"; 2 -> parts += "Stereo"; 1 -> parts += "Mono" }
                    codecName(f.sampleMimeType).takeIf { it.isNotBlank() }?.let { parts += it }
                }
            }
        }
        return parts.joinToString(" · ").ifBlank { "#${f.id ?: "?"}" }
    }

    override fun release() {
        view?.player = null
        player.release()
    }

    @Composable
    override fun Surface(modifier: Modifier) {
        AndroidView(
            factory = { c ->
                PlayerView(c).apply {
                    layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    useController = false
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    keepScreenOn = true
                    isFocusable = false
                    resizeMode = resize
                    player = this@ExoEngine.player
                    view = this
                }
            },
            modifier = modifier,
        )
    }
}
