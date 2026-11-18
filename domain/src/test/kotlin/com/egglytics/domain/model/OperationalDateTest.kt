package com.egglytics.domain.model

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class OperationalDateTest {

    @Test
    fun `parses and formats strict YYYY-MM-DD dates including leap day`() {
        val date = OperationalDate.parse("2026-11-18")
        assertEquals(LocalDate.of(2026, 11, 18), date)
        assertEquals("2026-11-18", OperationalDate.format(date))

        val leapDay = OperationalDate.parse("2024-02-29")
        assertEquals(LocalDate.of(2024, 2, 29), leapDay)
        assertEquals("2024-02-29", OperationalDate.format(leapDay))
    }

    @Test
    fun `rejects invalid calendar dates, empty text and non-ISO formats`() {
        val invalidInputs = listOf(
            "",
            "   ",
            "2026-13-45",
            "2026-02-29",
            "26-11-18",
            "2026/11/18",
            "2026-1-8",
            "2026-11-18T10:00:00Z",
        )
        invalidInputs.forEach { raw ->
            assertThrows("Debio rechazar '$raw'", IllegalArgumentException::class.java) {
                OperationalDate.parse(raw)
            }
        }
    }

    @Test
    fun `today converts instant using America Bogota timezone`() {
        // 2026-11-19T03:30:00Z es 2026-11-18 22:30 en America/Bogota (UTC-5)
        val utcClock = Clock.fixed(Instant.parse("2026-11-19T03:30:00Z"), ZoneOffset.UTC)
        assertEquals(LocalDate.of(2026, 11, 18), OperationalDate.today(utcClock))
    }
}
