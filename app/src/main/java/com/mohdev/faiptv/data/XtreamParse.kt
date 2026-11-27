package com.mohdev.faiptv.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Lenient parsing of Xtream Codes JSON (panels mix strings, numbers and nulls freely). Pure, unit-tested. */
object XtreamParse {
    val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun JsonElement?.obj(): JsonObject? = this as? JsonObject
    fun JsonElement?.arr(): JsonArray? = this as? JsonArray
    fun JsonObject?.s(key: String): String {
        val e = this?.get(key) ?: return ""
        return when (e) {
            is JsonNull -> ""
            is JsonPrimitive -> e.content.let { if (it == "null") "" else it }
            else -> ""
        }
    }
    fun JsonObject?.i(key: String): Int = s(key).toDoubleOrNull()?.toInt() ?: 0
    fun JsonObject?.l(key: String): Long = s(key).toDoubleOrNull()?.toLong() ?: 0L

    data class LoginInfo(
        val auth: Boolean, val status: String, val expires: Long, val maxConnections: Int,
        val activeConnections: Int, val timezone: String, val message: String,
    )

    fun login(text: String): LoginInfo {
        val root = json.parseToJsonElement(text).obj() ?: error("not an object")
        val u = root["user_info"].obj()
        val srv = root["server_info"].obj()
        return LoginInfo(
            auth = u.s("auth") == "1" || u.s("auth").equals("true", true),
            status = u.s("status"),
            expires = u.l("exp_date") * 1000,
            maxConnections = u.i("max_connections"),
            activeConnections = u.i("active_cons"),
            timezone = srv.s("timezone"),
            message = u.s("message"),
        )
    }

    fun categories(text: String): List<Category> =
        (json.parseToJsonElement(text).arr() ?: JsonArray(emptyList())).mapNotNull { e ->
            val o = e.obj() ?: return@mapNotNull null
            val id = o.s("category_id")
            if (id.isEmpty()) null else Category(id, o.s("category_name").trim())
        }.distinctBy { it.id }

    fun live(text: String): List<Live> =
        (json.parseToJsonElement(text).arr() ?: JsonArray(emptyList())).mapNotNull { e ->
            val o = e.obj() ?: return@mapNotNull null
            val id = o.s("stream_id")
            if (id.isEmpty()) return@mapNotNull null
            Live(
                id = id, num = o.i("num"), name = o.s("name").trim(), logo = o.s("stream_icon"),
                categoryId = o.s("category_id"), epgId = o.s("epg_channel_id"),
                archiveDays = if (o.s("tv_archive") == "1") o.i("tv_archive_duration").coerceAtLeast(1) else 0,
            )
        }.distinctBy { it.id }

    fun movies(text: String): List<Movie> =
        (json.parseToJsonElement(text).arr() ?: JsonArray(emptyList())).mapNotNull { e ->
            val o = e.obj() ?: return@mapNotNull null
            val id = o.s("stream_id")
            if (id.isEmpty()) return@mapNotNull null
            Movie(
                id = id, name = o.s("name").trim(), poster = o.s("stream_icon"), categoryId = o.s("category_id"),
                rating = o.s("rating"), ext = o.s("container_extension").ifBlank { "mp4" }, added = o.l("added") * 1000,
            )
        }.distinctBy { it.id }

    fun series(text: String): List<Series> =
        (json.parseToJsonElement(text).arr() ?: JsonArray(emptyList())).mapNotNull { e ->
            val o = e.obj() ?: return@mapNotNull null
            val id = o.s("series_id")
            if (id.isEmpty()) return@mapNotNull null
            Series(
                id = id, name = o.s("name").trim(), cover = o.s("cover"), categoryId = o.s("category_id"),
                plot = o.s("plot"), rating = o.s("rating"),
                year = o.s("releaseDate").ifBlank { o.s("release_date") }.take(4),
                added = o.l("last_modified") * 1000,
            )
        }.distinctBy { it.id }

    private fun firstOf(o: JsonObject?, key: String): String {
        val e = o?.get(key) ?: return ""
        return when (e) {
            is JsonArray -> (e.firstOrNull() as? JsonPrimitive)?.content ?: ""
            is JsonPrimitive -> e.content
            else -> ""
        }
    }

    fun movieInfo(text: String): MovieInfo {
        val root = json.parseToJsonElement(text).obj()
        val i = root?.get("info").obj()
        val d = root?.get("movie_data").obj()
        return MovieInfo(
            plot = i.s("plot").ifBlank { i.s("description") }, cast = i.s("cast").ifBlank { i.s("actors") },
            director = i.s("director"), genre = i.s("genre"), duration = i.s("duration"),
            rating = i.s("rating"), backdrop = firstOf(i, "backdrop_path").ifBlank { i.s("movie_image") },
            year = i.s("releasedate").take(4), ext = d.s("container_extension"),
        )
    }

    fun seriesInfo(text: String): SeriesInfo {
        val root = json.parseToJsonElement(text).obj()
        val i = root?.get("info").obj()
        val eps = ArrayList<Episode>()
        fun add(e: JsonElement, seasonKey: Int) {
            val o = e.obj() ?: return
            val inf = o["info"].obj()
            eps += Episode(
                id = o.s("id"), title = o.s("title"),
                season = o.i("season").takeIf { it > 0 } ?: seasonKey,
                episode = o.i("episode_num"), ext = o.s("container_extension").ifBlank { "mp4" },
                plot = inf.s("plot"), image = inf.s("movie_image"), duration = inf.s("duration"),
            )
        }
        when (val ep = root?.get("episodes")) {
            is JsonObject -> ep.forEach { (k, v) -> v.arr()?.forEach { add(it, k.toIntOrNull() ?: 1) } }
            is JsonArray -> ep.forEachIndexed { idx, v -> (v.arr() ?: JsonArray(listOf(v))).forEach { add(it, idx + 1) } }
            else -> Unit
        }
        return SeriesInfo(
            plot = i.s("plot"), cast = i.s("cast"), genre = i.s("genre"),
            backdrop = firstOf(i, "backdrop_path").ifBlank { i.s("cover") },
            seasons = eps.filter { it.id.isNotEmpty() }.groupBy { it.season }.toSortedMap()
                .mapValues { (_, v) -> v.sortedBy { it.episode } },
        )
    }

    /** get_short_epg and get_simple_data_table. Titles/descriptions are base64. */
    fun epg(text: String, tz: String = ""): List<Programme> {
        val root = json.parseToJsonElement(text).obj()
        val list = root?.get("epg_listings").arr() ?: return emptyList()
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
            timeZone = if (tz.isBlank()) TimeZone.getTimeZone("UTC") else TimeZone.getTimeZone(tz)
        }
        return list.mapNotNull { e ->
            val o = e.obj() ?: return@mapNotNull null
            val start = o.l("start_timestamp").takeIf { it > 0 }?.times(1000)
                ?: runCatching { fmt.parse(o.s("start"))!!.time }.getOrNull() ?: return@mapNotNull null
            val end = o.l("stop_timestamp").takeIf { it > 0 }?.times(1000)
                ?: runCatching { fmt.parse(o.s("end").ifBlank { o.s("stop") })!!.time }.getOrNull() ?: return@mapNotNull null
            Programme(
                start, end, b64(o.s("title")).trim(), b64(o.s("description")).trim(),
                hasArchive = o.s("has_archive") == "1",
            )
        }.distinctBy { it.start }.sortedBy { it.start }
    }

    private const val ALPHA = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

    /** Decodes base64; returns the input unchanged if it is not valid base64 (some panels send plain text). */
    fun b64(s: String): String {
        val clean = s.trim().replace("\n", "").replace("\r", "")
        if (clean.isEmpty()) return ""
        if (clean.length % 4 != 0 || clean.any { it != '=' && ALPHA.indexOf(it) < 0 }) return s
        val out = java.io.ByteArrayOutputStream()
        var buf = 0
        var bits = 0
        for (c in clean) {
            if (c == '=') break
            buf = (buf shl 6) or ALPHA.indexOf(c)
            bits += 6
            if (bits >= 8) {
                bits -= 8
                out.write((buf shr bits) and 0xFF)
            }
        }
        val r = String(out.toByteArray(), Charsets.UTF_8)
        return if (r.any { it == '\uFFFD' || (it < ' ' && it != '\n' && it != '\r' && it != '\t') }) s else r
    }
}
