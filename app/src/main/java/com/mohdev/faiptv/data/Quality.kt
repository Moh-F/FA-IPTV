package com.mohdev.faiptv.data

import android.content.Context
import com.mohdev.faiptv.core.AppLog
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Per channel: how reliably and how fast it plays. Device-local, used for the report and to pick alternatives. */
@Serializable
data class ChannelQuality(
    val plays: Int = 0,
    val failures: Int = 0,
    val rebuffers: Int = 0,
    val startMsSum: Long = 0,
    val starts: Int = 0,
    val kbpsSum: Long = 0,
    val kbpsSamples: Int = 0,
    val last: Long = 0,
) {
    val startMs get() = if (starts > 0) startMsSum / starts else 0
    val kbps get() = if (kbpsSamples > 0) (kbpsSum / kbpsSamples).toInt() else 0
    /** 0..100: success rate, fewer stalls, fast start */
    val score: Int get() {
        if (plays == 0) return 50
        val ok = (plays - failures).coerceAtLeast(0).toFloat() / plays
        val stall = (rebuffers.toFloat() / plays).coerceAtMost(5f) / 5f
        val start = (startMs / 8000f).coerceAtMost(1f)
        return ((ok * 0.6f + (1 - stall) * 0.25f + (1 - start) * 0.15f) * 100).toInt()
    }
}

object Quality {
    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var file: File
    private val map = ConcurrentHashMap<String, ChannelQuality>()

    fun init(ctx: Context) {
        file = File(ctx.filesDir, "quality.json")
        runCatching { if (file.exists()) map.putAll(json.decodeFromString<Map<String, ChannelQuality>>(file.readText())) }
            .onFailure { AppLog.w("quality", "unreadable") }
    }

    operator fun get(key: String): ChannelQuality = map[key] ?: ChannelQuality()

    private fun edit(key: String, f: (ChannelQuality) -> ChannelQuality) {
        map[key] = f(this[key]).copy(last = System.currentTimeMillis())
        if (map.size > 3000) map.entries.sortedBy { it.value.last }.take(500).forEach { map.remove(it.key) }
    }

    fun started(key: String, ms: Long) = edit(key) { it.copy(plays = it.plays + 1, startMsSum = it.startMsSum + ms, starts = it.starts + 1) }
    fun failed(key: String) = edit(key) { it.copy(plays = it.plays + 1, failures = it.failures + 1) }
    fun rebuffer(key: String) = edit(key) { it.copy(rebuffers = it.rebuffers + 1) }
    fun rate(key: String, kbps: Int) { if (kbps > 0) edit(key) { it.copy(kbpsSum = it.kbpsSum + kbps, kbpsSamples = it.kbpsSamples + 1) } }

    fun save() = runCatching { file.writeText(json.encodeToString(map.toMap())) }
}

/** Other versions of the same channel (FHD/HD/SD, backup feeds) to switch to when one fails. Pure. */
object Alternatives {
    private val noise = Regex("""(?i)\b(fhd|uhd|hd|sd|hq|lq|4k|8k|hevc|h\.?265|h\.?264|raw|backup|bkp|alt|vip|50fps|60fps|1080p?|720p?|576p?)\b|[\[\(].*?[\]\)]|[|:•*★_\-]+""")
    private val prefix = Regex("""^\s*[A-Za-z]{2,3}\s*[|:\-]\s*""")

    fun base(name: String): String = name.replace(prefix, " ").replace(noise, " ").lowercase().replace(Regex("""\s+"""), " ").trim()

    fun rank(name: String): Int {
        val n = name.lowercase()
        return when {
            Regex("""\b(4k|uhd)\b""").containsMatchIn(n) -> 4
            Regex("""\bfhd\b|1080""").containsMatchIn(n) -> 3
            Regex("""\bhd\b|720""").containsMatchIn(n) -> 2
            Regex("""\bsd\b|576""").containsMatchIn(n) -> 1
            else -> 2
        }
    }

    fun find(all: List<Live>, ch: Live, score: (Live) -> Int = { 50 }): List<Live> {
        val b = base(ch.name)
        if (b.length < 2) return emptyList()
        return all.filter { it.id != ch.id && base(it.name) == b }
            .sortedWith(compareByDescending<Live> { score(it) }.thenByDescending { rank(it.name) })
    }
}
