package com.egglytics.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class PaymentTest {

    private val activeAaPrice = Price(
        id = "p_aa",
        classification = EggClassification.AA,
        pricePerBucket = 18500L,
        validFrom = LocalDate.of(2026, 11, 1),
        createdAt = Instant.parse("2026-11-01T10:00:00Z"),
        createdBy = "admin_1",
    )

    @Test
    fun `forFullSalePayment creates PAYMENT matching sale total in Long`() {
        val sale = Sale.createSingleLinePending(
            SingleLineSaleRequest(
                id = "sale_1",
                fechaOperativa = LocalDate.of(2026, 11, 21),
                businessStartDate = LocalDate.of(2026, 11, 1),
                createdAt = Instant.parse("2026-11-21T15:00:00Z"),
                createdBy = "operator_1",
                classification = EggClassification.AA,
                buckets = 2L,
                activePrice = activeAaPrice,
                note = "Contado",
            ),
        )
        val payment = Payment.forFullSalePayment(
            id = "pay_1",
            sale = sale,
            methodId = "cash",
        )

        assertEquals(PaymentType.PAYMENT, payment.type)
        assertEquals(37000L, payment.amount)
        assertEquals("sale_1", payment.saleId)
        assertNull(payment.openingDebtId)
        assertEquals("cash", payment.methodId)
    }

    @Test
    fun `rejects non-positive payment amount or blank methodId`() {
        assertThrows(IllegalArgumentException::class.java) {
            Payment(
                id = "pay_bad_amt",
                fechaOperativa = LocalDate.of(2026, 11, 21),
                createdAt = Instant.parse("2026-11-21T15:00:00Z"),
                createdBy = "operator_1",
                saleId = "sale_1",
                amount = 0L,
                methodId = "cash",
                type = PaymentType.PAYMENT,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            Payment(
                id = "pay_bad_method",
                fechaOperativa = LocalDate.of(2026, 11, 21),
                createdAt = Instant.parse("2026-11-21T15:00:00Z"),
                createdBy = "operator_1",
                saleId = "sale_1",
                amount = 1000L,
                methodId = "",
                type = PaymentType.PAYMENT,
            )
        }
    }
}
