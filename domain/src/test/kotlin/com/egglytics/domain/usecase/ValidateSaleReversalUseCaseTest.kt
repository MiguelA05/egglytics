package com.egglytics.domain.usecase

import com.egglytics.domain.model.DeliveryStatus
import com.egglytics.domain.model.EggClassification
import com.egglytics.domain.model.Payment
import com.egglytics.domain.model.PaymentType
import com.egglytics.domain.model.Reversal
import com.egglytics.domain.model.ReversalTargetCollection
import com.egglytics.domain.model.Sale
import com.egglytics.domain.model.SaleLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class ValidateSaleReversalUseCaseTest {

    private val useCase = ValidateSaleReversalUseCase()
    private val today = LocalDate.of(2026, 11, 23)
    private val now = Instant.parse("2026-11-23T15:00:00Z")

    private val baseSale = Sale(
        id = "s1",
        fechaOperativa = today,
        createdAt = now,
        createdBy = "op_1",
        lines = listOf(SaleLine.create(EggClassification.AA, buckets = 2L, listPrice = 18500L)),
        total = 37000L,
        deliveryStatus = DeliveryStatus.PENDING,
    )

    private val fullPayment = Payment(
        id = "p1",
        fechaOperativa = today,
        createdAt = now,
        createdBy = "op_1",
        saleId = "s1",
        amount = 37000L,
        methodId = "cash",
        type = PaymentType.PAYMENT,
    )

    @Test
    fun `allows confirmed sale with single matching full payment and no returns`() {
        val result = useCase(
            SaleReversalValidationRequest(
                sale = baseSale,
                saleIsPendingWrite = false,
                salePayments = listOf(fullPayment),
                pendingPaymentIds = emptySet(),
                hasActiveReturns = false,
                reversals = emptyList(),
            ),
        )

        assertEquals(SaleReversalEligibility.Allowed(fullPayment), result)
    }

    @Test
    fun `blocks sale with multiple active payments`() {
        val p1 = fullPayment.copy(id = "p1", amount = 20000L)
        val p2 = fullPayment.copy(id = "p2", amount = 17000L)

        val result = useCase(
            SaleReversalValidationRequest(
                sale = baseSale,
                saleIsPendingWrite = false,
                salePayments = listOf(p1, p2),
                pendingPaymentIds = emptySet(),
                hasActiveReturns = false,
                reversals = emptyList(),
            ),
        )

        assertTrue(result is SaleReversalEligibility.Blocked)
        assertTrue((result as SaleReversalEligibility.Blocked).reason.contains("varios pagos"))
    }

    @Test
    fun `blocks sale with active returns`() {
        val result = useCase(
            SaleReversalValidationRequest(
                sale = baseSale,
                saleIsPendingWrite = false,
                salePayments = listOf(fullPayment),
                pendingPaymentIds = emptySet(),
                hasActiveReturns = true,
                reversals = emptyList(),
            ),
        )

        assertTrue(result is SaleReversalEligibility.Blocked)
        assertTrue((result as SaleReversalEligibility.Blocked).reason.contains("devoluciones"))
    }

    @Test
    fun `blocks sale without payment or with partial payment`() {
        val unpaidResult = useCase(
            SaleReversalValidationRequest(
                sale = baseSale,
                saleIsPendingWrite = false,
                salePayments = emptyList(),
                pendingPaymentIds = emptySet(),
                hasActiveReturns = false,
                reversals = emptyList(),
            ),
        )
        assertTrue(unpaidResult is SaleReversalEligibility.Blocked)

        val partialResult = useCase(
            SaleReversalValidationRequest(
                sale = baseSale,
                saleIsPendingWrite = false,
                salePayments = listOf(fullPayment.copy(amount = 10000L)),
                pendingPaymentIds = emptySet(),
                hasActiveReturns = false,
                reversals = emptyList(),
            ),
        )
        assertTrue(partialResult is SaleReversalEligibility.Blocked)
    }

    @Test
    fun `blocks already reversed sale`() {
        val rev = Reversal(
            id = Reversal.saleReversalId("s1"),
            fechaOperativa = today,
            createdAt = now,
            createdBy = "admin_1",
            targetCollection = ReversalTargetCollection.SALES,
            targetId = "s1",
            reason = "Error de registro",
        )

        val result = useCase(
            SaleReversalValidationRequest(
                sale = baseSale,
                saleIsPendingWrite = false,
                salePayments = listOf(fullPayment),
                pendingPaymentIds = emptySet(),
                hasActiveReturns = false,
                reversals = listOf(rev),
            ),
        )

        assertTrue(result is SaleReversalEligibility.Blocked)
        assertTrue((result as SaleReversalEligibility.Blocked).reason.contains("anulada"))
    }

    @Test
    fun `blocks delivered sale`() {
        val deliveredSale = baseSale.copy(
            deliveryStatus = DeliveryStatus.DELIVERED,
            deliveredAt = now.plusSeconds(60),
            deliveredBy = "op_1",
        )

        val result = useCase(
            SaleReversalValidationRequest(
                sale = deliveredSale,
                saleIsPendingWrite = false,
                salePayments = listOf(fullPayment),
                pendingPaymentIds = emptySet(),
                hasActiveReturns = false,
                reversals = emptyList(),
            ),
        )

        assertTrue(result is SaleReversalEligibility.Blocked)
        assertTrue((result as SaleReversalEligibility.Blocked).reason.contains("entregada"))
    }
}
