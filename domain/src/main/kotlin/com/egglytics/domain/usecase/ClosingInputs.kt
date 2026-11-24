package com.egglytics.domain.usecase

import com.egglytics.domain.model.CountItem
import com.egglytics.domain.model.Expense
import com.egglytics.domain.model.FeedPurchase
import com.egglytics.domain.model.Payment
import com.egglytics.domain.model.PaymentType
import com.egglytics.domain.model.Reversal
import com.egglytics.domain.model.ReversalTargetCollection
import com.egglytics.domain.model.Sale
import com.egglytics.domain.model.Withdrawal
import java.time.LocalDate

data class ClosingInputs(
    val sales: List<Sale> = emptyList(),
    val payments: List<Payment> = emptyList(),
    val expenses: List<Expense> = emptyList(),
    val feedPurchases: List<FeedPurchase> = emptyList(),
    val withdrawals: List<Withdrawal> = emptyList(),
    val reversals: List<Reversal> = emptyList(),
)

fun buildClosingSummary(
    inputs: ClosingInputs,
    fecha: LocalDate,
    feedItems: Map<String, CountItem>,
    cashItems: Map<String, CountItem>,
    omittedCount: Long,
): Map<String, Long> {
    val revSales = reversedIds(inputs.reversals, ReversalTargetCollection.SALES)
    val revPayments = reversedIds(inputs.reversals, ReversalTargetCollection.PAYMENTS)
    val revExpenses = reversedIds(inputs.reversals, ReversalTargetCollection.EXPENSES)
    val revFeedPurchases = reversedIds(inputs.reversals, ReversalTargetCollection.FEED_PURCHASES)
    val revWithdrawals = reversedIds(inputs.reversals, ReversalTargetCollection.WITHDRAWALS)

    val salesTotal = inputs.sales
        .filter { it.id !in revSales && it.fechaOperativa == fecha }
        .sumOf { it.total }
    val dayPayments = inputs.payments.filter { it.id !in revPayments && it.fechaOperativa == fecha }
    val collectionsTotal = dayPayments.filter { it.type == PaymentType.PAYMENT }.sumOf { it.amount }
    val refundsTotal = dayPayments
        .filter { it.type == PaymentType.REFUND || it.type == PaymentType.OVERPAYMENT_REFUND }
        .sumOf { it.amount }
    val operatingExpensesTotal = inputs.expenses
        .filter { it.id !in revExpenses && it.fechaOperativa == fecha }
        .sumOf { it.amount }
    val feedPurchasesTotal = inputs.feedPurchases
        .filter { it.id !in revFeedPurchases && it.fechaOperativa == fecha }
        .sumOf { it.totalCost }
    val withdrawalsTotal = inputs.withdrawals
        .filter { it.id !in revWithdrawals && it.fechaOperativa == fecha }
        .sumOf { it.amount }

    return mapOf(
        "salesTotal" to salesTotal,
        "collectionsTotal" to collectionsTotal,
        "refundsTotal" to refundsTotal,
        "operatingExpensesTotal" to operatingExpensesTotal,
        "feedPurchasesTotal" to feedPurchasesTotal,
        "withdrawalsTotal" to withdrawalsTotal,
        "countedFeedItemsCount" to feedItems.size.toLong(),
        "countedCashItemsCount" to cashItems.size.toLong(),
        "omittedItemsCount" to omittedCount,
        "feedDifferenceTotalKg" to feedItems.values.sumOf { it.difference },
        "cashDifferenceTotal" to cashItems.values.sumOf { it.difference },
        "totalCashCounted" to cashItems.values.sumOf { it.physical },
    )
}

private fun reversedIds(
    reversals: List<Reversal>,
    collection: ReversalTargetCollection,
): Set<String> = reversals.asSequence().filter { it.targetCollection == collection }.map { it.targetId }.toSet()
