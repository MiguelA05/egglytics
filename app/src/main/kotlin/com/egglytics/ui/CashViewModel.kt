package com.egglytics.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.egglytics.data.repository.ExpenseAndWithdrawalRepository
import com.egglytics.data.repository.FeedRepository
import com.egglytics.data.repository.ObservedExpense
import com.egglytics.data.repository.ObservedFeedDepletion
import com.egglytics.data.repository.ObservedFeedPurchase
import com.egglytics.data.repository.ObservedReversal
import com.egglytics.data.repository.ObservedSale
import com.egglytics.data.repository.ObservedSalePayment
import com.egglytics.data.repository.ObservedWithdrawal
import com.egglytics.data.repository.OpeningAndCountRepository
import com.egglytics.data.repository.PaymentMethodRepository
import com.egglytics.data.repository.ReversalRepository
import com.egglytics.data.repository.SaleRepository
import com.egglytics.data.repository.SessionState
import com.egglytics.domain.model.OpeningBalance
import com.egglytics.domain.model.OperationalDate
import com.egglytics.domain.model.PaymentMethod
import com.egglytics.domain.model.PhysicalCount
import com.egglytics.domain.model.SaleReturnSummary
import com.egglytics.domain.usecase.CashAndResultRequest
import com.egglytics.domain.usecase.CashAndResultSummary
import com.egglytics.domain.usecase.GetCashAndResultUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.Clock
import javax.inject.Inject

data class CashUiState(
    val periodStartInput: String,
    val periodEndInput: String,
    val summary: CashAndResultSummary? = null,
    val hasPendingSync: Boolean = false,
    val error: String? = null,
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

    private val methodsFlow = activeUid.flatMapLatest { uid ->
        if (uid == null) flowOf(emptyList<PaymentMethod>())
        else paymentMethodRepository.observeAllPaymentMethods().catch { emit(emptyList()) }
    }
    private val salesFlow = activeUid.flatMapLatest { uid ->
        if (uid == null) flowOf(emptyList<ObservedSale>())
        else saleRepository.observeSales().catch { emit(emptyList()) }
    }
    private val returnsFlow = activeUid.flatMapLatest { uid ->
        if (uid == null) flowOf(emptyList<SaleReturnSummary>())
        else expenseAndWithdrawalRepository.observeSaleReturns().catch { emit(emptyList()) }
    }
    private val paymentsFlow = activeUid.flatMapLatest { uid ->
        if (uid == null) flowOf(emptyMap<String, List<ObservedSalePayment>>())
        else saleRepository.observeAllSalePayments().catch { emit(emptyMap()) }
    }
    private val expensesFlow = activeUid.flatMapLatest { uid ->
        if (uid == null) flowOf(emptyList<ObservedExpense>())
        else expenseAndWithdrawalRepository.observeExpenses().catch { emit(emptyList()) }
    }
    private val withdrawalsFlow = activeUid.flatMapLatest { uid ->
        if (uid == null) flowOf(emptyList<ObservedWithdrawal>())
        else expenseAndWithdrawalRepository.observeWithdrawals().catch { emit(emptyList()) }
    }
    private val feedPurchasesFlow = activeUid.flatMapLatest { uid ->
        if (uid == null) flowOf(emptyList<ObservedFeedPurchase>())
        else feedRepository.observePurchases().catch { emit(emptyList()) }
    }
    private val feedDepletionsFlow = activeUid.flatMapLatest { uid ->
        if (uid == null) flowOf(emptyList<ObservedFeedDepletion>())
        else feedRepository.observeDepletions().catch { emit(emptyList()) }
    }
    private val openingsFlow = activeUid.flatMapLatest { uid ->
        if (uid == null) flowOf(emptyList<OpeningBalance>())
        else openingAndCountRepository.observeOpenings().catch { emit(emptyList()) }
    }
    private val countsFlow = activeUid.flatMapLatest { uid ->
        if (uid == null) flowOf(emptyList<PhysicalCount>())
        else openingAndCountRepository.observeCounts().catch { emit(emptyList()) }
    }
    private val reversalsFlow = activeUid.flatMapLatest { uid ->
        if (uid == null) flowOf(emptyList<ObservedReversal>())
        else reversalRepository.observeReversals().catch { emit(emptyList()) }
    }

    private val openingsCountsReversals = combine(
        openingsFlow,
        countsFlow,
        reversalsFlow,
    ) { openings, counts, reversals -> Triple(openings, counts, reversals) }

    private val salesAndPayments = combine(
        salesFlow,
        returnsFlow,
        paymentsFlow,
    ) { sales, returns, payments -> Triple(sales, returns, payments) }

    private val outflows = combine(
        expensesFlow,
        withdrawalsFlow,
        feedPurchasesFlow,
        feedDepletionsFlow,
    ) { expenses, withdrawals, purchases, depletions ->
        OutflowsBundle(expenses, withdrawals, purchases, depletions)
    }

    val uiState: StateFlow<CashUiState> = combine(
        periodDates,
        methodsFlow,
        salesAndPayments,
        outflows,
        openingsCountsReversals,
    ) { (startStr, endStr), methods, (sales, returns, paymentsBySale), out, (openings, counts, reversals) ->
        val today = OperationalDate.today(clock)
        val start = runCatching { OperationalDate.parse(startStr.trim()) }.getOrDefault(today)
        val end = runCatching { OperationalDate.parse(endStr.trim()) }.getOrDefault(today)
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
                periodStart = start,
                periodEnd = end,
            ),
        )
        val pending = sales.any { it.isPendingWrite } ||
            allPayments.any { it.isPendingWrite } ||
            out.expenses.any { it.isPendingWrite } ||
            out.withdrawals.any { it.isPendingWrite } ||
            out.purchases.any { it.isPendingWrite }
        CashUiState(
            periodStartInput = startStr,
            periodEndInput = endStr,
            summary = summary,
            hasPendingSync = pending,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(0L),
        initialValue = CashUiState(
            periodStartInput = OperationalDate.format(OperationalDate.today(clock)),
            periodEndInput = OperationalDate.format(OperationalDate.today(clock)),
        ),
    )

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
