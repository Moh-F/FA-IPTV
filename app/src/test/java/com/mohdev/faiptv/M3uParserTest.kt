package com.mohdev.faiptv

import com.mohdev.faiptv.data.M3uParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class M3uParserTest {
    private val sample = """
        #EXTM3U url-tvg="http://epg.example/guide.xml.gz"
        #EXTINF:-1 tvg-id="aljazeera.qa" tvg-name="Al Jazeera" tvg-logo="http://l/aj.png" group-title="News" catchup="default" catchup-days="3" catchup-source="?utc={utc}&dur={duration}",Al Jazeera, HD
        #EXTVLCOPT:http-user-agent=MyUA/1.0
        http://s.example/live/u/p/1001.ts
        #EXTINF:-1 tvg-logo="http://l/m.jpg" group-title="Movies 2024",The Movie (2024)
        http://s.example/movie/u/p/55.mkv
        #EXTINF:-1 group-title="Drama",Big Show S01 E02
        http://s.example/series/u/p/900.mp4
        #EXTINF:-1 group-title="Drama",Big Show S01E01
        http://s.example/series/u/p/899.mp4
        #EXTINF:-1,No Group Channel
        http://other.example/stream.m3u8
    """.trimIndent()

    private val lib = M3uParser.parse(sample.reader().buffered(), "acc", now = 1000L)

    @Test fun headerEpgUrl() = assertEquals("http://epg.example/guide.xml.gz", lib.epgUrl)

    @Test fun liveChannelsWithAttributes() {
        assertEquals(2, lib.live.size)
        val aj = lib.live.first()
        assertEquals("Al Jazeera, HD", aj.name)
        assertEquals("aljazeera.qa", aj.epgId)
        assertEquals("News", aj.categoryId)
        assertEquals(3, aj.archiveDays)
        assertEquals("MyUA/1.0", aj.userAgent)
        assertEquals("—", lib.live[1].categoryId)
    }

    @Test fun moviesAndSeriesAreSplit() {
        assertEquals(1, lib.movies.size)
        assertEquals("mkv", lib.movies[0].ext)
        assertEquals(1, lib.series.size)
        assertEquals("Big Show", lib.series[0].name)
        val eps = lib.m3uEpisodes[lib.series[0].id]!!
        assertEquals(listOf(1, 2), eps.map { it.episode })
    }

    @Test fun idsAreStable() {
        val again = M3uParser.parse(sample.reader().buffered(), "acc", now = 2000L)
        assertEquals(lib.live.map { it.id }, again.live.map { it.id })
    }

    @Test fun episodeNames() {
        val e = M3uParser.episodeName("Show Name - S02E10 - Title")!!
        assertEquals("Show Name", e.series); assertEquals(2, e.season); assertEquals(10, e.episode)
        assertNull(M3uParser.episodeName("Just a movie"))
    }

    @Test fun displayNameWithCommaInAttributes() {
        assertEquals("Name", M3uParser.displayName("""#EXTINF:-1 tvg-name="a,b" group-title="x,y",Name"""))
        assertTrue(M3uParser.attrs("""#EXTINF:-1 TVG-ID="X"""").containsKey("tvg-id"))
    }
}
