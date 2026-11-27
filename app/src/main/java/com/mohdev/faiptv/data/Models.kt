package com.mohdev.faiptv.data

import kotlinx.serialization.Serializable

@Serializable
enum class AccountType { XTREAM, M3U }

@Serializable
data class Account(
    val id: String,
    val name: String,
    val type: AccountType,
    val server: String = "",
    val username: String = "",
    val password: String = "",
    /** M3U: http(s) url, or "file:<name>" for a playlist copied into app storage. */
    val m3uUrl: String = "",
    /** Optional XMLTV url (M3U). For Xtream the panel's own EPG is used. */
    val epgUrl: String = "",
    val userAgent: String = "",
    /** Filled after login (Xtream). */
    val expires: Long = 0,
    val maxConnections: Int = 0,
    val status: String = "",
    val timezone: String = "",
)

enum class Kind { LIVE, MOVIE, SERIES }

@Serializable
data class Category(val id: String, val name: String)

@Serializable
data class Live(
    val id: String,
    val num: Int,
    val name: String,
    val logo: String = "",
    val categoryId: String = "",
    val epgId: String = "",
    /** Direct URL (M3U). Empty for Xtream: built from the account. */
    val url: String = "",
    val archiveDays: Int = 0,
    val catchup: String = "",
    val catchupSource: String = "",
    val userAgent: String = "",
    val referrer: String = "",
)

@Serializable
data class Movie(
    val id: String,
    val name: String,
    val poster: String = "",
    val categoryId: String = "",
    val rating: String = "",
    val ext: String = "mp4",
    val url: String = "",
    val added: Long = 0,
)

@Serializable
data class Series(
    val id: String,
    val name: String,
    val cover: String = "",
    val categoryId: String = "",
    val plot: String = "",
    val rating: String = "",
    val year: String = "",
    val added: Long = 0,
)

@Serializable
data class Episode(
    val id: String,
    val title: String,
    val season: Int,
    val episode: Int,
    val ext: String = "mp4",
    val url: String = "",
    val plot: String = "",
    val image: String = "",
    val duration: String = "",
)

@Serializable
data class Library(
    val accountId: String,
    val loadedAt: Long,
    val liveCats: List<Category> = emptyList(),
    val live: List<Live> = emptyList(),
    val movieCats: List<Category> = emptyList(),
    val movies: List<Movie> = emptyList(),
    val seriesCats: List<Category> = emptyList(),
    val series: List<Series> = emptyList(),
    /** M3U only: episodes grouped by series id. Xtream loads them on demand. */
    val m3uEpisodes: Map<String, List<Episode>> = emptyMap(),
    val epgUrl: String = "",
)

data class Programme(
    val start: Long,
    val end: Long,
    val title: String,
    val desc: String = "",
    val hasArchive: Boolean = false,
) {
    fun isNow(now: Long = System.currentTimeMillis()) = now in start until end
    fun isPast(now: Long = System.currentTimeMillis()) = end <= now
}

data class MovieInfo(
    val plot: String = "",
    val cast: String = "",
    val director: String = "",
    val genre: String = "",
    val duration: String = "",
    val rating: String = "",
    val backdrop: String = "",
    val year: String = "",
    val ext: String = "",
)

data class SeriesInfo(
    val plot: String = "",
    val cast: String = "",
    val genre: String = "",
    val backdrop: String = "",
    val seasons: Map<Int, List<Episode>> = emptyMap(),
)

/** What the player should play. [key] identifies the item for resume/history. */
data class PlayRequest(
    val url: String,
    val title: String,
    val kind: Kind,
    val key: String,
    val poster: String = "",
    val subtitle: String = "",
    val userAgent: String = "",
    val referrer: String = "",
    val liveId: String? = null,
    val isCatchup: Boolean = false,
    val seriesId: String? = null,
    val episodeId: String? = null,
    val startAtMs: Long = 0,
)
