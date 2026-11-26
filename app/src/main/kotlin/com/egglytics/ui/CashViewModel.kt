package com.egglytics.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.egglytics.data.repository.ExpenseAndWithdrawalRepository
import com.egglytics.data.repository.FeedRepository
import com.egglytics.data.repository.ObservedExpense
import com.egglytics.data.repository.ObservedFeedDepletion
import com.egglytics.data.repository.ObservedFeedPurchase
import com.egglytics.data.repository.ObservedWithdrawal
import com.egglytics.data.repository.OpeningAndCountRepository
import com.egglytics.data.repository.PaymentMethodRepository
import com.egglytics.data.repository.ReversalRepository
import com.egglytics.data.repository.SaleRepository
import com.egglytics.data.repository.SessionState
import com.egglytics.domain.model.OperationalDate
import com.egglytics.domain.usecase.CashAndResultRequest
import com.egglytics.domain.usecase.CashAndResultSummary
import com.egglytics.domain.usecase.GetCashAndResultUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class CashUiState(
    val periodStartInput: String,
    val periodEndInput: String,
    val summary: CashAndResultSummary? = null,
    val hasPendingSync: Boolean = false,
    val error: String? = null,
    val periodError: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CashViewModel @Inject constructor(
    private val paymentMethodRepository: PaymentMethodRepository,
    private val saleRepository: SaleRepository,
    private val feedRepository: FeedRepository,
    private val expenseAndWithdrawalRepository: ExpenseAndWithdrawalRepository,
    private val openingAndCountRepository: OpeningAndCountRepository,
    private val reversalRepository: ReversalRepository,
    private val getCashAndResultUseCase: GetCashAndResultUseCase,
    private val clock: Clock,
) : ViewModel() {

    private val activeUid = MutableStateFlow<String?>(null)
    private val periodDates = MutableStateFlow(
        OperationalDate.format(OperationalDate.today(clock)) to
            OperationalDate.format(OperationalDate.today(clock)),
    )

    val uiState: StateFlow<CashUiState> = activeUid.flatMapLatest { uid ->
        val initial = CashUiState(periodDates.value.first, periodDates.value.second)
        if (uid == null) {
            flowOf(initial)
        } else {
            observeCash()
                .onStart { emit(initial) }
                .catch {
                    emit(initial.copy(error = "No se pudo cargar la caja completa. Vuelve a abrir Caja para reintentar."))
                }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(0L, replayExpirationMillis = 0L),
        initialValue = CashUiState(
            periodStartInput = OperationalDate.format(OperationalDate.today(clock)),
            periodEndInput = OperationalDate.format(OperationalDate.today(clock)),
        ),
    )

    private fun observeCash(): Flow<CashUiState> {
        val methodsFlow = paymentMethodRepository.observeAllPaymentMethods()
        val salesFlow = saleRepository.observeSales()
        val returnsFlow = expenseAndWithdrawalRepository.observeSaleReturns()
        val paymentsFlow = saleRepository.observeAllSalePayments()
        val expensesFlow = expenseAndWithdrawalRepository.observeExpenses()
        val withdrawalsFlow = expenseAndWithdrawalRepository.observeWithdrawals()
        val feedPurchasesFlow = feedRepository.observePurchases()
        val feedDepletionsFlow = feedRepository.observeDepletions()
        val openingsFlow = openingAndCountRepository.observeOpenings()
        val countsFlow = openingAndCountRepository.observeCounts()
        val reversalsFlow = reversalRepository.observeReversals()

        val openingsCountsReversals = combine(
            openingsFlow,
            countsFlow,
            reversalsFlow,
        ) { openings, counts, reversals -> Triple(openings, counts, reversals) }

        val salesAndPayments = combine(
            salesFlow,
            returnsFlow,
            paymentsFlow,
        ) { sales, returns, payments -> Triple(sales, returns, payments) }

        val outflows = combine(
            expensesFlow,
            withdrawalsFlow,
            feedPurchasesFlow,
            feedDepletionsFlow,
        ) { expenses, withdrawals, purchases, depletions ->
            OutflowsBundle(expenses, withdrawals, purchases, depletions)
        }

        return combine(
            periodDates,
            methodsFlow,
            salesAndPayments,
            outflows,
            openingsCountsReversals,
        ) { (startStr, endStr), methods, (sales, returns, paymentsBySale), out, (openings, counts, reversals) ->
            val today = OperationalDate.today(clock)
            val start = runCatching { OperationalDate.parse(startStr.trim()) }.getOrNull()
            val end = runCatching { OperationalDate.parse(endStr.trim()) }.getOrNull()
            val allPayments = paymentsBySale.values.flatten()
            val summary = getCashAndResultUseCase(
                CashAndResultRequest(
                    methods = methods,
                    openings = openings,
                    counts = counts,
                    sales = sales.map { it.sale },
                    saleReturns = returns,
                    payments = allPayments.map { it.payment },
                    expenses = out.expenses.map { it.expense },
                    feedPurchases = out.purchases.map { it.purchase },
                    feedDepletions = out.depletions.map { it.depletion },
                    withdrawals = out.withdrawals.map { it.withdrawal },
                    reversals = reversals.map { it.reversal },
                    periodStart = start ?: today,
                    periodEnd = end ?: today,
                ),
            )
            val pending = sales.any { it.isPendingWrite } ||
                allPayments.any { it.isPendingWrite } ||
                out.expenses.any { it.isPendingWrite } ||
                out.withdrawals.any { it.isPendingWrite } ||
                out.purchases.any { it.isPendingWrite } ||
                out.depletions.any { it.isPendingWrite } || reversals.any { it.isPendingWrite }
            CashUiState(
                periodStartInput = startStr,
                periodEndInput = endStr,
                summary = summary,
                hasPendingSync = pending,
                periodError = when {
                    start == null || end == null -> "Introduce ambas fechas en formato AAAA-MM-DD."
                    start > end -> "La fecha final debe ser igual o posterior a la inicial."
                    else -> null
                },
            )
        }
    }

    fun syncSession(session: SessionState) {
        val nextUid = (session as? SessionState.Authenticated)?.uid
        if (activeUid.value != nextUid) {
            activeUid.value = nextUid
        }
    }

    fun onPeriodStartChange(v: String) = periodDates.update { it.copy(first = v) }
    fun onPeriodEndChange(v: String) = periodDates.update { it.copy(second = v) }

    private data class OutflowsBundle(
        val expenses: List<ObservedExpense>,
        val withdrawals: List<ObservedWithdrawal>,
        val purchases: List<ObservedFeedPurchase>,
        val depletions: List<ObservedFeedDepletion>,
    )
}
