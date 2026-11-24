package com.egglytics.domain.usecase

import com.egglytics.domain.model.Payment
import com.egglytics.domain.model.PaymentType
import com.egglytics.domain.model.Reversal
import com.egglytics.domain.model.ReversalTargetCollection
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class ClosingInputsTest {
    @Test
    fun `collections exclude reversed payments and payments from other days`() {
        val day = LocalDate.of(2026, 12, 1)
        val payment = Payment(
            id = "paid", fechaOperativa = day, createdAt = Instant.EPOCH, createdBy = "operator",
            saleId = "sale", amount = 18000L, methodId = "cash", type = PaymentType.PAYMENT,
        )
        val inputs = ClosingInputs(
            payments = listOf(
                payment,
                payment.copy(id = "void"),
                payment.copy(id = "later", fechaOperativa = day.plusDays(1)),
            ),
            reversals = listOf(
                Reversal("rev", day, Instant.EPOCH, "admin", ReversalTargetCollection.PAYMENTS, "void", "Error"),
            ),
        )
        val summary = buildClosingSummary(inputs, day, emptyMap(), emptyMap(), omittedCount = 0L)

        assertEquals(18000L, summary.getValue("collectionsTotal"))
    }
}
