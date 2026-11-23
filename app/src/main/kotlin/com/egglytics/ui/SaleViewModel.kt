package com.egglytics.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.egglytics.data.repository.ObservedReversal
import com.egglytics.data.repository.ObservedSale
import com.egglytics.data.repository.ObservedSalePayment
import com.egglytics.data.repository.PaymentMethodRepository
import com.egglytics.data.repository.ReversalRepository
import com.egglytics.data.repository.SaleRepository
import com.egglytics.data.repository.SaleWithPaymentRequest
import com.egglytics.data.repository.SessionState
import com.egglytics.domain.model.EggClassification
import com.egglytics.domain.model.MemberRole
import com.egglytics.domain.model.OperationalDate
import com.egglytics.domain.model.Payment
import com.egglytics.domain.model.PaymentMethod
import com.egglytics.domain.model.PaymentType
import com.egglytics.domain.model.ReversalTargetCollection
import com.egglytics.domain.model.Sale
import com.egglytics.domain.model.SingleLineSaleRequest
import com.egglytics.domain.usecase.ActivePriceResult
import com.egglytics.domain.usecase.SaleReversalEligibility
import com.egglytics.domain.usecase.SaleReversalValidationRequest
import com.egglytics.domain.usecase.ValidateSaleReversalUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

data class SaleFormState(
    val saleClassification: EggClassification = EggClassification.AA,
    val selectedMethodId: String? = null,
    val saleBucketsInput: String = "",
    val saleDateInput: String = "",
    val saleNoteInput: String = "",
    val isSubmitting: Boolean = false,
    val saleError: String? = null,
)

data class SaleUiState(
    val form: SaleFormState = SaleFormState(),
    val paymentMethods: List<PaymentMethod> = emptyList(),
    val sales: List<ObservedSale> = emptyList(),
    val paidAmountBySaleId: Map<String, Long> = emptyMap(),
    val reversedSaleReasons: Map<String, String> = emptyMap(),
    val saleReversalEligibility: Map<String, SaleReversalEligibility> = emptyMap(),
    val voidingSaleIds: Set<String> = emptySet(),
    val canVoid: Boolean = false,
    val salesError: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SaleViewModel @Inject constructor(
    private val saleRepository: SaleRepository,
    private val paymentMethodRepository: PaymentMethodRepository,
    private val reversalRepository: ReversalRepository,
    private val validateSaleReversalUseCase: ValidateSaleReversalUseCase,
    private val clock: Clock,
) : ViewModel() {

    private val activeUid = MutableStateFlow<String?>(null)
    private val isAdmin = MutableStateFlow(false)
    private val formState = MutableStateFlow(initialFormState(OperationalDate.today(clock)))
    private val pendingLocalSaleId = MutableStateFlow<String?>(null)
    private val remoteBatchError = MutableStateFlow<String?>(null)
    private val voidingSaleIds = MutableStateFlow<Set<String>>(emptySet())

    private val observedSales = activeUid
        .flatMapLatest { uid ->
            if (uid == null) {
                flowOf(Pair(emptyList<ObservedSale>(), null as String?))
            } else {
                saleRepository.observeSales()
                    .onEach { items ->
                        val awaitingId = pendingLocalSaleId.value
                        if (awaitingId != null && items.any { it.sale.id == awaitingId }) {
                            pendingLocalSaleId.value = null
                            formState.update { current ->
                                current.copy(
                                    saleBucketsInput = "",
                                    saleNoteInput = "",
                                    isSubmitting = false,
                                    saleError = null,
                                )
                            }
                        }
                    }
                    .map { items -> Pair(items, null as String?) }
                    .catch { ex ->
                        emit(Pair(emptyList(), ex.localizedMessage ?: "Error al consultar ventas."))
                    }
            }
        }

    private val observedPaymentMethods = activeUid
        .flatMapLatest { uid ->
            if (uid == null) {
                flowOf(Pair(emptyList<PaymentMethod>(), null as String?))
            } else {
                paymentMethodRepository.observeActivePaymentMethods()
                    .map { items -> Pair(items, null as String?) }
                    .catch { ex ->
                        emit(Pair(emptyList(), ex.localizedMessage ?: "Error al consultar formas de pago."))
                    }
            }
        }

    private val observedPayments = activeUid
        .flatMapLatest { uid ->
            if (uid == null) {
                flowOf(emptyMap<String, List<ObservedSalePayment>>())
            } else {
                saleRepository.observeAllSalePayments()
                    .catch { emit(emptyMap()) }
            }
        }

    private val observedSaleReturnIds = activeUid
        .flatMapLatest { uid ->
            if (uid == null) {
                flowOf(emptyMap<String, List<String>>())
            } else {
                saleRepository.observeSaleReturnIdsBySaleId()
                    .catch { emit(emptyMap()) }
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

    private val reversalSignals = combine(
        observedReversals,
        observedSaleReturnIds,
        voidingSaleIds,
        isAdmin,
    ) { revRes, returnIdsBySale, voiding, admin ->
        ReversalSignals(revRes, returnIdsBySale, voiding, admin)
    }

    val uiState: StateFlow<SaleUiState> = combine(
        formState,
        observedSales,
        observedPaymentMethods,
        observedPayments,
        combine(remoteBatchError, reversalSignals) { err, sig -> err to sig },
    ) { form, salesRes, methodsRes, paymentsBySale, (remoteErr, signals) ->
        val methods = methodsRes.first
        val effectiveMethodId = when {
            form.selectedMethodId != null && methods.any { it.id == form.selectedMethodId } -> form.selectedMethodId
            else -> methods.firstOrNull()?.id
        }
        val reversals = signals.reversalsResult.first.map { it.reversal }
        val reversedPaymentIds = reversals
            .asSequence()
            .filter { it.targetCollection == ReversalTargetCollection.PAYMENTS }
            .map { it.targetId }
            .toSet()
        val reversedSaleReturnIds = reversals
            .asSequence()
            .filter { it.targetCollection == ReversalTargetCollection.SALE_RETURNS }
            .map { it.targetId }
            .toSet()
        val reversedSaleReasons = reversals
            .asSequence()
            .filter { it.targetCollection == ReversalTargetCollection.SALES }
            .associate { it.targetId to it.reason }

        val paidAmounts = paymentsBySale.mapValues { (_, list) ->
            list.asSequence()
                .map { it.payment }
                .filter { it.id !in reversedPaymentIds && it.type == PaymentType.PAYMENT }
                .sumOf { it.amount }
        }
        val eligibilityMap = salesRes.first.associate { observed ->
            val saleId = observed.sale.id
            val salePayments = paymentsBySale[saleId].orEmpty()
            val pendingPaymentIds = salePayments.filter { it.isPendingWrite }.map { it.payment.id }.toSet()
            val hasActiveReturns = signals.returnIdsBySale[saleId]
                .orEmpty()
                .any { it !in reversedSaleReturnIds }
            saleId to validateSaleReversalUseCase(
                SaleReversalValidationRequest(
                    sale = observed.sale,
                    saleIsPendingWrite = observed.isPendingWrite,
                    salePayments = salePayments.map { it.payment },
                    pendingPaymentIds = pendingPaymentIds,
                    hasActiveReturns = hasActiveReturns,
                    reversals = reversals,
                ),
            )
        }
        SaleUiState(
            form = form.copy(selectedMethodId = effectiveMethodId),
            paymentMethods = methods,
            sales = salesRes.first,
            paidAmountBySaleId = paidAmounts,
            reversedSaleReasons = reversedSaleReasons,
            saleReversalEligibility = eligibilityMap,
            voidingSaleIds = signals.voidingIds,
            canVoid = signals.isAdmin,
            salesError = remoteErr ?: salesRes.second ?: methodsRes.second ?: signals.reversalsResult.second,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(0L),
        initialValue = SaleUiState(form = initialFormState(OperationalDate.today(clock))),
    )

    fun syncSession(session: SessionState) {
        val auth = session as? SessionState.Authenticated
        val nextUid = auth?.uid
        isAdmin.value = auth?.member?.role == MemberRole.ADMIN
        if (activeUid.value != nextUid) {
            activeUid.value = nextUid
            voidingSaleIds.value = emptySet()
            if (nextUid == null) {
                pendingLocalSaleId.value = null
                formState.value = initialFormState(OperationalDate.today(clock))
                remoteBatchError.value = null
            }
        }
    }

    fun onSaleClassificationChange(classification: EggClassification) {
        formState.update { it.copy(saleClassification = classification, saleError = null) }
    }

    fun onPaymentMethodSelect(methodId: String) {
        formState.update { it.copy(selectedMethodId = methodId, saleError = null) }
    }

    fun onSaleBucketsChange(value: String) {
        formState.update { it.copy(saleBucketsInput = value, saleError = null) }
    }

    fun onSaleDateChange(value: String) {
        formState.update { it.copy(saleDateInput = value, saleError = null) }
    }

    fun onSaleNoteChange(value: String) {
        formState.update { it.copy(saleNoteInput = value) }
    }

    fun submitSale(
        session: SessionState.Authenticated,
        activePrices: Map<EggClassification, ActivePriceResult>,
        saleId: String = UUID.randomUUID().toString(),
        paymentId: String = UUID.randomUUID().toString(),
    ) {
        val state = formState.value
        val currentUi = uiState.value
        if (state.isSubmitting) return

        val parsedDate = runCatching {
            OperationalDate.parse(state.saleDateInput.trim())
        }.getOrElse { ex ->
            formState.update { it.copy(saleError = ex.message ?: "Fecha operativa inválida.") }
            return
        }

        val bucketsRaw = state.saleBucketsInput.trim()
        val buckets = bucketsRaw.toLongOrNull()
        if (buckets == null || buckets <= 0L) {
            formState.update { it.copy(saleError = "Ingresa una cantidad válida de cubetas (> 0).") }
            return
        }

        val activePriceResult = activePrices[state.saleClassification]
        if (activePriceResult !is ActivePriceResult.Found) {
            formState.update {
                it.copy(saleError = "No hay precio vigente para ${state.saleClassification.name}.")
            }
            return
        }

        val methodId = state.selectedMethodId ?: currentUi.paymentMethods.firstOrNull()?.id
        if (methodId.isNullOrBlank() || currentUi.paymentMethods.none { it.id == methodId }) {
            formState.update { it.copy(saleError = "Selecciona una forma de pago activa.") }
            return
        }

        val validationError = runCatching {
            val previewSale = Sale.createSingleLinePending(
                SingleLineSaleRequest(
                    id = saleId,
                    fechaOperativa = parsedDate,
                    businessStartDate = session.business.startDate,
                    createdAt = clock.instant(),
                    createdBy = session.uid,
                    classification = state.saleClassification,
                    buckets = buckets,
                    activePrice = activePriceResult.price,
                    note = state.saleNoteInput,
                ),
            )
            Payment.forFullSalePayment(
                id = paymentId,
                sale = previewSale,
                methodId = methodId,
            )
        }.exceptionOrNull()

        if (validationError != null) {
            formState.update { it.copy(saleError = validationError.message ?: "Venta inválida.") }
            return
        }

        pendingLocalSaleId.value = saleId
        remoteBatchError.value = null
        formState.update { it.copy(isSubmitting = true, saleError = null) }

        saleRepository.createPendingSaleWithFullPayment(
            request = SaleWithPaymentRequest(
                saleId = saleId,
                paymentId = paymentId,
                fechaOperativa = parsedDate,
                businessStartDate = session.business.startDate,
                classification = state.saleClassification,
                buckets = buckets,
                activePrice = activePriceResult.price,
                methodId = methodId,
                note = state.saleNoteInput,
            ),
            onWriteError = { err ->
                if (pendingLocalSaleId.value == saleId) {
                    pendingLocalSaleId.value = null
                    formState.update { it.copy(isSubmitting = false) }
                }
                remoteBatchError.value = err
            },
        )
    }

    fun markSaleDelivered(saleId: String) {
        remoteBatchError.value = null
        saleRepository.markDelivered(
            saleId = saleId,
            onWriteError = { err ->
                remoteBatchError.value = err
            },
        )
    }

    fun voidSale(
        session: SessionState.Authenticated,
        observed: ObservedSale,
        reason: String,
    ) {
        if (session.member.role != MemberRole.ADMIN) {
            remoteBatchError.value = "Solo un administrador puede anular ventas."
            return
        }
        val saleId = observed.sale.id
        if (saleId in uiState.value.reversedSaleReasons || saleId in voidingSaleIds.value) {
            return
        }
        val eligibility = uiState.value.saleReversalEligibility[saleId]
        if (eligibility !is SaleReversalEligibility.Allowed) {
            remoteBatchError.value = (eligibility as? SaleReversalEligibility.Blocked)?.reason
                ?: "No se puede anular esta venta."
            return
        }
        val trimmedReason = reason.trim()
        if (trimmedReason.isEmpty()) {
            remoteBatchError.value = "El motivo de anulación es obligatorio."
            return
        }

        remoteBatchError.value = null
        voidingSaleIds.update { it + saleId }
        reversalRepository.reverseSaleWithPayment(
            sale = observed.sale,
            payment = eligibility.payment,
            businessStartDate = session.business.startDate,
            reason = trimmedReason,
            onSuccess = {
                voidingSaleIds.update { it - saleId }
            },
            onError = { err ->
                voidingSaleIds.update { it - saleId }
                remoteBatchError.value = err
            },
        )
    }

    private data class ReversalSignals(
        val reversalsResult: Pair<List<ObservedReversal>, String?>,
        val returnIdsBySale: Map<String, List<String>>,
        val voidingIds: Set<String>,
        val isAdmin: Boolean,
    )

    private fun initialFormState(today: LocalDate): SaleFormState = SaleFormState(
        saleDateInput = OperationalDate.format(today),
    )
}
