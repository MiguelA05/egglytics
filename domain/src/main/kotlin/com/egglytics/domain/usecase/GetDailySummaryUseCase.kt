package com.egglytics.domain.usecase

import com.egglytics.domain.model.DeliveryStatus
import com.egglytics.domain.model.Harvest
import com.egglytics.domain.model.Payment
import com.egglytics.domain.model.PaymentType
import com.egglytics.domain.model.Sale
import java.time.LocalDate

data class DailySummary(
    val operationalDate: LocalDate,
    val gradedEggs: Long = 0L,
    val discardedEggs: Long = 0L,
    val totalSold: Long = 0L,
    val totalCollected: Long = 0L,
    val pendingDeliveryBuckets: Long = 0L,
)

class GetDailySummaryUseCase {
    operator fun invoke(
        operationalDate: LocalDate,
        harvests: List<Harvest>,
        sales: List<Sale>,
        payments: List<Payment>,
    ): DailySummary {
        var gradedEggs = 0L
        var discardedEggs = 0L
        harvests.forEach { harvest ->
            if (harvest.fechaOperativa == operationalDate) {
                gradedEggs += harvest.graded.values.sum()
                discardedEggs += harvest.discarded
            }
        }

        var totalSold = 0L
        var pendingDeliveryBuckets = 0L
        sales.forEach { sale ->
            if (sale.fechaOperativa == operationalDate) {
                totalSold += sale.total
            }
            if (sale.deliveryStatus == DeliveryStatus.PENDING) {
                pendingDeliveryBuckets += sale.lines.sumOf { it.buckets }
            }
        }

        val totalCollected = payments
            .asSequence()
            .filter { it.fechaOperativa == operationalDate && it.type == PaymentType.PAYMENT }
            .sumOf { it.amount }

        return DailySummary(
            operationalDate = operationalDate,
            gradedEggs = gradedEggs,
            discardedEggs = discardedEggs,
            totalSold = totalSold,
            totalCollected = totalCollected,
            pendingDeliveryBuckets = pendingDeliveryBuckets,
        )
    }
}
