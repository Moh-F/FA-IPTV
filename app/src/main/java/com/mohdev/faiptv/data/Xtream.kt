package com.mohdev.faiptv.data

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.net.URLEncoder

/** Xtream Codes player_api.php client. */
object Xtream {
    class LoginError(val reason: String) : Exception(reason)

    /** Accepts "host:port", "http://host:port/", ".../player_api.php", or a full get.php link. */
    fun normalizeServer(input: String): String {
        var s = input.trim().trimEnd('/')
        if (s.isEmpty()) return s
        if (!s.startsWith("http://", true) && !s.startsWith("https://", true)) s = "http://$s"
        s = s.substringBefore("?")
        for (suffix in listOf("/player_api.php", "/get.php", "/xmltv.php", "/panel_api.php", "/c")) {
            if (s.endsWith(suffix, true)) s = s.dropLast(suffix.length)
        }
        return s.trimEnd('/')
    }

    data class Creds(val server: String, val user: String, val pass: String)

    /** Detects Xtream credentials inside an M3U link like http://host/get.php?username=a&password=b&type=m3u_plus */
    fun fromM3uLink(url: String): Creds? {
        val q = url.substringAfter('?', "")
        if (q.isEmpty()) return null
        val map = q.split('&').mapNotNull {
            val k = it.substringBefore('=', "")
            if (k.isEmpty()) null else k.lowercase() to java.net.URLDecoder.decode(it.substringAfter('='), "UTF-8")
        }.toMap()
        val u = map["username"] ?: return null
        val p = map["password"] ?: return null
        if (!url.substringBefore('?').endsWith("get.php", true)) return null
        return Creds(normalizeServer(url), u, p)
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    private fun api(a: Account, action: String = "", extra: String = "") =
        "${a.server}/player_api.php?username=${enc(a.username)}&password=${enc(a.password)}" +
            (if (action.isNotEmpty()) "&action=$action" else "") + extra

    suspend fun login(a: Account): Account {
        val text = try { Net.get(api(a), a.userAgent) } catch (e: Net.HttpError) {
            throw LoginError(if (e.code == 401 || e.code == 403) "auth" else "http ${e.code}")
        }
        val info = runCatching { XtreamParse.login(text) }.getOrElse { throw LoginError("format") }
        if (!info.auth) throw LoginError("auth")
        if (info.status.isNotEmpty() && !info.status.equals("Active", true)) throw LoginError("status:${info.status}")
        return a.copy(expires = info.expires, maxConnections = info.maxConnections, status = info.status, timezone = info.timezone)
    }

    suspend fun library(a: Account, onStep: () -> Unit = {}): Library = coroutineScope {
        val lc = async { XtreamParse.categories(Net.get(api(a, "get_live_categories"), a.userAgent)).also { onStep() } }
        val ls = async { XtreamParse.live(Net.get(api(a, "get_live_streams"), a.userAgent)).also { onStep() } }
        val mc = async { runCatching { XtreamParse.categories(Net.get(api(a, "get_vod_categories"), a.userAgent)) }.getOrDefault(emptyList()).also { onStep() } }
        val ms = async { runCatching { XtreamParse.movies(Net.get(api(a, "get_vod_streams"), a.userAgent)) }.getOrDefault(emptyList()).also { onStep() } }
        val sc = async { runCatching { XtreamParse.categories(Net.get(api(a, "get_series_categories"), a.userAgent)) }.getOrDefault(emptyList()).also { onStep() } }
        val ss = async { runCatching { XtreamParse.series(Net.get(api(a, "get_series"), a.userAgent)) }.getOrDefault(emptyList()).also { onStep() } }
        Library(
            accountId = a.id, loadedAt = System.currentTimeMillis(),
            liveCats = lc.await(), live = ls.await(), movieCats = mc.await(), movies = ms.await(),
            seriesCats = sc.await(), series = ss.await(),
        )
    }

    suspend fun shortEpg(a: Account, streamId: String): List<Programme> =
        XtreamParse.epg(Net.get(api(a, "get_short_epg", "&stream_id=$streamId&limit=12"), a.userAgent), a.timezone)

    suspend fun fullEpg(a: Account, streamId: String): List<Programme> =
        XtreamParse.epg(Net.get(api(a, "get_simple_data_table", "&stream_id=$streamId"), a.userAgent), a.timezone)

    suspend fun movieInfo(a: Account, id: String): MovieInfo =
        XtreamParse.movieInfo(Net.get(api(a, "get_vod_info", "&vod_id=$id"), a.userAgent))

    suspend fun seriesInfo(a: Account, id: String): SeriesInfo =
        XtreamParse.seriesInfo(Net.get(api(a, "get_series_info", "&series_id=$id"), a.userAgent))

    fun liveUrl(a: Account, id: String, format: String) = "${a.server}/live/${a.username}/${a.password}/$id.$format"
    fun movieUrl(a: Account, m: Movie) = "${a.server}/movie/${a.username}/${a.password}/${m.id}.${m.ext}"
    fun episodeUrl(a: Account, e: Episode) = "${a.server}/series/${a.username}/${a.password}/${e.id}.${e.ext}"
    fun xmltvUrl(a: Account) = "${a.server}/xmltv.php?username=${enc(a.username)}&password=${enc(a.password)}"
}
