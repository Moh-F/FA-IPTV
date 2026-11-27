package com.mohdev.faiptv.data

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedInputStream
import java.io.InputStream
import java.util.Calendar
import java.util.TimeZone
import java.util.zip.GZIPInputStream

/** Streaming XMLTV reader. Keeps only wanted channels and a time window to stay light on TV sticks. */
object Xmltv {
    /** "20240101123000 +0100" -> epoch ms. Pure. */
    fun time(s: String): Long? {
        val t = s.trim()
        if (t.length < 12) return null
        return runCatching {
            val c = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            c.clear()
            c.set(
                t.substring(0, 4).toInt(), t.substring(4, 6).toInt() - 1, t.substring(6, 8).toInt(),
                t.substring(8, 10).toInt(), t.substring(10, 12).toInt(),
                if (t.length >= 14 && t[12].isDigit()) t.substring(12, 14).toInt() else 0,
            )
            var ms = c.timeInMillis
            val tz = t.substringAfter(' ', "").trim()
            if (tz.length == 5 && (tz[0] == '+' || tz[0] == '-')) {
                val off = (tz.substring(1, 3).toInt() * 60 + tz.substring(3, 5).toInt()) * 60_000L
                ms -= if (tz[0] == '+') off else -off
            }
            ms
        }.getOrNull()
    }

    fun parse(input: InputStream, wanted: Set<String>, fromMs: Long, toMs: Long): Map<String, List<Programme>> {
        val buffered = BufferedInputStream(input, 64 * 1024)
        buffered.mark(4)
        val b1 = buffered.read(); val b2 = buffered.read()
        buffered.reset()
        val stream: InputStream = if (b1 == 0x1f && b2 == 0x8b) GZIPInputStream(buffered, 64 * 1024) else buffered
        val wantedLower = wanted.map { it.lowercase() }.toSet()
        val out = HashMap<String, MutableList<Programme>>()
        val p = Xml.newPullParser()
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        p.setInput(stream, null)
        var ev = p.eventType
        var chan: String? = null
        var start = 0L
        var stop = 0L
        var title = ""
        var desc = ""
        var field = ""
        while (ev != XmlPullParser.END_DOCUMENT) {
            when (ev) {
                XmlPullParser.START_TAG -> when (p.name) {
                    "programme" -> {
                        val c = p.getAttributeValue(null, "channel")?.lowercase()
                        val s = time(p.getAttributeValue(null, "start") ?: "")
                        val e = time(p.getAttributeValue(null, "stop") ?: "")
                        if (c != null && c in wantedLower && s != null && e != null && e > fromMs && s < toMs) {
                            chan = c; start = s; stop = e; title = ""; desc = ""
                        } else chan = null
                    }
                    "title" -> field = if (chan != null && title.isEmpty()) "title" else ""
                    "desc" -> field = if (chan != null && desc.isEmpty()) "desc" else ""
                }
                XmlPullParser.TEXT -> when (field) {
                    "title" -> title += p.text
                    "desc" -> desc += p.text
                }
                XmlPullParser.END_TAG -> when (p.name) {
                    "title", "desc" -> field = ""
                    "programme" -> {
                        chan?.let { out.getOrPut(it) { ArrayList() } += Programme(start, stop, title.trim(), desc.trim().take(400)) }
                        chan = null
                    }
                }
            }
            ev = p.next()
        }
        return out.mapValues { (_, v) -> v.sortedBy { it.start } }
    }
}
