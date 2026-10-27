package io.github.lightbahn

import java.nio.file.Files
import java.time.LocalDate
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CleanupAndCacheTest {

    private fun ticket(travel: String?) = TicketEntry(
        uid = 1, subject = "Buchung", date = "", text = "", files = emptyList(), travelDate = travel,
    )

    @Test
    fun `ticket expires 14 days after travel`() {
        val today = LocalDate.of(2026, 10, 26)
        assertFalse(ticket("2026-10-12").isExpired(today), "genau 14 Tage danach bleibt es noch")
        assertTrue(ticket("2026-10-11").isExpired(today), "15 Tage danach wird es gelöscht")
        assertFalse(ticket("2026-11-02").isExpired(today), "künftige Fahrt bleibt")
        assertFalse(ticket(null).isExpired(today), "ohne erkanntes Datum bleibt das Ticket")
    }

    private fun journey(dep: OffsetDateTime, minutes: Long) = Journey(
        listOf(
            Leg(
                origin = StopRef("a", "A"),
                destination = StopRef("b", "B"),
                departure = dep.toString(),
                plannedDeparture = dep.toString(),
                arrival = dep.plusMinutes(minutes).toString(),
                plannedArrival = dep.plusMinutes(minutes).toString(),
                line = Line("ICE 1"),
            ),
        ),
    )

    @Test
    fun `saved journeys are returned without network and past ones are dropped`() {
        val dir = Files.createTempDirectory("cache").toFile()
        val cache = JourneyCache(dir)
        val now = OffsetDateTime.now(BERLIN)
        val past = journey(now.minusHours(3), 60)
        val running = journey(now.minusMinutes(20), 60)
        val later = journey(now.plusHours(1), 60)
        cache.save("a", "b", null, listOf(past, running, later))

        val found = assertNotNull(cache.find("a", "b", null))
        assertEquals(listOf(running, later), found.journeys)
        assertNull(cache.find("b", "a", null), "andere Richtung ist nicht gespeichert")

        // Überlebt einen Neustart (neue Instanz liest die Datei).
        assertNotNull(JourneyCache(dir).find("a", "b", null))
    }

    @Test
    fun `later connections are appended and searches per departure are kept apart`() {
        val dir = Files.createTempDirectory("cache2").toFile()
        val cache = JourneyCache(dir)
        val tomorrow = OffsetDateTime.now(BERLIN).plusDays(1).withHour(8).withMinute(0).withSecond(0).withNano(0)
        val first = journey(tomorrow, 90)
        val second = journey(tomorrow.plusHours(1), 90)
        cache.save("a", "b", tomorrow, listOf(first))
        cache.append("a", "b", listOf(second))
        assertEquals(listOf(first, second), cache.find("a", "b", tomorrow)?.journeys)
    }
}

class StopIdTest {
    @Test
    fun `group ids lose their _G suffix`() {
        assertEquals("de-DELFI_de:08336:6578", stopId("de-DELFI_de:08336:6578_G"))
        assertEquals("de-DELFI_ch:23005:6", stopId("de-DELFI_ch:23005:6"))
    }
}
