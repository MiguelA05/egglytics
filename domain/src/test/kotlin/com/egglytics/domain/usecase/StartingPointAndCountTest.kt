package com.egglytics.domain.usecase

import com.egglytics.domain.model.CountItem
import com.egglytics.domain.model.CountKind
import com.egglytics.domain.model.Expense
import com.egglytics.domain.model.FeedIngredient
import com.egglytics.domain.model.FeedPurchase
import com.egglytics.domain.model.OpeningBalance
import com.egglytics.domain.model.OpeningKind
import com.egglytics.domain.model.PaymentMethod
import com.egglytics.domain.model.PhysicalCount
import com.egglytics.domain.model.Reversal
import com.egglytics.domain.model.ReversalTargetCollection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class StartingPointAndCountTest {

    private val feedUseCase = GetFeedBalanceUseCase()
    private val cashUseCase = GetCashAndResultUseCase()
    private val day1 = LocalDate.of(2026, 11, 22)
    private val day2 = LocalDate.of(2026, 11, 23)
    private val tOpen = Instant.parse("2026-11-22T08:00:00Z")
    private val tCount = Instant.parse("2026-11-22T18:00:00Z")
    private val tLate = Instant.parse("2026-11-22T19:00:00Z")
    private val tNext = Instant.parse("2026-11-23T10:00:00Z")

    @Test
    fun `AC-44 partial feed count updates concentrate base to 70 kg while corn_bran keeps opening`() {
        val openings = listOf(
            OpeningBalance("ob_c", day1, tOpen, "admin_1", OpeningKind.FEED, "concentrate", 100L),
            OpeningBalance("ob_b", day1, tOpen, "admin_1", OpeningKind.FEED, "corn_bran", 50L),
        )
        val partialCount = PhysicalCount(
            id = "c_feed",
            fechaOperativa = day1,
            createdAt = tCount,
            createdBy = "op_1",
            kind = CountKind.FEED,
            items = mapOf("concentrate" to CountItem(expected = 100L, physical = 70L, reason = "Ajuste")),
            isClosing = false,
        )

        val result = feedUseCase(
            FeedSummaryRequest(
                openings = openings,
                counts = listOf(partialCount),
                periodStart = day1,
                periodEnd = day2,
            ),
        )

        assertEquals(70L, result.getValue(FeedIngredient.CONCENTRATE).balanceKg)
        assertEquals("Conteo", result.getValue(FeedIngredient.CONCENTRATE).baseSourceLabel)
        assertEquals(50L, result.getValue(FeedIngredient.CORN_BRAN).balanceKg)
        assertEquals("Apertura", result.getValue(FeedIngredient.CORN_BRAN).baseSourceLabel)
    }

    @Test
    fun `AC-45 partial cash count requires reason for -5000 diff and preserves uncounted nequi`() {
        assertThrows(IllegalArgumentException::class.java) {
            CountItem(expected = 100000L, physical = 95000L, reason = "   ")
        }

        val cash = PaymentMethod("cash", "Efectivo", active = true)
        val nequi = PaymentMethod("nequi", "Nequi", active = true)
        val openings = listOf(
            OpeningBalance("ob_cash", day1, tOpen, "admin_1", OpeningKind.CASH, "cash", 100000L),
            OpeningBalance("ob_nequi", day1, tOpen, "admin_1", OpeningKind.CASH, "nequi", 30000L),
        )
        val closingCount = PhysicalCount(
            id = "c_cash",
            fechaOperativa = day1,
            createdAt = tCount,
            createdBy = "op_1",
            kind = CountKind.CASH,
            items = mapOf("cash" to CountItem(100000L, 95000L, -5000L, "Faltante")),
            isClosing = true,
        )
        val lateExpense = Expense("exp_late", day1, tLate, "op_1", "fuel", 10000L, "cash")
        val nextDayExpense = Expense("exp_next", day2, tNext, "op_1", "fuel", 5000L, "cash")

        val summary = cashUseCase(
            CashAndResultRequest(
                methods = listOf(cash, nequi),
                openings = openings,
                counts = listOf(closingCount),
                expenses = listOf(lateExpense, nextDayExpense),
                periodStart = day1,
                periodEnd = day2,
            ),
        )

        // Late expense (day1 <= closing day1) is excluded by R-21; nextDayExpense (day2 > day1) enters
        assertEquals(90000L, summary.balancesByMethod.first { it.method.id == "cash" }.balance)
        // Uncounted Nequi keeps opening base 30000
        assertEquals(30000L, summary.balancesByMethod.first { it.method.id == "nequi" }.balance)
    }

    @Test
    fun `reversing a feed purchase removes its effect from feed balance`() {
        val purchase = FeedPurchase(
            id = "fp_1",
            fechaOperativa = day2,
            createdAt = tNext,
            createdBy = "op_1",
            ingredient = FeedIngredient.CONCENTRATE,
            bags = 2L,
            totalCost = 160000L,
            methodId = "cash",
        )
        val reversal = Reversal(
            id = "rev_fp_1",
            fechaOperativa = day2,
            createdAt = tNext.plusSeconds(10),
            createdBy = "admin_1",
            targetCollection = ReversalTargetCollection.FEED_PURCHASES,
            targetId = "fp_1",
            reason = "Error",
        )

        val result = feedUseCase(
            FeedSummaryRequest(
                purchases = listOf(purchase),
                reversals = listOf(reversal),
                periodStart = day2,
                periodEnd = day2,
            ),
        )

        assertEquals(0L, result.getValue(FeedIngredient.CONCENTRATE).balanceKg)
    }
}
