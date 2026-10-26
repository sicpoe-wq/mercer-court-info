package com.example.mercercourt
import org.junit.Assert.*
import org.junit.Test
class ParserTest {
    private fun res(n: String): String { val u = javaClass.classLoader!!.getResource(n); org.junit.Assume.assumeTrue("fixture $n not present (personal data kept out of repo)", u != null); return u!!.readText() }
    @Test fun jail() {
        val l = Parsers.jail(res("jail.html"))
        println("JAIL ${l.size} " + l.groupingBy { it.section }.eachCount() + " first=" + l.first() + " last=" + l.last())
        assertEquals(156 + 36 + 7, l.size)
        assertEquals("Males", l.first().section)
    }
    @Test fun hearings() {
        val h = Parsers.hearings(res("daily.txt"))
        h.forEach { println("H $it") }
        assertEquals(18, h.size)
        assertTrue(h.all { it.docket.isNotEmpty() && it.time.isNotEmpty() })
    }
    @Test fun news() {
        val n = Parsers.news(res("feed.xml"))
        println("NEWS ${n.size}"); n.forEach { println("N $it") }
    }
}
