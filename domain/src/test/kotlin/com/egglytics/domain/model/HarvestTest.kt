package com.egglytics.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class HarvestTest {

    @Test
    fun `validates non-negative quantities and requires at least one egg`() {
        val harvest = Harvest(
            id = "h_1",
            fechaOperativa = LocalDate.of(2026, 11, 21),
            createdAt = Instant.parse("2026-11-21T12:00:00Z"),
            createdBy = "operator_1",
            graded = mapOf(EggClassification.AA to 100L, EggClassification.A to 20L),
            discarded = 5L,
            note = "Mañana",
        )
        assertEquals(100L, harvest.graded[EggClassification.AA])
        assertEquals(5L, harvest.discarded)
    }

    @Test
    fun `rejects negative graded count`() {
        assertThrows(IllegalArgumentException::class.java) {
            Harvest.validate(mapOf(EggClassification.AA to -1L), 0L)
        }
    }

    @Test
    fun `rejects negative discarded count`() {
        assertThrows(IllegalArgumentException::class.java) {
            Harvest.validate(mapOf(EggClassification.AA to 10L), -1L)
        }
    }

    @Test
    fun `rejects all-zero harvest`() {
        assertThrows(IllegalArgumentException::class.java) {
            Harvest.validate(mapOf(EggClassification.AA to 0L), 0L)
        }
    }

    @Test
    fun `rejects operational date before business startDate`() {
        assertThrows(IllegalArgumentException::class.java) {
            Harvest.validateOperationalDate(
                fechaOperativa = LocalDate.of(2026, 10, 31),
                businessStartDate = LocalDate.of(2026, 11, 1),
            )
        }
    }
}
