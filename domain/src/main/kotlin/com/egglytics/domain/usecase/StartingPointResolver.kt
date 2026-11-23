package com.egglytics.domain.usecase

import com.egglytics.domain.model.CountKind
import com.egglytics.domain.model.OpeningBalance
import com.egglytics.domain.model.OpeningKind
import com.egglytics.domain.model.PhysicalCount
import com.egglytics.domain.model.Reversal
import com.egglytics.domain.model.ReversalTargetCollection
import java.time.Instant
import java.time.LocalDate

data class StartingPoint(
    val quantity: Long,
    val fechaOperativa: LocalDate,
    val createdAt: Instant,
    val isClosing: Boolean,
    val sourceLabel: String,
)

data class StartingPointQuery(
    val countKind: CountKind,
    val openingKind: OpeningKind,
    val key: String,
    val openings: List<OpeningBalance>,
    val counts: List<PhysicalCount>,
    val reversals: List<Reversal>,
)

object StartingPointResolver {

    fun resolveForKey(query: StartingPointQuery): StartingPoint? {
        val latestCount = query.counts
            .asSequence()
            .filter { it.kind == query.countKind && query.key in it.items }
            .maxWithOrNull(compareBy<PhysicalCount> { it.createdAt }.thenBy { it.id })

        if (latestCount != null) {
            val item = latestCount.items.getValue(query.key)
            return StartingPoint(
                quantity = item.physical,
                fechaOperativa = latestCount.fechaOperativa,
                createdAt = latestCount.createdAt,
                isClosing = latestCount.isClosing,
                sourceLabel = if (latestCount.isClosing) "Cierre" else "Conteo",
            )
        }

        val reversedOpeningIds = query.reversals
            .asSequence()
            .filter { it.targetCollection == ReversalTargetCollection.OPENING_BALANCES }
            .map { it.targetId }
            .toSet()

        val activeOpening = query.openings
            .asSequence()
            .filter { it.id !in reversedOpeningIds && it.kind == query.openingKind && it.key == query.key }
            .maxWithOrNull(compareBy<OpeningBalance> { it.createdAt }.thenBy { it.id })

        return activeOpening?.let {
            StartingPoint(
                quantity = it.quantity,
                fechaOperativa = it.fechaOperativa,
                createdAt = it.createdAt,
                isClosing = false,
                sourceLabel = "Apertura",
            )
        }
    }

    fun movementEntersBalance(
        movementFecha: LocalDate,
        movementCreatedAt: Instant,
        startingPoint: StartingPoint?,
    ): Boolean = when {
        startingPoint == null -> true
        !movementCreatedAt.isAfter(startingPoint.createdAt) -> false
        startingPoint.isClosing -> movementFecha.isAfter(startingPoint.fechaOperativa)
        else -> !movementFecha.isBefore(startingPoint.fechaOperativa)
    }
}
