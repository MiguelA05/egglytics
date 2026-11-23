package com.egglytics.domain.usecase

import com.egglytics.domain.model.CountKind
import com.egglytics.domain.model.FeedDepletion
import com.egglytics.domain.model.FeedIngredient
import com.egglytics.domain.model.FeedLoss
import com.egglytics.domain.model.FeedPurchase
import com.egglytics.domain.model.OpeningBalance
import com.egglytics.domain.model.OpeningKind
import com.egglytics.domain.model.PhysicalCount
import com.egglytics.domain.model.Reversal
import com.egglytics.domain.model.ReversalTargetCollection
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class FeedIngredientSummary(
    val ingredient: FeedIngredient,
    val balanceKg: Long,
    val hasBase: Boolean,
    val baseSourceLabel: String? = null,
    val depletedKgInPeriod: Long,
    val inclusiveDays: Long,
    val dailyConsumptionKg: BigDecimal?,
) {
    fun formattedDailyConsumption(): String {
        val value = dailyConsumptionKg ?: return "Sin datos"
        val rounded = value.setScale(1, RoundingMode.HALF_UP).toPlainString().replace('.', ',')
        return "$rounded kg/día"
    }
}

data class FeedSummaryRequest(
    val baseKgByIngredient: Map<FeedIngredient, Long> = emptyMap(),
    val openings: List<OpeningBalance> = emptyList(),
    val counts: List<PhysicalCount> = emptyList(),
    val purchases: List<FeedPurchase> = emptyList(),
    val depletions: List<FeedDepletion> = emptyList(),
    val losses: List<FeedLoss> = emptyList(),
    val reversals: List<Reversal> = emptyList(),
    val periodStart: LocalDate,
    val periodEnd: LocalDate,
)

class GetFeedBalanceUseCase {
    operator fun invoke(request: FeedSummaryRequest): Map<FeedIngredient, FeedIngredientSummary> {
        val reversed = ReversedFeedIds(
            purchases = reversedIds(request.reversals, ReversalTargetCollection.FEED_PURCHASES),
            depletions = reversedIds(request.reversals, ReversalTargetCollection.FEED_DEPLETIONS),
            losses = reversedIds(request.reversals, ReversalTargetCollection.FEED_LOSSES),
        )

        val inclusiveDays = if (request.periodEnd.isBefore(request.periodStart)) {
            0L
        } else {
            ChronoUnit.DAYS.between(request.periodStart, request.periodEnd) + 1L
        }

        return FeedIngredient.entries.associateWith { ingredient ->
            summarizeIngredient(ingredient, request, inclusiveDays, reversed)
        }
    }

    private fun summarizeIngredient(
        ingredient: FeedIngredient,
        request: FeedSummaryRequest,
        inclusiveDays: Long,
        reversed: ReversedFeedIds,
    ): FeedIngredientSummary {
        val startPoint = StartingPointResolver.resolveForKey(
            StartingPointQuery(
                countKind = CountKind.FEED,
                openingKind = OpeningKind.FEED,
                key = ingredient.code,
                openings = request.openings,
                counts = request.counts,
                reversals = request.reversals,
            ),
        )
        val baseKg = startPoint?.quantity ?: (request.baseKgByIngredient[ingredient] ?: 0L)
        val hasBase = startPoint != null || ingredient in request.baseKgByIngredient
        val purchasedKg = request.purchases
            .asSequence()
            .filter {
                it.id !in reversed.purchases &&
                    it.ingredient == ingredient &&
                    StartingPointResolver.movementEntersBalance(it.fechaOperativa, it.createdAt, startPoint)
            }
            .sumOf { it.totalKg }
        val activeDepletions = request.depletions
            .filter { it.id !in reversed.depletions && it.ingredient == ingredient }
        val depletedKgTotal = activeDepletions
            .asSequence()
            .filter { StartingPointResolver.movementEntersBalance(it.fechaOperativa, it.createdAt, startPoint) }
            .sumOf { it.kg }
        val lostKgTotal = request.losses
            .asSequence()
            .filter {
                it.id !in reversed.losses &&
                    it.ingredient == ingredient &&
                    StartingPointResolver.movementEntersBalance(it.fechaOperativa, it.createdAt, startPoint)
            }
            .sumOf { it.kg }
        val depletedInPeriod = activeDepletions
            .asSequence()
            .filter {
                !it.fechaOperativa.isBefore(request.periodStart) &&
                    !it.fechaOperativa.isAfter(request.periodEnd)
            }
            .sumOf { it.kg }
        val dailyAvg = if (inclusiveDays > 0L) {
            BigDecimal.valueOf(depletedInPeriod)
                .divide(BigDecimal.valueOf(inclusiveDays), DIVISION_SCALE, RoundingMode.HALF_UP)
        } else {
            null
        }
        return FeedIngredientSummary(
            ingredient = ingredient,
            balanceKg = baseKg + purchasedKg - depletedKgTotal - lostKgTotal,
            hasBase = hasBase,
            baseSourceLabel = startPoint?.sourceLabel,
            depletedKgInPeriod = depletedInPeriod,
            inclusiveDays = inclusiveDays,
            dailyConsumptionKg = dailyAvg,
        )
    }

    private fun reversedIds(
        reversals: List<Reversal>,
        collection: ReversalTargetCollection,
    ): Set<String> = reversals
        .asSequence()
        .filter { it.targetCollection == collection }
        .map { it.targetId }
        .toSet()

    private data class ReversedFeedIds(
        val purchases: Set<String>,
        val depletions: Set<String>,
        val losses: Set<String>,
    )

    private companion object {
        const val DIVISION_SCALE = 6
    }
}
