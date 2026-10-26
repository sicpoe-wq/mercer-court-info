package com.example.mercercourt
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class NewsGeoTest {
    private fun res(n: String): String { val u = javaClass.classLoader!!.getResource(n); org.junit.Assume.assumeTrue("fixture $n not present (personal data kept out of repo)", u != null); return u!!.readText() }
    private val assets = listOf("src/main/assets", "app/src/main/assets").map(::File).first { it.exists() }
    private val geo by lazy { Geo(File(assets, "places.tsv").inputStream(), File(assets, "zips.csv").inputStream()) }

    @Test fun zipAndDistance() {
        val z = geo.zip("16146")!! // Sharon PA
        val sharon = geo.places["Sharon"]!!
        val d = Geo.miles(z.first, z.second, sharon.lat, sharon.lon)
        println("ZIP 16146 -> $z ; Sharon $d mi; zips=${geo.zips.size} places=${geo.places.size}")
        assertTrue(d < 3)
        val y = geo.places["Youngstown"]!!
        val d2 = Geo.miles(z.first, z.second, y.lat, y.lon); println("Sharon ZIP -> Youngstown $d2 mi")
        assertTrue(d2 in 12.0..20.0)
        assertNotNull(geo.zip("44503")); assertNotNull(geo.zip("90210"))
    }

    @Test fun detect() {
        assertEquals(listOf("Grove City", "Mercer County"), geo.detect("Grove City man charged in Mercer County crash").map { it.name })
        assertEquals("Hermitage", geo.detect("HERMITAGE, Pa. (WKBN) – Police arrested").first().name)
        assertEquals("Pymatuning Township", geo.detect("Crash in Pymatuning Township").first().name)
        assertTrue(geo.detect("Washington lawmakers vote on Liberty bill").isEmpty())
    }

    @Test fun liveBundle() {
        val r = NewsParser.parseBundle(res("news_bundle.txt"), geo = geo)
        println("OK=${r.okFeeds} FAILED=${r.failedFeeds} stories=${r.stories.size} police=${r.stories.count { it.police }} placed=${r.stories.count { it.places.isNotEmpty() }}")
        val home = geo.zip("16148")!! // Hermitage
        r.stories.filter { it.police }.take(40).forEach { s ->
            val near = s.places.minByOrNull { Geo.miles(home.first, home.second, it.lat, it.lon) }
            println("P [${s.source}] ${s.title} | ${s.places.joinToString { it.name }} | " + (near?.let { "%s %.0f mi".format(it.name, Geo.miles(home.first, home.second, it.lat, it.lon)) } ?: "-"))
        }
        assertTrue(r.stories.size > 100)
        assertTrue(r.stories.zipWithNext().all { (a, b) -> a.time >= b.time })
        assertEquals(r.stories.size, r.stories.map { NewsParser.key(it.title) }.toSet().size)
        assertTrue(r.stories.count { it.police } > 20)
        val within25 = r.stories.filter { it.police && it.places.any { p -> Geo.miles(home.first, home.second, p.lat, p.lon) <= 25 } }
        println("police within 25 mi of 16148: ${within25.size}")
        assertTrue(within25.isNotEmpty())
    }

    @Test fun dates() {
        assertTrue(NewsParser.parseDate("Mon, 26 Oct 2026 05:15:00 -0400") > 0)
        assertTrue(NewsParser.parseDate("Fri, 09 Oct 2026 07:00:00 GMT") > 0)
        assertTrue(NewsParser.parseDate("Mon, 26 Oct 2026 09:49:38 +0000") > 0)
    }

    @Test fun tracker() {
        val live = Parsers.jail(res("jail_live.html")); val old = Parsers.jail(res("jail.html"))
        println("jail old=${old.size} live=${live.size}")
        assertTrue(live.size > 50)
        val t0 = 1_000_000L
        var s = JailTracker.merge(JailTracker.State(), old, t0)
        assertEquals(0, old.count { JailTracker.isNew(s, it, t0) }) // first run seeds silently
        s = JailTracker.decode(JailTracker.encode(s))
        val t1 = t0 + 3600_000
        s = JailTracker.merge(s, live, t1)
        val newOnes = live.filter { JailTracker.isNew(s, it, t1) }
        val oldKeys = old.map(JailTracker::key).toSet()
        assertEquals(live.count { JailTracker.key(it) !in oldKeys }, newOnes.size)
        val gone = JailTracker.recentlyGone(s, t1)
        println("new since Oct 23 snapshot: ${newOnes.size} ${newOnes.take(3).map { it.name }}; left roster: ${gone.size}")
        // badge expires after 48h; empty fetch changes nothing
        assertEquals(0, live.count { JailTracker.isNew(s, it, t1 + JailTracker.NEW_WINDOW_MS + 1) })
        assertEquals(s, JailTracker.merge(s, emptyList(), t1 + 5))
        val rt = JailTracker.decode(JailTracker.encode(s))
        assertEquals(s.firstSeen, rt.firstSeen); assertEquals(s.gone, rt.gone); assertEquals(s.lastKeys, rt.lastKeys)
    }
}
