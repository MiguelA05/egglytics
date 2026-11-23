package com.egglytics.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.egglytics.data.repository.HarvestRepository
import com.egglytics.data.repository.ObservedHarvest
import com.egglytics.data.repository.ObservedReversal
import com.egglytics.data.repository.ObservedSale
import com.egglytics.data.repository.ReversalRepository
import com.egglytics.data.repository.SaleRepository
import com.egglytics.data.repository.SessionState
import com.egglytics.data.repository.SubmissionReceipt
import com.egglytics.data.repository.SubmissionReceiptStore
import com.egglytics.data.repository.SubmissionReconciler
import com.egglytics.data.repository.SubmissionSyncStatus
import com.egglytics.domain.model.DeliveryStatus
import com.egglytics.domain.model.OperationalDate
import com.egglytics.domain.model.Payment
import com.egglytics.domain.usecase.DailySummary
import com.egglytics.domain.usecase.GetDailySummaryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject

data class HomeUiState(
    val summary: DailySummary,
    val pendingSyncCount: Int = 0,
    val syncNotices: List<SubmissionReceipt> = emptyList(),
    val isReconciling: Boolean = false,
    val harvestsIncomplete: Boolean = false,
    val salesIncomplete: Boolean = false,
    val paymentsIncomplete: Boolean = false,
    val summaryError: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val harvestRepository: HarvestRepository,
    private val saleRepository: SaleRepository,
    private val reversalRepository: ReversalRepository,
    private val receiptStore: SubmissionReceiptStore,
    private val submissionReconciler: SubmissionReconciler,
    private val getDailySummaryUseCase: GetDailySummaryUseCase,
    private val clock: Clock,
) : ViewModel() {

    private val activeSession = MutableStateFlow<Pair<String, String>?>(null)
    private val activeUid = activeSession.map { it?.first }
    private val isReconciling = MutableStateFlow(false)
    private var reconcileJob: Job? = null

    private val observedHarvests = activeUid
        .flatMapLatest { uid ->
            if (uid == null) {
                flowOf(Pair(emptyList<ObservedHarvest>(), null as String?))
            } else {
                harvestRepository.observeHarvests()
                    .map { items -> Pair(items, null as String?) }
                    .catch { ex ->
                        emit(Pair(emptyList(), ex.localizedMessage ?: "Error al consultar recolecciones."))
                    }
            }
        }

    private val observedSales = activeUid
        .flatMapLatest { uid ->
            if (uid == null) {
                flowOf(Pair(emptyList<ObservedSale>(), null as String?))
            } else {
                saleRepository.observeSales()
                    .map { items -> Pair(items, null as String?) }
                    .catch { ex ->
                        emit(Pair(emptyList(), ex.localizedMessage ?: "Error al consultar ventas."))
                    }
            }
        }

    private val observedPayments = activeUid
        .flatMapLatest { uid ->
            if (uid == null) {
                flowOf(Pair(emptyMap<String, List<Payment>>(), null as String?))
            } else {
                saleRepository.observeSalePayments()
                    .map { items -> Pair(items, null as String?) }
                    .catch { ex ->
                        emit(Pair(emptyMap(), ex.localizedMessage ?: "Error al consultar pagos."))
                    }
            }
        }

    private val observedReversals = activeUid
        .flatMapLatest { uid ->
            if (uid == null) {
                flowOf(Pair(emptyList<ObservedReversal>(), null as String?))
            } else {
                reversalRepository.observeReversals()
                    .map { items -> Pair(items, null as String?) }
                    .catch { ex ->
                        emit(Pair(emptyList(), ex.localizedMessage ?: "Error al consultar anulaciones."))
                    }
            }
        }

    private val observedReceipts = activeSession
        .flatMapLatest { sessionPair ->
            if (sessionPair == null) {
                flowOf(emptyList())
            } else {
                receiptStore.observeReceipts(sessionPair.first, sessionPair.second)
            }
        }

    private val syncSignals = combine(
        observedReceipts,
        isReconciling,
    ) { receipts, reconciling -> receipts to reconciling }

    val uiState: StateFlow<HomeUiState> = combine(
        observedHarvests,
        observedSales,
        observedPayments,
        observedReversals,
        syncSignals,
    ) { (harvests, harvestErr), (sales, saleErr), (paymentsBySale, paymentErr), (reversals, reversalErr), (receipts, reconciling) ->
        val today = OperationalDate.today(clock)
        val activeReversals = reversals.map { it.reversal }
        val summary = getDailySummaryUseCase(
            operationalDate = today,
            harvests = harvests.map { it.harvest },
            sales = sales.map { it.sale },
            payments = paymentsBySale.values.flatten(),
            reversals = activeReversals,
        )
        val pendingHarvests = harvests.count {
            it.isPendingWrite && it.harvest.fechaOperativa == today
        }
        val pendingSales = sales.count {
            it.isPendingWrite &&
                (it.sale.fechaOperativa == today || it.sale.deliveryStatus == DeliveryStatus.PENDING)
        }
        val reversalsIncomplete = reversalErr != null
        val syncNotices = receipts.filter {
            it.status == SubmissionSyncStatus.REJECTED || it.status == SubmissionSyncStatus.UNCONFIRMED
        }
        HomeUiState(
            summary = summary,
            pendingSyncCount = pendingHarvests + pendingSales + reversals.count { it.isPendingWrite },
            syncNotices = syncNotices,
            isReconciling = reconciling,
            harvestsIncomplete = harvestErr != null || reversalsIncomplete,
            salesIncomplete = saleErr != null || reversalsIncomplete,
            paymentsIncomplete = paymentErr != null || reversalsIncomplete,
            summaryError = listOfNotNull(harvestErr, saleErr, paymentErr, reversalErr)
                .distinct()
                .joinToString(" · ")
                .ifEmpty { null },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(0L),
        initialValue = HomeUiState(
            summary = DailySummary(operationalDate = OperationalDate.today(clock)),
        ),
    )

    fun syncSession(session: SessionState) {
        val auth = session as? SessionState.Authenticated
        val nextPair = auth?.let { it.uid to it.business.id }
        if (activeSession.value != nextPair) {
            activeSession.value = nextPair
            reconcileJob?.cancel()
            if (nextPair != null) {
                triggerReconcile(nextPair.first, nextPair.second)
            }
        }
    }

    fun retryReconcile() {
        val pair = activeSession.value ?: return
        triggerReconcile(pair.first, pair.second)
    }

    fun dismissSyncNotice(submissionId: String) {
        val pair = activeSession.value ?: return
        receiptStore.remove(pair.first, pair.second, submissionId)
    }

    private fun triggerReconcile(uid: String, businessId: String) {
        reconcileJob?.cancel()
        reconcileJob = viewModelScope.launch {
            isReconciling.value = true
            try {
                submissionReconciler.reconcile(uid, businessId)
            } finally {
                isReconciling.value = false
            }
        }
    }
}
