package com.mohdev.faiptv.data

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** Builds catch-up (archive) URLs for Xtream panels and the common M3U catch-up flavours. */
object CatchUp {
    private val xcUrl = Regex("""^(https?://[^/]+)/(?:live/)?([^/]+)/([^/]+)/(\d+)(\.\w+)?$""")
    private val fmtVar = Regex("""\$?\{(utc|start|end|utcend|lutc|now|timestamp):([^}]+)\}""")
    private val numVar = Regex("""\$?\{(duration|offset):(\d+)\}""")

    fun xtream(server: String, user: String, pass: String, streamId: String, startMs: Long, endMs: Long, tz: String): String {
        val minutes = ((endMs - startMs) / 60_000L).coerceAtLeast(1)
        return "$server/timeshift/$user/$pass/$minutes/${xtreamTime(startMs, tz)}/$streamId.ts"
    }

    fun xtreamTime(ms: Long, tz: String): String {
        val c = Calendar.getInstance(zone(tz), Locale.US).apply { timeInMillis = ms }
        return String.format(
            Locale.US, "%04d-%02d-%02d:%02d-%02d",
            c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH),
            c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE),
        )
    }

    private fun zone(tz: String): TimeZone = if (tz.isBlank()) TimeZone.getDefault() else TimeZone.getTimeZone(tz)

    fun m3u(live: Live, startMs: Long, endMs: Long, nowMs: Long = System.currentTimeMillis(), tz: String = ""): String {
        val type = live.catchup.lowercase(Locale.US)
        val src = live.catchupSource
        val url = live.url
        return when (type) {
            "append" -> url + fill(src, startMs, endMs, nowMs, tz)
            "default" -> if (src.isNotBlank()) absolute(url, fill(src, startMs, endMs, nowMs, tz)) else shift(url, startMs, nowMs)
            "shift", "timeshift" -> shift(url, startMs, nowMs)
            "flussonic", "flussonic-hls", "flussonic-ts", "fs" -> flussonic(url, startMs, endMs)
            "xc" -> xcFromUrl(url, startMs, endMs, tz) ?: shift(url, startMs, nowMs)
            else -> when {
                src.isNotBlank() -> absolute(url, fill(src, startMs, endMs, nowMs, tz))
                xcUrl.matches(url) -> xcFromUrl(url, startMs, endMs, tz)!!
                else -> shift(url, startMs, nowMs)
            }
        }
    }

    private fun absolute(url: String, filled: String) =
        if (filled.startsWith("http://") || filled.startsWith("https://")) filled else url + filled

    fun shift(url: String, startMs: Long, nowMs: Long): String {
        val sep = if (url.contains('?')) "&" else "?"
        return "$url${sep}utc=${startMs / 1000}&lutc=${nowMs / 1000}"
    }

    fun flussonic(url: String, startMs: Long, endMs: Long): String {
        val s = startMs / 1000
        val d = ((endMs - startMs) / 1000).coerceAtLeast(60)
        val base = url.substringBefore('?')
        val q = url.substringAfter('?', "").let { if (it.isEmpty()) "" else "?$it" }
        val dir = base.substringBeforeLast('/')
        val last = base.substringAfterLast('/')
        return when {
            last.endsWith(".m3u8") -> "$dir/${last.removeSuffix(".m3u8")}-$s-$d.m3u8$q"
            last == "mpegts" || last.endsWith(".ts") -> "$dir/timeshift_abs-$s.ts$q"
            else -> "$base/index-$s-$d.m3u8$q"
        }
    }

    fun xcFromUrl(url: String, startMs: Long, endMs: Long, tz: String): String? {
        val m = xcUrl.matchEntire(url.substringBefore('?')) ?: return null
        val (host, u, p, id) = m.destructured
        return xtream(host, u, p, id, startMs, endMs, tz)
    }

    /** Replaces the usual catch-up placeholders (seconds based). */
    fun fill(src: String, startMs: Long, endMs: Long, nowMs: Long, tz: String = ""): String {
        val s = startMs / 1000
        val e = endMs / 1000
        val n = nowMs / 1000
        val dur = (e - s).coerceAtLeast(0)
        val off = (n - s).coerceAtLeast(0)
        var out = fmtVar.replace(src) { m ->
            val t = when (m.groupValues[1]) { "end", "utcend" -> endMs; "lutc", "now", "timestamp" -> nowMs; else -> startMs }
            dateFormat(m.groupValues[2], t, tz)
        }
        out = numVar.replace(out) { m ->
            val div = m.groupValues[2].toLong().coerceAtLeast(1)
            ((if (m.groupValues[1] == "duration") dur else off) / div).toString()
        }
        val map = linkedMapOf(
            "utcend" to e, "utc" to s, "start" to s, "end" to e, "lutc" to n, "now" to n,
            "timestamp" to n, "duration" to dur, "offset" to off,
        )
        for ((k, v) in map) {
            out = out.replace("\${$k}", v.toString()).replace("{$k}", v.toString())
        }
        val c = Calendar.getInstance(zone(tz), Locale.US).apply { timeInMillis = startMs }
        out = out.replace("{Y}", "%04d".format(Locale.US, c.get(Calendar.YEAR)))
            .replace("{m}", "%02d".format(Locale.US, c.get(Calendar.MONTH) + 1))
            .replace("{d}", "%02d".format(Locale.US, c.get(Calendar.DAY_OF_MONTH)))
            .replace("{H}", "%02d".format(Locale.US, c.get(Calendar.HOUR_OF_DAY)))
            .replace("{M}", "%02d".format(Locale.US, c.get(Calendar.MINUTE)))
            .replace("{S}", "%02d".format(Locale.US, c.get(Calendar.SECOND)))
        return out
    }

    /** Formats like "Y-m-d:H-M" where Y m d H M S are date parts. */
    fun dateFormat(pattern: String, ms: Long, tz: String): String {
        val c = Calendar.getInstance(zone(tz), Locale.US).apply { timeInMillis = ms }
        val b = StringBuilder()
        for (ch in pattern) {
            b.append(
                when (ch) {
                    'Y' -> "%04d".format(Locale.US, c.get(Calendar.YEAR))
                    'm' -> "%02d".format(Locale.US, c.get(Calendar.MONTH) + 1)
                    'd' -> "%02d".format(Locale.US, c.get(Calendar.DAY_OF_MONTH))
                    'H' -> "%02d".format(Locale.US, c.get(Calendar.HOUR_OF_DAY))
                    'M' -> "%02d".format(Locale.US, c.get(Calendar.MINUTE))
                    'S' -> "%02d".format(Locale.US, c.get(Calendar.SECOND))
                    else -> ch.toString()
                }
            )
        }
        return b.toString()
    }
}
