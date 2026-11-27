package com.mohdev.faiptv.data

import java.io.BufferedReader

/** Streaming M3U/M3U8 playlist parser. Splits entries into live channels, movies and series episodes. */
object M3uParser {
    private val attr = Regex("""([\w-]+)\s*=\s*"([^"]*)"""")
    private val episodeRx = Regex("""^(.*?)[\s._\-|]*[Ss](\d{1,2})[\s._\-]*[Ee](\d{1,4})\b.*$""")
    private val vodExt = setOf("mp4", "mkv", "avi", "mov", "m4v", "wmv", "flv", "webm", "mpg", "mpeg")

    data class Entry(
        val name: String,
        val url: String,
        val attrs: Map<String, String>,
        val group: String,
        val userAgent: String,
        val referrer: String,
    )

    fun parse(reader: BufferedReader, accountId: String, now: Long = System.currentTimeMillis()): Library {
        var epgUrl = ""
        val entries = ArrayList<Entry>()
        var info: String? = null
        var group = ""
        var ua = ""
        var ref = ""
        reader.useLines { lines ->
            for (raw in lines) {
                val line = raw.trim().removePrefix("﻿")
                if (line.isEmpty()) continue
                when {
                    line.startsWith("#EXTM3U", true) -> {
                        val a = attrs(line)
                        epgUrl = (a["url-tvg"] ?: a["x-tvg-url"] ?: a["tvg-url"] ?: "").split(',').first().trim()
                    }
                    line.startsWith("#EXTINF", true) -> { info = line; group = ""; ua = ""; ref = "" }
                    line.startsWith("#EXTGRP:", true) -> group = line.substringAfter(':').trim()
                    line.startsWith("#EXTVLCOPT:", true) -> {
                        val kv = line.substringAfter(':')
                        val k = kv.substringBefore('=').trim().lowercase()
                        val v = kv.substringAfter('=', "").trim()
                        if (k == "http-user-agent") ua = v
                        if (k == "http-referrer" || k == "http-referer") ref = v
                    }
                    line.startsWith("#") -> Unit
                    else -> {
                        val inf = info ?: "#EXTINF:-1,${line.substringAfterLast('/')}"
                        val a = attrs(inf)
                        val name = displayName(inf).ifBlank { a["tvg-name"] ?: line.substringAfterLast('/') }
                        entries += Entry(name, line, a, a["group-title"]?.takeIf { it.isNotBlank() } ?: group, ua, ref)
                        info = null
                    }
                }
            }
        }
        return build(entries, accountId, epgUrl, now)
    }

    fun attrs(line: String): Map<String, String> =
        attr.findAll(line).associate { it.groupValues[1].lowercase() to it.groupValues[2] }

    /** Title after the comma that follows the attribute list. */
    fun displayName(extinf: String): String {
        val lastQuote = extinf.lastIndexOf('"')
        val comma = extinf.indexOf(',', if (lastQuote >= 0) lastQuote else 0)
        return if (comma >= 0) extinf.substring(comma + 1).trim() else ""
    }

    enum class Type { LIVE, MOVIE, EPISODE }

    fun classify(e: Entry): Type {
        val path = e.url.substringBefore('?').lowercase()
        if (path.contains("/series/")) return Type.EPISODE
        if (path.contains("/movie/")) return Type.MOVIE
        val ext = path.substringAfterLast('/').substringAfterLast('.', "")
        if (ext in vodExt) return if (episodeRx.matches(e.name)) Type.EPISODE else Type.MOVIE
        return Type.LIVE
    }

    data class EpisodeName(val series: String, val season: Int, val episode: Int)

    fun episodeName(name: String): EpisodeName? {
        val m = episodeRx.matchEntire(name.trim()) ?: return null
        val s = m.groupValues[1].trim().trimEnd('-', '|', '.', '_').trim()
        return EpisodeName(s.ifBlank { name }, m.groupValues[2].toInt(), m.groupValues[3].toInt())
    }

    private fun stableId(prefix: String, vararg parts: String): String =
        prefix + Integer.toHexString(parts.joinToString("|").hashCode())

    private fun build(entries: List<Entry>, accountId: String, epgUrl: String, now: Long): Library {
        val live = ArrayList<Live>()
        val movies = ArrayList<Movie>()
        val seriesMap = LinkedHashMap<String, Series>()
        val episodes = HashMap<String, MutableList<Episode>>()
        val liveCats = LinkedHashSet<String>()
        val movieCats = LinkedHashSet<String>()
        val seriesCats = LinkedHashSet<String>()
        val used = HashSet<String>()
        fun unique(id: String): String {
            var i = id; var n = 1
            while (!used.add(i)) i = "$id-${n++}"
            return i
        }
        var num = 0
        for (e in entries) {
            val group = e.group.ifBlank { "—" }
            val ext = e.url.substringBefore('?').substringAfterLast('.', "mp4").take(5)
            when (classify(e)) {
                Type.LIVE -> {
                    liveCats += group
                    val days = (e.attrs["catchup-days"] ?: e.attrs["timeshift"] ?: e.attrs["tvg-rec"])?.toIntOrNull() ?: 0
                    live += Live(
                        id = unique(stableId("l", group, e.name, e.attrs["tvg-id"] ?: "")),
                        num = (e.attrs["tvg-chno"]?.toIntOrNull() ?: ++num).also { if (it > num) num = it },
                        name = e.name,
                        logo = e.attrs["tvg-logo"] ?: "",
                        categoryId = group,
                        epgId = e.attrs["tvg-id"]?.takeIf { it.isNotBlank() } ?: e.attrs["tvg-name"] ?: "",
                        url = e.url,
                        archiveDays = if (e.attrs.containsKey("catchup") || e.attrs.containsKey("catchup-days") || days > 0) days.coerceAtLeast(1) else 0,
                        catchup = e.attrs["catchup"] ?: e.attrs["catchup-type"] ?: "",
                        catchupSource = e.attrs["catchup-source"] ?: "",
                        userAgent = e.userAgent,
                        referrer = e.referrer,
                    )
                }
                Type.MOVIE -> {
                    movieCats += group
                    movies += Movie(
                        id = unique(stableId("m", group, e.name)),
                        name = e.name,
                        poster = e.attrs["tvg-logo"] ?: "",
                        categoryId = group,
                        ext = ext,
                        url = e.url,
                        added = now,
                    )
                }
                Type.EPISODE -> {
                    seriesCats += group
                    val en = episodeName(e.name) ?: EpisodeName(e.name, 1, (episodes.size + 1))
                    val sid = stableId("s", group, en.series.lowercase())
                    seriesMap.getOrPut(sid) {
                        Series(sid, en.series, e.attrs["tvg-logo"] ?: "", group, added = now)
                    }
                    episodes.getOrPut(sid) { ArrayList() } += Episode(
                        id = stableId("e", sid, en.season.toString(), en.episode.toString(), e.url),
                        title = e.name,
                        season = en.season,
                        episode = en.episode,
                        ext = ext,
                        url = e.url,
                        image = e.attrs["tvg-logo"] ?: "",
                    )
                }
            }
        }
        return Library(
            accountId = accountId,
            loadedAt = now,
            liveCats = liveCats.map { Category(it, it) },
            live = live,
            movieCats = movieCats.map { Category(it, it) },
            movies = movies,
            seriesCats = seriesCats.map { Category(it, it) },
            series = seriesMap.values.toList(),
            m3uEpisodes = episodes.mapValues { (_, v) -> v.sortedWith(compareBy({ it.season }, { it.episode })) },
            epgUrl = epgUrl,
        )
    }
}
