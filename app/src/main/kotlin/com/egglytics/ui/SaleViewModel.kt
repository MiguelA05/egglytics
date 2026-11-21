package com.egglytics.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.egglytics.data.repository.ObservedSale
import com.egglytics.data.repository.PaymentMethodRepository
import com.egglytics.data.repository.SaleRepository
import com.egglytics.data.repository.SaleWithPaymentRequest
import com.egglytics.data.repository.SessionState
import com.egglytics.domain.model.EggClassification
import com.egglytics.domain.model.OperationalDate
import com.egglytics.domain.model.Payment
import com.egglytics.domain.model.PaymentMethod
import com.egglytics.domain.model.Sale
import com.egglytics.domain.model.SingleLineSaleRequest
import com.egglytics.domain.usecase.ActivePriceResult
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
    val salesError: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SaleViewModel @Inject constructor(
    private val saleRepository: SaleRepository,
    private val paymentMethodRepository: PaymentMethodRepository,
    private val clock: Clock,
) : ViewModel() {

    private val activeUid = MutableStateFlow<String?>(null)
    private val formState = MutableStateFlow(initialFormState(OperationalDate.today(clock)))
    private val deliveryError = MutableStateFlow<String?>(null)

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
                flowOf(emptyMap<String, List<Payment>>())
            } else {
                saleRepository.observeSalePayments()
                    .catch { emit(emptyMap()) }
            }
        }

    val uiState: StateFlow<SaleUiState> = combine(
        formState,
        observedSales,
        observedPaymentMethods,
        observedPayments,
        deliveryError,
    ) { form, salesRes, methodsRes, paymentsBySale, delivErr ->
        val methods = methodsRes.first
        val effectiveMethodId = when {
            form.selectedMethodId != null && methods.any { it.id == form.selectedMethodId } -> form.selectedMethodId
            else -> methods.firstOrNull()?.id
        }
        val paidAmounts = paymentsBySale.mapValues { (_, list) -> list.sumOf { it.amount } }
        SaleUiState(
            form = form.copy(selectedMethodId = effectiveMethodId),
            paymentMethods = methods,
            sales = salesRes.first,
            paidAmountBySaleId = paidAmounts,
            salesError = delivErr ?: salesRes.second ?: methodsRes.second,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(0L),
        initialValue = SaleUiState(form = initialFormState(OperationalDate.today(clock))),
    )

    fun syncSession(session: SessionState) {
        val nextUid = (session as? SessionState.Authenticated)?.uid
        if (activeUid.value != nextUid) {
            activeUid.value = nextUid
            if (nextUid == null) {
                formState.value = initialFormState(OperationalDate.today(clock))
                deliveryError.value = null
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
            onWriteSuccess = {
                formState.update { current ->
                    current.copy(
                        saleBucketsInput = "",
                        saleNoteInput = "",
                        isSubmitting = false,
                        saleError = null,
                    )
                }
            },
            onWriteError = { err ->
                formState.update { current ->
                    current.copy(
                        isSubmitting = false,
                        saleError = err,
                    )
                }
            },
        )
    }

    fun markSaleDelivered(saleId: String) {
        deliveryError.value = null
        saleRepository.markDelivered(
            saleId = saleId,
            onWriteError = { err ->
                deliveryError.value = err
            },
        )
    }

    private fun initialFormState(today: LocalDate): SaleFormState = SaleFormState(
        saleDateInput = OperationalDate.format(today),
    )
}
