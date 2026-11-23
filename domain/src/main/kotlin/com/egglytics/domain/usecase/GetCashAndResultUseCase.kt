package com.egglytics.domain.usecase

import com.egglytics.domain.model.Expense
import com.egglytics.domain.model.FeedDepletion
import com.egglytics.domain.model.FeedIngredient
import com.egglytics.domain.model.FeedPurchase
import com.egglytics.domain.model.Payment
import com.egglytics.domain.model.PaymentMethod
import com.egglytics.domain.model.PaymentType
import com.egglytics.domain.model.Reversal
import com.egglytics.domain.model.ReversalTargetCollection
import com.egglytics.domain.model.Sale
import com.egglytics.domain.model.SaleReturnSummary
import com.egglytics.domain.model.Withdrawal
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

data class CashMethodBalance(
    val method: PaymentMethod,
    val baseAmount: Long,
    val hasBase: Boolean,
    val collections: Long,
    val refunds: Long,
    val expenses: Long,
    val feedPurchases: Long,
    val withdrawals: Long,
) {
    val balance: Long
        get() = baseAmount + collections - refunds - expenses - feedPurchases - withdrawals
}

data class ApproximateResultSummary(
    val netSales: Long,
    val operatingExpenses: Long,
    val feedPurchaseOutflows: Long,
    val estimatedFeedCost: Long?,
    val approximateResult: Long?,
)

data class CashAndResultSummary(
    val balancesByMethod: List<CashMethodBalance>,
    val totalCashBalance: Long,
    val resultSummary: ApproximateResultSummary,
)

data class CashAndResultRequest(
    val methods: List<PaymentMethod>,
    val baseByMethod: Map<String, Long> = emptyMap(),
    val sales: List<Sale> = emptyList(),
    val saleReturns: List<SaleReturnSummary> = emptyList(),
    val payments: List<Payment> = emptyList(),
    val expenses: List<Expense> = emptyList(),
    val feedPurchases: List<FeedPurchase> = emptyList(),
    val feedDepletions: List<FeedDepletion> = emptyList(),
    val withdrawals: List<Withdrawal> = emptyList(),
    val reversals: List<Reversal> = emptyList(),
    val periodStart: LocalDate,
    val periodEnd: LocalDate,
)

class GetCashAndResultUseCase {

    operator fun invoke(request: CashAndResultRequest): CashAndResultSummary {
        val reversed = ReversalLookup(request.reversals)
        val activePayments = request.payments.filter { it.id !in reversed.payments }
        val activeExpenses = request.expenses.filter { it.id !in reversed.expenses }
        val activeFeedPurchases = request.feedPurchases.filter { it.id !in reversed.feedPurchases }
        val activeWithdrawals = request.withdrawals.filter { it.id !in reversed.withdrawals }

        val inputs = ActiveCashInputs(
            sales = request.sales.filter { it.id !in reversed.sales },
            returns = request.saleReturns.filter { it.id !in reversed.saleReturns },
            payments = activePayments,
            expenses = activeExpenses,
            feedPurchases = activeFeedPurchases,
            feedDepletions = request.feedDepletions.filter { it.id !in reversed.feedDepletions },
            withdrawals = activeWithdrawals,
        )

        val methodBalances = request.methods.map { method ->
            computeMethodBalance(method, request.baseByMethod, inputs)
        }
        val resultSummary = computeResultSummary(request, inputs)

        return CashAndResultSummary(
            balancesByMethod = methodBalances,
            totalCashBalance = methodBalances.sumOf { it.balance },
            resultSummary = resultSummary,
        )
    }

    private fun computeMethodBalance(
        method: PaymentMethod,
        baseByMethod: Map<String, Long>,
        inputs: ActiveCashInputs,
    ): CashMethodBalance {
        val methodPayments = inputs.payments.filter { it.methodId == method.id }
        val collections = methodPayments
            .asSequence()
            .filter { it.type == PaymentType.PAYMENT }
            .sumOf { it.amount }
        val refunds = methodPayments
            .asSequence()
            .filter { it.type == PaymentType.REFUND || it.type == PaymentType.OVERPAYMENT_REFUND }
            .sumOf { it.amount }
        val expenseTotal = inputs.expenses.asSequence().filter { it.methodId == method.id }.sumOf { it.amount }
        val feedTotal = inputs.feedPurchases.asSequence().filter { it.methodId == method.id }.sumOf { it.totalCost }
        val withdrawalTotal = inputs.withdrawals.asSequence().filter { it.methodId == method.id }.sumOf { it.amount }

        return CashMethodBalance(
            method = method,
            baseAmount = baseByMethod[method.id] ?: 0L,
            hasBase = method.id in baseByMethod,
            collections = collections,
            refunds = refunds,
            expenses = expenseTotal,
            feedPurchases = feedTotal,
            withdrawals = withdrawalTotal,
        )
    }

    private fun computeResultSummary(
        request: CashAndResultRequest,
        inputs: ActiveCashInputs,
    ): ApproximateResultSummary {
        val grossSales = inputs.sales
            .asSequence()
            .filter { inPeriod(it.fechaOperativa, request.periodStart, request.periodEnd) }
            .sumOf { it.total }
        val returnCredits = inputs.returns
            .asSequence()
            .filter { inPeriod(it.fechaOperativa, request.periodStart, request.periodEnd) }
            .sumOf { it.creditAmount }
        val netSales = grossSales - returnCredits

        val operatingExpenses = inputs.expenses
            .asSequence()
            .filter { inPeriod(it.fechaOperativa, request.periodStart, request.periodEnd) }
            .sumOf { it.amount }
        val feedOutflows = inputs.feedPurchases
            .asSequence()
            .filter { inPeriod(it.fechaOperativa, request.periodStart, request.periodEnd) }
            .sumOf { it.totalCost }

        val estimatedFeedCost = computeEstimatedFeedCost(
            activeFeedPurchases = inputs.feedPurchases,
            activeDepletions = inputs.feedDepletions,
            periodStart = request.periodStart,
            periodEnd = request.periodEnd,
        )
        val approximateResult = estimatedFeedCost?.let { netSales - operatingExpenses - it }

        return ApproximateResultSummary(
            netSales = netSales,
            operatingExpenses = operatingExpenses,
            feedPurchaseOutflows = feedOutflows,
            estimatedFeedCost = estimatedFeedCost,
            approximateResult = approximateResult,
        )
    }

    private fun computeEstimatedFeedCost(
        activeFeedPurchases: List<FeedPurchase>,
        activeDepletions: List<FeedDepletion>,
        periodStart: LocalDate,
        periodEnd: LocalDate,
    ): Long? {
        val windowStart = periodEnd.minusDays(LOOKBACK_DAYS_INCLUSIVE_OFFSET)
        var totalCost = 0L
        var missingCost = false

        for (ingredient in FeedIngredient.entries) {
            val depletedKg = activeDepletions
                .asSequence()
                .filter { it.ingredient == ingredient && inPeriod(it.fechaOperativa, periodStart, periodEnd) }
                .sumOf { it.kg }
            if (depletedKg <= 0L) continue

            val costPerKg = resolveCostPerKg(
                purchases = activeFeedPurchases.filter { it.ingredient == ingredient },
                windowStart = windowStart,
                periodEnd = periodEnd,
            )
            if (costPerKg == null) {
                missingCost = true
            } else {
                totalCost += BigDecimal.valueOf(depletedKg)
                    .multiply(costPerKg)
                    .setScale(0, RoundingMode.HALF_UP)
                    .toLong()
            }
        }
        return if (missingCost) null else totalCost
    }

    private fun resolveCostPerKg(
        purchases: List<FeedPurchase>,
        windowStart: LocalDate,
        periodEnd: LocalDate,
    ): BigDecimal? {
        val windowPurchases = purchases.filter {
            !it.fechaOperativa.isBefore(windowStart) && !it.fechaOperativa.isAfter(periodEnd)
        }
        val windowCost = if (windowPurchases.isNotEmpty()) {
            val paid = windowPurchases.sumOf { it.totalCost }
            val kg = windowPurchases.sumOf { it.totalKg }
            if (kg > 0L) {
                BigDecimal.valueOf(paid).divide(BigDecimal.valueOf(kg), COST_SCALE, RoundingMode.HALF_UP)
            } else {
                null
            }
        } else {
            null
        }
        val fallback = purchases
            .filter { !it.fechaOperativa.isAfter(periodEnd) }
            .maxWithOrNull(compareBy<FeedPurchase> { it.fechaOperativa }.thenBy { it.createdAt }.thenBy { it.id })
        return windowCost ?: fallback?.let {
            BigDecimal.valueOf(it.totalCost).divide(BigDecimal.valueOf(it.totalKg), COST_SCALE, RoundingMode.HALF_UP)
        }
    }

    private fun inPeriod(date: LocalDate, start: LocalDate, end: LocalDate): Boolean =
        !date.isBefore(start) && !date.isAfter(end)

    private data class ActiveCashInputs(
        val sales: List<Sale>,
        val returns: List<SaleReturnSummary>,
        val payments: List<Payment>,
        val expenses: List<Expense>,
        val feedPurchases: List<FeedPurchase>,
        val feedDepletions: List<FeedDepletion>,
        val withdrawals: List<Withdrawal>,
    )

    private class ReversalLookup(reversals: List<Reversal>) {
        val sales = idsFor(reversals, ReversalTargetCollection.SALES)
        val saleReturns = idsFor(reversals, ReversalTargetCollection.SALE_RETURNS)
        val payments = idsFor(reversals, ReversalTargetCollection.PAYMENTS)
        val expenses = idsFor(reversals, ReversalTargetCollection.EXPENSES)
        val feedPurchases = idsFor(reversals, ReversalTargetCollection.FEED_PURCHASES)
        val feedDepletions = idsFor(reversals, ReversalTargetCollection.FEED_DEPLETIONS)
        val withdrawals = idsFor(reversals, ReversalTargetCollection.WITHDRAWALS)

        private fun idsFor(reversals: List<Reversal>, coll: ReversalTargetCollection): Set<String> =
            reversals.asSequence().filter { it.targetCollection == coll }.map { it.targetId }.toSet()
    }

    private companion object {
        const val LOOKBACK_DAYS_INCLUSIVE_OFFSET = 89L
        const val COST_SCALE = 6
    }
}
