package com.egglytics.domain.usecase

import com.egglytics.domain.model.DeliveryStatus
import com.egglytics.domain.model.EggClassification
import com.egglytics.domain.model.Expense
import com.egglytics.domain.model.FeedDepletion
import com.egglytics.domain.model.FeedIngredient
import com.egglytics.domain.model.FeedPurchase
import com.egglytics.domain.model.Payment
import com.egglytics.domain.model.PaymentMethod
import com.egglytics.domain.model.PaymentType
import com.egglytics.domain.model.Sale
import com.egglytics.domain.model.SaleLine
import com.egglytics.domain.model.Withdrawal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class GetCashAndResultUseCaseTest {

    private val useCase = GetCashAndResultUseCase()
    private val cash = PaymentMethod("cash", "Efectivo", active = true)
    private val nequiDisabled = PaymentMethod("nequi", "Nequi", active = false)
    private val now = Instant.parse("2026-11-23T15:00:00Z")
    private val today = LocalDate.of(2026, 11, 23)

    @Test
    fun `AC-17 AC-18 AC-19 cash deducts expense, feed purchase once, withdrawal and refunds`() {
        val line = SaleLine.create(EggClassification.AA, buckets = 5L, listPrice = 20000L)
        val sale = Sale("s_1", today, now, "op_1", listOf(line), 100000L, DeliveryStatus.DELIVERED, now, "op_1")
        val payment = Payment(
            "p_1", today, now, "op_1", saleId = "s_1",
            amount = 100000L, methodId = "cash", type = PaymentType.PAYMENT,
        )
        val refund = Payment(
            "p_ref", today, now, "admin_1", saleId = "s_1",
            amount = 5000L, methodId = "nequi", type = PaymentType.OVERPAYMENT_REFUND, reason = "Ajuste",
        )
        val fuelExpense = Expense("exp_1", today, now, "op_1", "fuel", 20000L, "cash")
        val feedPurchase = FeedPurchase(
            "fp_1", today, now, "op_1", FeedIngredient.CONCENTRATE,
            1L, totalCost = 80000L, methodId = "cash",
        )
        val withdrawal = Withdrawal("w_1", today, now, "op_1", 15000L, "cash")

        val summary = useCase(
            CashAndResultRequest(
                methods = listOf(cash, nequiDisabled),
                baseByMethod = mapOf("cash" to 50000L, "nequi" to 30000L),
                sales = listOf(sale),
                payments = listOf(payment, refund),
                expenses = listOf(fuelExpense),
                feedPurchases = listOf(feedPurchase),
                withdrawals = listOf(withdrawal),
                periodStart = today,
                periodEnd = today,
            ),
        )

        val cashBalance = summary.balancesByMethod.first { it.method.id == "cash" }
        assertEquals(35000L, cashBalance.balance)
        assertEquals(80000L, cashBalance.feedPurchases)
        assertEquals(25000L, summary.balancesByMethod.first { it.method.id == "nequi" }.balance)
        assertEquals(100000L, summary.resultSummary.netSales)
        assertEquals(20000L, summary.resultSummary.operatingExpenses)
        assertEquals(80000L, summary.resultSummary.feedPurchaseOutflows)
        assertEquals(80000L, summary.resultSummary.approximateResult)
    }

    @Test
    fun `AC-46 weighted avg cost per kg rounds to whole peso, falls back to last purchase, or returns null`() {
        val p1 = FeedPurchase(
            "fp_1", today.minusDays(5), now, "op_1",
            FeedIngredient.CONCENTRATE, 3L, totalCost = 240001L, methodId = "cash",
        )
        val dep = FeedDepletion("fd_1", today, now, "op_1", FeedIngredient.CONCENTRATE, 1L)
        val res1 = useCase(
            CashAndResultRequest(
                methods = listOf(cash),
                feedPurchases = listOf(p1),
                feedDepletions = listOf(dep),
                periodStart = today,
                periodEnd = today,
            ),
        )
        assertEquals(80000L, res1.resultSummary.estimatedFeedCost)

        val oldPurchase = FeedPurchase(
            "fp_old", today.minusDays(120), now, "op_1",
            FeedIngredient.CONCENTRATE, 1L, totalCost = 90000L, methodId = "cash",
        )
        val res2 = useCase(
            CashAndResultRequest(
                methods = listOf(cash),
                feedPurchases = listOf(oldPurchase),
                feedDepletions = listOf(dep),
                periodStart = today,
                periodEnd = today,
            ),
        )
        assertEquals(90000L, res2.resultSummary.estimatedFeedCost)

        val res3 = useCase(
            CashAndResultRequest(
                methods = listOf(cash),
                feedPurchases = emptyList(),
                feedDepletions = listOf(dep),
                periodStart = today,
                periodEnd = today,
            ),
        )
        assertNull(res3.resultSummary.estimatedFeedCost)
        assertNull(res3.resultSummary.approximateResult)
    }
}
