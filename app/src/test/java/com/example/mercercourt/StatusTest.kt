package com.example.mercercourt
import org.junit.Assert.*
import org.junit.Test
/** Synthetic values only; no real roster names. */
class StatusTest {
    @Test fun mapping() {
        assertEquals(Status.SENTENCED, Status.of("SENTENCED BY A COURT OF RECORD"))
        assertEquals(Status.SENTENCED, Status.of("SENTENCED BY MINOR JUDICIARY TO MCP"))
        assertEquals(Status.AWAITING, Status.of("HELD FOR TRIAL, HEARING, WITNESS"))
        assertEquals(Status.OTHER_AGENCY, Status.of("HELD FOR VARIOUS FED/STATE/POLICE"))
        assertEquals(Status.OTHER, Status.of("TEMPORARY TRNSFER OTHER INSTITUTION"))
        assertEquals(Status.OTHER, Status.of("TRANSFERRED", "Transfers"))
        assertEquals(Status.OTHER, Status.of(""))
        assertEquals(Status.OTHER, Status.of("SOMETHING NEW"))
    }
    @Test fun names() {
        assertEquals("DOE, JANE", ujsQuery("DOE JANE"))
        assertEquals("SAMPLE-TEST, ALEX", ujsQuery("SAMPLE-TEST  ALEX"))
        assertEquals("O'EXAMPLE, PAT", ujsQuery("O'EXAMPLE PAT"))
        assertEquals("ROE, JOHN", ujsQuery("ROE JOHN QUINCY"))
        assertEquals("ROE, JOHN", ujsQuery("ROE JR JOHN"))
        assertEquals("Roe, John", ujsQuery("Roe, John Q"))
        assertEquals("SOLO", ujsQuery("SOLO"))
    }
}
class StatusFixtureTest {
    @Test fun liveRosterAllMapped() {
        val u = javaClass.classLoader!!.getResource("jail_live.html")
        org.junit.Assume.assumeTrue(u != null)
        val l = Parsers.jail(u!!.readText())
        val counts = l.groupingBy { "${it.section}|${it.admission}|${Status.of(it).label}" }.eachCount()
        counts.toSortedMap().forEach { (k, v) -> println("MAP $v $k") }
        assertTrue(l.filter { it.section != "Transfers" }.none { Status.of(it) == Status.OTHER && "TRNSFER" !in it.admission })
    }
}
