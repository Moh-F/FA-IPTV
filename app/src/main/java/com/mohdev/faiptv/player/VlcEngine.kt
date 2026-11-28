package com.mohdev.faiptv.player

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.mohdev.faiptv.core.AppLog
import com.mohdev.faiptv.core.Prefs
import com.mohdev.faiptv.data.Net
import com.mohdev.faiptv.data.PlayRequest
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.util.VLCVideoLayout

/**
 * VLC engine (LGPL libVLC, dynamically linked): plays almost every stream, de-interlaces TV channels,
 * decodes AC3/E-AC3/DTS in software where the device cannot.
 */
class VlcEngine(ctx: Context, private val l: EngineListener) : Engine {
    override val name = "VLC"
    private val lib: LibVLC
    private val mp: MediaPlayer
    private var media: Media? = null
    private var layout: VLCVideoLayout? = null
    private var attached = false
    private var buffering = true
    private var length = 0L
    private var released = false
    private var quality = Prefs.s.quality
    private var last: PlayRequest? = null

    init {
        val s = Prefs.s
        val cache = when (s.buffer) { "small" -> 800; "large" -> 3000; else -> 1500 }
        val opts = arrayListOf(
            "--network-caching=$cache",
            "--live-caching=$cache",
            "--http-reconnect",
            "--audio-time-stretch",
            "--avcodec-skiploopfilter=1",
            "--stats",
        )
        if (s.deinterlace) opts += listOf("--deinterlace=-1", "--deinterlace-mode=yadif")
        if (s.passthrough) opts += "--spdif"
        lib = LibVLC(ctx.applicationContext, opts)
        mp = MediaPlayer(lib)
        mp.setEventListener { ev ->
            if (released) return@setEventListener
            when (ev.type) {
                MediaPlayer.Event.Playing -> { buffering = false; l.onState(true, false); l.onReady() }
                MediaPlayer.Event.Paused -> l.onState(false, false)
                MediaPlayer.Event.Stopped -> l.onState(false, false)
                MediaPlayer.Event.Buffering -> {
                    val b = ev.buffering < 100f
                    if (b != buffering) { buffering = b; l.onState(mp.isPlaying, b) }
                }
                MediaPlayer.Event.LengthChanged -> length = ev.lengthChanged
                MediaPlayer.Event.EndReached -> l.onEnded()
                MediaPlayer.Event.EncounteredError -> { AppLog.w("vlc", "playback error"); l.onError("VLC_ERROR", false) }
                else -> Unit
            }
        }
    }

    override fun load(r: PlayRequest, forceHls: Boolean) {
        val m = Media(lib, Uri.parse(com.mohdev.faiptv.data.SmartDns.ipUrl(r.url)))
        m.setHWDecoderEnabled(true, false)
        m.addOption(":http-user-agent=" + Net.userAgent(r.userAgent))
        if (r.referrer.isNotBlank()) m.addOption(":http-referrer=" + r.referrer)
        last = r
        when (val q = quality) {
            "max" -> m.addOption(":adaptive-logic=highest")
            "auto" -> Unit
            else -> q.toIntOrNull()?.let { m.addOption(":adaptive-maxheight=$it") }
        }
        if (r.startAtMs > 0) m.addOption(":start-time=" + (r.startAtMs / 1000.0))
        media?.release()
        media = m
        length = 0
        buffering = true
        mp.media = m
        mp.play()
    }

    override fun play() { mp.play() }
    override fun pause() { mp.pause() }
    override fun isPlaying() = mp.isPlaying
    override fun playWhenReady() = mp.isPlaying || buffering
    override fun position() = mp.time.coerceAtLeast(0)
    override fun duration() = (if (length > 0) length else mp.length).coerceAtLeast(0)
    override fun seekTo(ms: Long) { mp.setTime(ms) }
    override fun setSpeed(f: Float) { mp.rate = f }
    override fun speed() = mp.rate

    override fun setResize(mode: Int) {
        mp.videoScale = when (mode) {
            1 -> MediaPlayer.ScaleType.SURFACE_FIT_SCREEN
            2 -> MediaPlayer.ScaleType.SURFACE_FILL
            else -> MediaPlayer.ScaleType.SURFACE_BEST_FIT
        }
    }

    override fun setVolume(v: Float) { mp.volume = (v * 100).toInt() }
    override fun audioSession(): Int = 0

    override fun setQuality(q: String) {
        quality = q
        last?.let { r -> load(if (duration() > 0) r.copy(startAtMs = position()) else r) }
    }

    override fun stats(): Stats {
        val v = mp.currentVideoTrack
        val m = media
        var acodec = ""
        var ch = 0
        if (m != null) {
            for (i in 0 until m.trackCount) {
                val t = m.getTrack(i) ?: continue
                if (t is IMedia.AudioTrack) { acodec = t.codec ?: ""; ch = t.channels; break }
            }
        }
        val st = m?.stats
        return Stats(
            width = v?.width ?: 0, height = v?.height ?: 0,
            fps = if (v != null && v.frameRateDen > 0) v.frameRateNum.toFloat() / v.frameRateDen else 0f,
            vcodec = codecName(v?.codec), acodec = codecName(acodec).ifBlank { acodec.uppercase() }, channels = ch,
            declaredKbps = ((st?.demuxBitrate ?: 0f) * 8000).toInt(),
            netEstimateKbps = ((st?.inputBitrate ?: 0f) * 8000).toInt(),
            bytes = (st?.readBytes ?: 0).toLong(), dropped = st?.lostPictures ?: 0,
        )
    }

    override fun tracks(): TrackSet {
        val audio = mp.audioTracks.orEmpty().filter { it.id >= 0 }.map { t ->
            TrackOpt(t.name ?: "#${t.id}", t.id == mp.audioTrack) { mp.setAudioTrack(t.id) }
        }
        val spu = mp.spuTracks.orEmpty().filter { it.id >= 0 }.map { t ->
            TrackOpt(t.name ?: "#${t.id}", t.id == mp.spuTrack) { mp.setSpuTrack(t.id) }
        }
        return TrackSet(
            audio = audio, text = spu, video = emptyList(),
            textIsOff = mp.spuTrack < 0, videoIsAuto = true,
            textOff = { mp.setSpuTrack(-1) }, videoAuto = {},
        )
    }

    override fun release() {
        released = true
        runCatching { mp.stop() }
        if (attached) runCatching { mp.detachViews() }
        mp.setEventListener(null)
        mp.release()
        media?.release()
        lib.release()
    }

    @Composable
    override fun Surface(modifier: Modifier) {
        AndroidView(
            factory = { c ->
                VLCVideoLayout(c).also { v ->
                    v.keepScreenOn = true
                    layout = v
                    mp.attachViews(v, null, true, false)
                    attached = true
                }
            },
            modifier = modifier,
        )
    }
}
