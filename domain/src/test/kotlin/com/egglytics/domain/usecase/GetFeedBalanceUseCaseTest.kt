package com.egglytics.domain.usecase

import com.egglytics.domain.model.FeedDepletion
import com.egglytics.domain.model.FeedIngredient
import com.egglytics.domain.model.FeedLoss
import com.egglytics.domain.model.FeedLossType
import com.egglytics.domain.model.FeedPurchase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class GetFeedBalanceUseCaseTest {

    private val useCase = GetFeedBalanceUseCase()
    private val now = Instant.parse("2026-11-14T15:00:00Z")

    @Test
    fun `AC-15 three bags of concentrate add 120 kg`() {
        val purchase = FeedPurchase(
            id = "fp_1",
            fechaOperativa = LocalDate.of(2026, 11, 10),
            createdAt = now,
            createdBy = "op_1",
            ingredient = FeedIngredient.CONCENTRATE,
            bags = 3L,
            totalCost = 240000L,
            methodId = "cash",
        )

        val result = useCase(
            FeedSummaryRequest(
                purchases = listOf(purchase),
                periodStart = LocalDate.of(2026, 11, 1),
                periodEnd = LocalDate.of(2026, 11, 14),
            ),
        )

        assertEquals(120L, result[FeedIngredient.CONCENTRATE]?.balanceKg)
    }

    @Test
    fun `AC-16 depleting 1 bag in 14 inclusive days subtracts 40 kg and formats 2,9 kg per day`() {
        val depletion = FeedDepletion(
            id = "fd_1",
            fechaOperativa = LocalDate.of(2026, 11, 14),
            createdAt = now,
            createdBy = "op_1",
            ingredient = FeedIngredient.CORN_BRAN,
            bags = 1L,
        )

        val result = useCase(
            FeedSummaryRequest(
                baseKgByIngredient = mapOf(FeedIngredient.CORN_BRAN to 100L),
                depletions = listOf(depletion),
                periodStart = LocalDate.of(2026, 11, 1),
                periodEnd = LocalDate.of(2026, 11, 14),
            ),
        )

        val summary = result.getValue(FeedIngredient.CORN_BRAN)
        assertEquals(60L, summary.balanceKg)
        assertEquals(14L, summary.inclusiveDays)
        assertEquals("2,9 kg/día", summary.formattedDailyConsumption())
    }

    @Test
    fun `AC-43 loss of 5 kg subtracts 5 kg and loss without reason is rejected`() {
        val purchase = FeedPurchase(
            id = "fp_1",
            fechaOperativa = LocalDate.of(2026, 11, 10),
            createdAt = now,
            createdBy = "op_1",
            ingredient = FeedIngredient.CONCENTRATE,
            bags = 3L,
            totalCost = 240000L,
            methodId = "cash",
        )
        val loss = FeedLoss(
            id = "fl_1",
            fechaOperativa = LocalDate.of(2026, 11, 11),
            createdAt = now,
            createdBy = "op_1",
            ingredient = FeedIngredient.CONCENTRATE,
            kg = 5L,
            type = FeedLossType.LOSS,
            reason = "Humedad",
        )

        val result = useCase(
            FeedSummaryRequest(
                purchases = listOf(purchase),
                losses = listOf(loss),
                periodStart = LocalDate.of(2026, 11, 1),
                periodEnd = LocalDate.of(2026, 11, 14),
            ),
        )

        assertEquals(115L, result.getValue(FeedIngredient.CONCENTRATE).balanceKg)

        assertThrows(IllegalArgumentException::class.java) {
            FeedLoss(
                id = "fl_bad",
                fechaOperativa = LocalDate.of(2026, 11, 11),
                createdAt = now,
                createdBy = "op_1",
                ingredient = FeedIngredient.CONCENTRATE,
                kg = 5L,
                type = FeedLossType.LOSS,
                reason = "   ",
            )
        }
    }
}
