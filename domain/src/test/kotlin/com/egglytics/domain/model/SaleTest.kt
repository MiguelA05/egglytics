package com.egglytics.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class SaleTest {

    private val activePrice = Price(
        id = "p_aa",
        classification = EggClassification.AA,
        pricePerBucket = 18500L,
        validFrom = LocalDate.of(2026, 11, 1),
        createdAt = Instant.parse("2026-11-01T10:00:00Z"),
        createdBy = "admin_1",
    )

    @Test
    fun `creates single-line pending sale with Long subtotal and total`() {
        val sale = Sale.createSingleLinePending(
            SingleLineSaleRequest(
                id = "sale_1",
                fechaOperativa = LocalDate.of(2026, 11, 21),
                businessStartDate = LocalDate.of(2026, 11, 1),
                createdAt = Instant.parse("2026-11-21T15:00:00Z"),
                createdBy = "operator_1",
                classification = EggClassification.AA,
                buckets = 3L,
                activePrice = activePrice,
                note = "Venta tienda",
            ),
        )

        assertEquals(DeliveryStatus.PENDING, sale.deliveryStatus)
        assertNull(sale.deliveredAt)
        assertNull(sale.deliveredBy)
        assertEquals(1, sale.lines.size)
        assertEquals(90L, sale.lines.first().eggs)
        assertEquals(55500L, sale.lines.first().subtotal)
        assertEquals(55500L, sale.total)
    }

    @Test
    fun `rejects non-positive buckets`() {
        assertThrows(IllegalArgumentException::class.java) {
            Sale.createSingleLinePending(
                SingleLineSaleRequest(
                    id = "sale_bad",
                    fechaOperativa = LocalDate.of(2026, 11, 21),
                    businessStartDate = LocalDate.of(2026, 11, 1),
                    createdAt = Instant.parse("2026-11-21T15:00:00Z"),
                    createdBy = "operator_1",
                    classification = EggClassification.AA,
                    buckets = 0L,
                    activePrice = activePrice,
                ),
            )
        }
    }

    @Test
    fun `rejects operational date before business startDate`() {
        assertThrows(IllegalArgumentException::class.java) {
            Sale.createSingleLinePending(
                SingleLineSaleRequest(
                    id = "sale_old",
                    fechaOperativa = LocalDate.of(2026, 10, 31),
                    businessStartDate = LocalDate.of(2026, 11, 1),
                    createdAt = Instant.parse("2026-11-21T15:00:00Z"),
                    createdBy = "operator_1",
                    classification = EggClassification.AA,
                    buckets = 2L,
                    activePrice = activePrice,
                ),
            )
        }
    }
}
