package com.mohdev.faiptv

import com.mohdev.faiptv.data.CatchUp
import com.mohdev.faiptv.data.Live
import com.mohdev.faiptv.data.Xtream
import com.mohdev.faiptv.data.XtreamParse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatchUpXtreamTest {
    // 2024-01-15 20:00:00 UTC
    private val start = 1705348800000L
    private val end = start + 3_600_000L
    private val now = start + 7_200_000L

    @Test fun xtreamTimeshiftUrl() {
        val u = CatchUp.xtream("http://h:80", "u", "p", "42", start, end, "Europe/Berlin")
        assertEquals("http://h:80/timeshift/u/p/60/2024-01-15:21-00/42.ts", u)
    }

    @Test fun placeholders() {
        val s = CatchUp.fill("?utc={utc}&end=\${end}&d={duration}&m={duration:60}&t={Y}-{m}-{d}T{H}:{M}", start, end, now, "UTC")
        assertEquals("?utc=1705348800&end=1705352400&d=3600&m=60&t=2024-01-15T20:00", s)
        assertEquals("20240115200000", CatchUp.fill("{utc:YmdHMS}", start, end, now, "UTC"))
    }

    @Test fun m3uFlavours() {
        val ch = Live("1", 1, "x", url = "http://h/ch/index.m3u8", catchup = "flussonic")
        assertEquals("http://h/ch/index-1705348800-3600.m3u8", CatchUp.m3u(ch, start, end, now))
        val shift = ch.copy(url = "http://h/live.ts", catchup = "shift")
        assertEquals("http://h/live.ts?utc=1705348800&lutc=1705356000", CatchUp.m3u(shift, start, end, now))
        val app = ch.copy(url = "http://h/a.m3u8?x=1", catchup = "append", catchupSource = "&start={utc}")
        assertEquals("http://h/a.m3u8?x=1&start=1705348800", CatchUp.m3u(app, start, end, now))
        val xc = ch.copy(url = "http://h:8080/live/u/p/77.ts", catchup = "")
        assertEquals("http://h:8080/timeshift/u/p/60/2024-01-15:20-00/77.ts", CatchUp.m3u(xc, start, end, now, "UTC"))
    }

    @Test fun normalizeServerAndGetPhp() {
        assertEquals("http://host:8080", Xtream.normalizeServer("host:8080/"))
        assertEquals("https://h.tv", Xtream.normalizeServer("https://h.tv/player_api.php?username=a"))
        val c = Xtream.fromM3uLink("http://h.tv:8080/get.php?username=ab&password=c%40d&type=m3u_plus&output=ts")
        assertNotNull(c)
        assertEquals("http://h.tv:8080", c!!.server); assertEquals("ab", c.user); assertEquals("c@d", c.pass)
        assertNull(Xtream.fromM3uLink("http://h.tv/list.m3u"))
    }

    @Test fun lenientXtreamJson() {
        val live = XtreamParse.live("""[{"num":1,"name":" A ","stream_id":12,"stream_icon":null,"category_id":"3","tv_archive":1,"tv_archive_duration":"5"},{"name":"bad"}]""")
        assertEquals(1, live.size); assertEquals("A", live[0].name); assertEquals("12", live[0].id); assertEquals(5, live[0].archiveDays); assertEquals("", live[0].logo)
        val login = XtreamParse.login("""{"user_info":{"auth":1,"status":"Active","exp_date":"1735689600","max_connections":"2"},"server_info":{"timezone":"Europe/Berlin"}}""")
        assertTrue(login.auth); assertEquals(1735689600000L, login.expires); assertEquals(2, login.maxConnections)
    }

    @Test fun seriesInfoObjectAndArray() {
        val obj = XtreamParse.seriesInfo("""{"info":{"plot":"P","backdrop_path":["http://b.jpg"]},"episodes":{"1":[{"id":"5","episode_num":2,"title":"E2","container_extension":"mkv"},{"id":"4","episode_num":1,"title":"E1"}]}}""")
        assertEquals("http://b.jpg", obj.backdrop)
        assertEquals(listOf("4", "5"), obj.seasons[1]!!.map { it.id })
        val arr = XtreamParse.seriesInfo("""{"info":{},"episodes":[[{"id":"9","episode_num":1,"season":1}]]}""")
        assertEquals("9", arr.seasons[1]!![0].id)
    }

    @Test fun epgBase64() {
        val t = XtreamParse.epg("""{"epg_listings":[{"title":"2KfZhNij2K7YqNin2LE=","description":"","start_timestamp":"100","stop_timestamp":"200","has_archive":1}]}""")
        assertEquals("الأخبار", t[0].title)
        assertEquals(100_000L, t[0].start)
        assertTrue(t[0].hasArchive)
        assertEquals("plain text!", XtreamParse.b64("plain text!"))
    }
}
