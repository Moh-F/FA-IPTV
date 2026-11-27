package com.mohdev.faiptv.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.mohdev.faiptv.core.Prefs
import com.mohdev.faiptv.data.Category
import com.mohdev.faiptv.data.Lib
import com.mohdev.faiptv.data.Live
import com.mohdev.faiptv.data.Movie
import com.mohdev.faiptv.data.Series

/**
 * What the current profile sees: hidden items removed, own names and order applied,
 * kids profile limited to the parents' whitelist, locked (adult) categories kept out of "All".
 */
class Vis(val lib: Lib, val acc: String) {
    val kids = Prefs.isKids
    private val custom = Prefs.custom(acc)
    private val hidden = custom.hidden.toHashSet()
    private val allow = if (kids) Prefs.allowed(acc) else emptySet()
    val lockedLive: Set<String> = if (kids) emptySet() else lockedCats(lib, acc, "live")
    val lockedMovie: Set<String> = if (kids) emptySet() else lockedCats(lib, acc, "movie")
    val lockedSeries: Set<String> = if (kids) emptySet() else lockedCats(lib, acc, "series")

    fun name(key: String, default: String) = custom.renamed[key]?.takeIf { it.isNotBlank() } ?: default
    fun liveName(ch: Live) = name("live:${ch.id}", ch.name)
    fun catName(kind: String, c: Category) = name("${kind}cat:${c.id}", c.name)
    fun isHidden(key: String) = key in hidden

    fun liveOk(ch: Live) = "live:${ch.id}" !in hidden && "livecat:${ch.categoryId}" !in hidden &&
        (!kids || "live:cat:${ch.categoryId}" in allow || "live:ch:${ch.id}" in allow)
    fun movieOk(m: Movie) = "movie:${m.id}" !in hidden && "moviecat:${m.categoryId}" !in hidden &&
        (!kids || "movie:cat:${m.categoryId}" in allow)
    fun seriesOk(s: Series) = "series:${s.id}" !in hidden && "seriescat:${s.categoryId}" !in hidden &&
        (!kids || "series:cat:${s.categoryId}" in allow)

    val liveCats: List<Category> = Prefs.ordered(acc, "cats:live", lib.raw.liveCats.filter { c ->
        "livecat:${c.id}" !in hidden && (!kids || "live:cat:${c.id}" in allow || lib.liveByCat[c.id].orEmpty().any { "live:ch:${it.id}" in allow })
    }) { it.id }
    val movieCats: List<Category> = Prefs.ordered(acc, "cats:movie", lib.raw.movieCats.filter { c ->
        "moviecat:${c.id}" !in hidden && (!kids || "movie:cat:${c.id}" in allow)
    }) { it.id }
    val seriesCats: List<Category> = Prefs.ordered(acc, "cats:series", lib.raw.seriesCats.filter { c ->
        "seriescat:${c.id}" !in hidden && (!kids || "series:cat:${c.id}" in allow)
    }) { it.id }

    fun liveIn(catId: String): List<Live> = Prefs.ordered(acc, "live:$catId", lib.liveByCat[catId].orEmpty().filter(::liveOk)) { it.id }
    val allLive: List<Live> by lazy { lib.raw.live.filter { it.categoryId !in lockedLive && liveOk(it) } }
    val allMovies: List<Movie> by lazy { lib.raw.movies.filter { it.categoryId !in lockedMovie && movieOk(it) } }
    val allSeries: List<Series> by lazy { lib.raw.series.filter { it.categoryId !in lockedSeries && seriesOk(it) } }
}

@Composable
fun rememberVis(lib: Lib, acc: String): Vis =
    remember(lib, acc, Prefs.pd, Prefs.profileId, Prefs.unlocked, Prefs.s.autoLockAdult) { Vis(lib, acc) }
