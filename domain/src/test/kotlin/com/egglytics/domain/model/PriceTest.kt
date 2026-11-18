package com.egglytics.domain.model

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PriceTest {

    @Test
    fun `accepts positive price per bucket`() {
        val price = Price(
            id = "p1",
            classification = EggClassification.AA,
            pricePerBucket = 18_000L,
            validFrom = LocalDate.of(2026, 11, 1),
            createdAt = Instant.parse("2026-11-01T10:00:00Z"),
            createdBy = "admin_uid",
        )
        assertEquals(18_000L, price.pricePerBucket)
    }

    @Test
    fun `rejects zero or negative price per bucket`() {
        assertThrows(IllegalArgumentException::class.java) {
            Price(
                id = "p_zero",
                classification = EggClassification.AA,
                pricePerBucket = 0L,
                validFrom = LocalDate.of(2026, 11, 1),
                createdAt = Instant.parse("2026-11-01T10:00:00Z"),
                createdBy = "admin_uid",
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            Price(
                id = "p_neg",
                classification = EggClassification.AA,
                pricePerBucket = -500L,
                validFrom = LocalDate.of(2026, 11, 1),
                createdAt = Instant.parse("2026-11-01T10:00:00Z"),
                createdBy = "admin_uid",
            )
        }
    }
}
