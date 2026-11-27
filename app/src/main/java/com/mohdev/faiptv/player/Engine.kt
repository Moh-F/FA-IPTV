package com.mohdev.faiptv.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mohdev.faiptv.data.PlayRequest

interface EngineListener {
    fun onState(playing: Boolean, buffering: Boolean)
    fun onReady()
    fun onEnded()
    /** [unsupported] = container/codec problem: another engine or HLS may work. */
    fun onError(code: String, unsupported: Boolean)
}

/** Technical info for the info bar. */
data class Stats(
    val width: Int = 0,
    val height: Int = 0,
    val fps: Float = 0f,
    val vcodec: String = "",
    val acodec: String = "",
    val channels: Int = 0,
    val declaredKbps: Int = 0,
    val netEstimateKbps: Int = 0,
    val bytes: Long = 0,
    val dropped: Int = 0,
)

class TrackOpt(val label: String, val selected: Boolean, val apply: () -> Unit)

class TrackSet(
    val audio: List<TrackOpt> = emptyList(),
    val text: List<TrackOpt> = emptyList(),
    val video: List<TrackOpt> = emptyList(),
    val textIsOff: Boolean = true,
    val videoIsAuto: Boolean = true,
    val textOff: () -> Unit = {},
    val videoAuto: () -> Unit = {},
)

/** Playback engine: ExoPlayer (Media3) or VLC. */
interface Engine {
    val name: String
    fun load(r: PlayRequest, forceHls: Boolean = false)
    fun play()
    fun pause()
    fun isPlaying(): Boolean
    fun playWhenReady(): Boolean
    fun position(): Long
    fun duration(): Long
    fun seekTo(ms: Long)
    fun setSpeed(f: Float)
    fun speed(): Float
    /** 0 = fit, 1 = zoom/crop, 2 = stretch */
    fun setResize(mode: Int)
    /** "auto" | "max" | "1080" … for this session */
    fun setQuality(q: String)
    fun tracks(): TrackSet
    fun stats(): Stats
    fun release()

    @Composable
    fun Surface(modifier: Modifier)
}

fun qualityLabel(h: Int): String = when {
    h >= 2000 -> "4K"
    h >= 1400 -> "QHD"
    h >= 1000 -> "FHD"
    h >= 700 -> "HD"
    h > 0 -> "SD"
    else -> ""
}

fun codecName(mime: String?): String {
    val m = (mime ?: "").lowercase()
    return when {
        m.isBlank() -> ""
        "avc" in m || "h264" in m -> "H.264"
        "hevc" in m || "h265" in m || "hvc" in m -> "H.265"
        "av01" in m || "av1" in m -> "AV1"
        "vp9" in m -> "VP9"
        "mp4a" in m || "aac" in m -> "AAC"
        "eac3" in m || "ec-3" in m -> "E-AC3"
        "ac3" in m || "ac-3" in m -> "AC3"
        "mpeg" in m && "audio" in m -> "MP2"
        "mpeg2" in m || "mp2v" in m -> "MPEG-2"
        "dts" in m -> "DTS"
        "opus" in m -> "Opus"
        else -> m.substringAfter('/').uppercase().take(10)
    }
}
