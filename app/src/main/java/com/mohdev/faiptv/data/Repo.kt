package com.mohdev.faiptv.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mohdev.faiptv.core.AppLog
import com.mohdev.faiptv.core.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import java.io.File
import java.text.Normalizer
import java.util.concurrent.ConcurrentHashMap

/** Library with lookup indexes. */
class Lib(val raw: Library) {
    val liveById: Map<String, Live> = raw.live.associateBy { it.id }
    val liveByCat: Map<String, List<Live>> = raw.live.groupBy { it.categoryId }
    val moviesByCat: Map<String, List<Movie>> = raw.movies.groupBy { it.categoryId }
    val movieById: Map<String, Movie> = raw.movies.associateBy { it.id }
    val seriesByCat: Map<String, List<Series>> = raw.series.groupBy { it.categoryId }
    val seriesById: Map<String, Series> = raw.series.associateBy { it.id }
    val liveCatName: Map<String, String> = raw.liveCats.associate { it.id to it.name }
    val movieCatName: Map<String, String> = raw.movieCats.associate { it.id to it.name }
    val seriesCatName: Map<String, String> = raw.seriesCats.associate { it.id to it.name }
    val recentMovies: List<Movie> by lazy { raw.movies.sortedByDescending { it.added }.take(40) }
    val recentSeries: List<Series> by lazy { raw.series.sortedByDescending { it.added }.take(40) }
    /** Normalised names for fast search ("l<id>", "m<id>", "s<id>"). Built once in the background. */
    val normName: Map<String, String> by lazy {
        HashMap<String, String>(raw.live.size + raw.movies.size + raw.series.size).apply {
            raw.live.forEach { put("l" + it.id, Repo.norm(it.name)) }
            raw.movies.forEach { put("m" + it.id, Repo.norm(it.name)) }
            raw.series.forEach { put("s" + it.id, Repo.norm(it.name)) }
        }
    }
}

object Repo {
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var app: Context

    var lib by mutableStateOf<Lib?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    /** Background refresh progress 0..1, -1 = unknown size, null = not refreshing. */
    var progress by mutableStateOf<Float?>(null)
        private set
    var error by mutableStateOf<String?>(null)
    /** Bumps when XMLTV data arrives so lists can show "now playing". */
    var epgVersion by mutableIntStateOf(0)
        private set

    private var xmltv: Map<String, List<Programme>> = emptyMap()
    private var xmltvJob: Job? = null
    private val epgCache = ConcurrentHashMap<String, Pair<Long, List<Programme>>>()

    fun init(ctx: Context) { app = ctx.applicationContext }

    /** Binary (protobuf) cache: several times faster to load than JSON on TV sticks. */
    private fun cacheFile(id: String) = File(app.filesDir, "cache/$id.pb")
    @OptIn(ExperimentalSerializationApi::class)
    private val proto = kotlinx.serialization.protobuf.ProtoBuf
    fun playlistDir() = File(app.filesDir, "playlists").apply { mkdirs() }

    @OptIn(ExperimentalSerializationApi::class)
    suspend fun loadCache(a: Account): Boolean = withContext(Dispatchers.IO) {
        val f = cacheFile(a.id)
        if (!f.exists()) return@withContext false
        runCatching {
            val l = proto.decodeFromByteArray(Library.serializer(), f.readBytes())
            lib = Lib(l)
            scope.launch { lib?.normName?.size }
            true
        }.onFailure { AppLog.e("repo", "cache unreadable", it); f.delete() }.getOrDefault(false)
    }

    fun isStale(): Boolean {
        val l = lib?.raw ?: return true
        return System.currentTimeMillis() - l.loadedAt > Prefs.s.refreshHours * 3_600_000L
    }

    /** Downloads the full library. Keeps the old one on failure. */
    suspend fun refresh(a: Account): Result<Unit> {
        loading = true
        error = null
        progress = 0f
        return try { withContext(Dispatchers.IO) { doRefresh(a) } } finally { progress = null }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun doRefresh(a: Account): Result<Unit> {
        loading = true
        return try {
            val l = when (a.type) {
                AccountType.XTREAM -> {
                    val logged = try { Xtream.login(a) } catch (e: Xtream.LoginError) { throw e } catch (e: Exception) { null }
                    if (logged != null && Prefs.accounts.any { it.id == a.id }) Prefs.saveAccount(logged)
                    progress = 0.05f
                    var steps = 0
                    Xtream.library(logged ?: a) { synchronized(this) { steps++; progress = 0.05f + 0.85f * steps / 6f } }
                }
                AccountType.M3U -> loadM3u(a)
            }
            lib = Lib(l)
            scope.launch { lib?.normName?.size }
            epgCache.clear()
            runCatching {
                cacheFile(a.id).parentFile?.mkdirs()
                val tmp = File(cacheFile(a.id).path + ".tmp")
                tmp.writeBytes(proto.encodeToByteArray(Library.serializer(), l))
                File(app.filesDir, "cache/${a.id}.json").delete()
                tmp.renameTo(cacheFile(a.id))
            }.onFailure { AppLog.w("repo", "cache write failed: ${it.message}") }
            AppLog.i("repo", "library ${a.type}: ${l.live.size} live, ${l.movies.size} movies, ${l.series.size} series")
            startXmltv(a)
            Result.success(Unit)
        } catch (e: Exception) {
            AppLog.e("repo", "refresh failed", e)
            error = e.message ?: e.javaClass.simpleName
            Result.failure(e)
        } finally {
            loading = false
        }
    }

    private fun loadM3u(a: Account): Library {
        val src = a.m3uUrl
        return if (src.startsWith("file:")) {
            File(playlistDir(), src.removePrefix("file:")).bufferedReader().use { M3uParser.parse(it, a.id) }
        } else {
            Net.open(src, a.userAgent).use { r ->
                val total = r.body!!.contentLength()
                val counted = Counting(r.body!!.byteStream()) { n -> progress = if (total > 0) (0.9f * n / total).coerceAtMost(0.9f) else -1f }
                counted.bufferedReader().use { M3uParser.parse(it, a.id) }
            }
        }
    }

    /** Switches the active account: cache first, network when stale. */
    suspend fun activate(a: Account, forceRefresh: Boolean = false) {
        xmltvJob?.cancel()
        xmltv = emptyMap()
        epgCache.clear()
        lib = null
        val cached = loadCache(a)
        if (cached && !forceRefresh && !isStale()) { startXmltv(a); return }
        val r = refresh(a)
        if (r.isFailure && cached) startXmltv(a)
    }

    /** Reports how many bytes were read (for the refresh progress). */
    private class Counting(private val src: java.io.InputStream, private val report: (Long) -> Unit) : java.io.InputStream() {
        private var n = 0L
        private var last = 0L
        override fun read(): Int = src.read().also { if (it >= 0) step(1) }
        override fun read(b: ByteArray, off: Int, len: Int): Int = src.read(b, off, len).also { if (it > 0) step(it.toLong()) }
        private fun step(k: Long) { n += k; if (n - last > 256 * 1024) { last = n; report(n) } }
        override fun close() = src.close()
    }

    fun clear() { lib = null; xmltv = emptyMap(); epgCache.clear() }

    fun startXmltv(a: Account) {
        if (a.type != AccountType.M3U) return
        val url = a.epgUrl.ifBlank { lib?.raw?.epgUrl ?: "" }
        if (url.isBlank()) return
        val wanted = lib?.raw?.live?.mapNotNull { it.epgId.takeIf { e -> e.isNotBlank() } }?.toSet() ?: return
        xmltvJob?.cancel()
        xmltvJob = scope.launch {
            runCatching {
                val now = System.currentTimeMillis()
                val from = now - 7 * 86_400_000L
                val to = now + 2 * 86_400_000L
                val map = Net.open(url, a.userAgent).use { r -> Xmltv.parse(r.body!!.byteStream(), wanted, from, to) }
                xmltv = map
                epgVersion++
                AppLog.i("epg", "xmltv loaded for ${map.size} channels")
            }.onFailure { AppLog.w("epg", "xmltv failed: ${it.message}") }
        }
    }

    private fun shifted(list: List<Programme>): List<Programme> {
        val sh = Prefs.s.epgShiftMin * 60_000L
        return if (sh == 0L) list else list.map { it.copy(start = it.start + sh, end = it.end + sh) }
    }

    /** Programmes for a channel; [full] = include the past (catch-up). */
    suspend fun epg(a: Account, ch: Live, full: Boolean): List<Programme> = withContext(Dispatchers.IO) {
        val key = "${a.id}:${ch.id}:$full"
        epgCache[key]?.let { (t, l) -> if (System.currentTimeMillis() - t < 15 * 60_000L) return@withContext l }
        val list = when (a.type) {
            AccountType.XTREAM -> runCatching {
                if (full) Xtream.fullEpg(a, ch.id) else Xtream.shortEpg(a, ch.id)
            }.onFailure { AppLog.w("epg", "xtream epg failed: ${it.message}") }.getOrDefault(emptyList())
            AccountType.M3U -> xmltv[ch.epgId.lowercase()].orEmpty()
                .let { l -> if (full) l else l.filter { !it.isPast() }.take(12) }
        }
        val r = shifted(list)
        epgCache[key] = System.currentTimeMillis() to r
        r
    }

    /** Current programme title for list rows (M3U with XMLTV only; Xtream needs a request per channel). */
    fun nowTitle(ch: Live): String? {
        if (xmltv.isEmpty() || ch.epgId.isBlank()) return null
        val now = System.currentTimeMillis() - Prefs.s.epgShiftMin * 60_000L
        return xmltv[ch.epgId.lowercase()]?.firstOrNull { it.isNow(now) }?.title
    }

    suspend fun movieInfo(a: Account, m: Movie): MovieInfo =
        if (a.type == AccountType.XTREAM) runCatching { Xtream.movieInfo(a, m.id) }.getOrDefault(MovieInfo()) else MovieInfo()

    suspend fun seriesInfo(a: Account, s: Series): SeriesInfo = when (a.type) {
        AccountType.XTREAM -> runCatching { Xtream.seriesInfo(a, s.id) }
            .onFailure { AppLog.w("repo", "series info failed: ${it.message}") }.getOrDefault(SeriesInfo())
        AccountType.M3U -> SeriesInfo(plot = s.plot, seasons = lib?.raw?.m3uEpisodes?.get(s.id).orEmpty().groupBy { it.season }.toSortedMap())
    }

    // ---- urls / play requests
    fun liveUrl(a: Account, ch: Live): String =
        if (a.type == AccountType.XTREAM) Xtream.liveUrl(a, ch.id, Prefs.s.liveFormat) else ch.url

    fun catchupUrl(a: Account, ch: Live, p: Programme): String {
        val sh = Prefs.s.epgShiftMin * 60_000L
        val s = p.start - sh
        val e = p.end - sh
        return if (a.type == AccountType.XTREAM) CatchUp.xtream(a.server, a.username, a.password, ch.id, s, e, a.timezone)
        else CatchUp.m3u(ch, s, e, tz = a.timezone)
    }

    fun playLive(a: Account, ch: Live) = PlayRequest(
        url = liveUrl(a, ch), title = ch.name, kind = Kind.LIVE, key = "live:${ch.id}", poster = ch.logo,
        userAgent = ch.userAgent.ifBlank { a.userAgent }, referrer = ch.referrer, liveId = ch.id,
    )

    fun playCatchup(a: Account, ch: Live, p: Programme) = PlayRequest(
        url = catchupUrl(a, ch, p), title = p.title.ifBlank { ch.name }, subtitle = ch.name, kind = Kind.LIVE,
        key = "catchup:${ch.id}:${p.start}", poster = ch.logo, userAgent = ch.userAgent.ifBlank { a.userAgent },
        referrer = ch.referrer, liveId = ch.id, isCatchup = true,
    )

    fun playMovie(a: Account, m: Movie, ext: String = "") = PlayRequest(
        url = if (a.type == AccountType.XTREAM) Xtream.movieUrl(a, if (ext.isNotBlank()) m.copy(ext = ext) else m) else m.url,
        title = m.name, kind = Kind.MOVIE, key = "movie:${m.id}", poster = m.poster, userAgent = a.userAgent,
        startAtMs = Prefs.pd.resume["movie:${m.id}"]?.takeIf { !it.finished }?.posMs ?: 0,
    )

    fun playEpisode(a: Account, s: Series, e: Episode) = PlayRequest(
        url = if (a.type == AccountType.XTREAM) Xtream.episodeUrl(a, e) else e.url,
        title = s.name, subtitle = "S%02d E%02d %s".format(java.util.Locale.US, e.season, e.episode, e.title.takeIf { it != s.name } ?: ""),
        kind = Kind.SERIES, key = "ep:${e.id}", poster = s.cover.ifBlank { e.image }, userAgent = a.userAgent,
        seriesId = s.id, episodeId = e.id,
        startAtMs = Prefs.pd.resume["ep:${e.id}"]?.takeIf { !it.finished }?.posMs ?: 0,
    )

    // ---- search
    fun norm(s: String): String {
        val n = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
        return n.replace(Regex("[\\p{Mn}\\u064B-\\u065F\\u0670\\u0640]"), "")
            .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا').replace('ة', 'ه').replace('ى', 'ي')
    }
}
