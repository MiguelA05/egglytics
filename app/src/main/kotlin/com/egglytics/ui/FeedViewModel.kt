package com.egglytics.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.egglytics.data.repository.FeedDepletionRequest
import com.egglytics.data.repository.FeedLossRequest
import com.egglytics.data.repository.FeedPurchaseRequest
import com.egglytics.data.repository.FeedRepository
import com.egglytics.data.repository.ObservedFeedDepletion
import com.egglytics.data.repository.ObservedFeedLoss
import com.egglytics.data.repository.ObservedFeedPurchase
import com.egglytics.data.repository.ObservedReversal
import com.egglytics.data.repository.OpeningAndCountRepository
import com.egglytics.data.repository.PaymentMethodRepository
import com.egglytics.data.repository.ReversalRepository
import com.egglytics.data.repository.SessionState
import com.egglytics.domain.model.FeedIngredient
import com.egglytics.domain.model.FeedLossType
import com.egglytics.domain.model.Harvest
import com.egglytics.domain.model.KG_PER_FEED_BAG
import com.egglytics.domain.model.MemberRole
import com.egglytics.domain.model.OpeningBalance
import com.egglytics.domain.model.OperationalDate
import com.egglytics.domain.model.PaymentMethod
import com.egglytics.domain.model.PhysicalCount
import com.egglytics.domain.model.ReversalTargetCollection
import com.egglytics.domain.usecase.FeedIngredientSummary
import com.egglytics.domain.usecase.FeedSummaryRequest
import com.egglytics.domain.usecase.GetFeedBalanceUseCase
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

enum class FeedFormMode {
    PURCHASE,
    DEPLETION,
    LOSS,
}

data class FeedFormState(
    val mode: FeedFormMode = FeedFormMode.PURCHASE,
    val ingredient: FeedIngredient = FeedIngredient.CONCENTRATE,
    val bagsInput: String = "",
    val totalCostInput: String = "",
    val selectedMethodId: String? = null,
    val supplierInput: String = "",
    val lossKgInput: String = "",
    val lossType: FeedLossType = FeedLossType.LOSS,
    val lossReasonInput: String = "",
    val dateInput: String = "",
    val noteInput: String = "",
    val periodStartInput: String = "",
    val periodEndInput: String = "",
    val formError: String? = null,
)

data class FeedUiState(
    val form: FeedFormState = FeedFormState(),
    val isAdmin: Boolean = false,
    val paymentMethods: List<PaymentMethod> = emptyList(),
    val summaries: Map<FeedIngredient, FeedIngredientSummary> = emptyMap(),
    val purchases: List<ObservedFeedPurchase> = emptyList(),
    val depletions: List<ObservedFeedDepletion> = emptyList(),
    val losses: List<ObservedFeedLoss> = emptyList(),
    val reversedReasons: Map<String, String> = emptyMap(),
    val feedError: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FeedViewModel @Inject constructor(
    private val feedRepository: FeedRepository,
    private val paymentMethodRepository: PaymentMethodRepository,
    private val openingAndCountRepository: OpeningAndCountRepository,
    private val reversalRepository: ReversalRepository,
    private val getFeedBalanceUseCase: GetFeedBalanceUseCase,
    private val clock: Clock,
) : ViewModel() {

    private val activeUid = MutableStateFlow<String?>(null)
    private val isAdminFlow = MutableStateFlow(false)
    private val formState = MutableStateFlow(initialFormState(OperationalDate.today(clock)))
    private val writeError = MutableStateFlow<String?>(null)

    private val observedPurchases = activeUid.flatMapLatest { uid ->
        if (uid == null) {
            flowOf(Pair(emptyList<ObservedFeedPurchase>(), null as String?))
        } else {
            feedRepository.observePurchases()
                .map { it to (null as String?) }
                .catch { emit(emptyList<ObservedFeedPurchase>() to (it.localizedMessage ?: "Error al consultar compras.")) }
        }
    }

    private val observedDepletions = activeUid.flatMapLatest { uid ->
        if (uid == null) {
            flowOf(Pair(emptyList<ObservedFeedDepletion>(), null as String?))
        } else {
            feedRepository.observeDepletions()
                .map { it to (null as String?) }
                .catch { emit(emptyList<ObservedFeedDepletion>() to (it.localizedMessage ?: "Error al consultar agotamientos.")) }
        }
    }

    private val observedLosses = activeUid.flatMapLatest { uid ->
        if (uid == null) {
            flowOf(Pair(emptyList<ObservedFeedLoss>(), null as String?))
        } else {
            feedRepository.observeLosses()
                .map { it to (null as String?) }
                .catch { emit(emptyList<ObservedFeedLoss>() to (it.localizedMessage ?: "Error al consultar pérdidas.")) }
        }
    }

    private val observedMethods = activeUid.flatMapLatest { uid ->
        if (uid == null) {
            flowOf(emptyList())
        } else {
            paymentMethodRepository.observeActivePaymentMethods().catch { emit(emptyList()) }
        }
    }

    private val observedOpenings = activeUid.flatMapLatest { uid ->
        if (uid == null) {
            flowOf(emptyList<OpeningBalance>())
        } else {
            openingAndCountRepository.observeOpenings().catch { emit(emptyList()) }
        }
    }

    private val observedCounts = activeUid.flatMapLatest { uid ->
        if (uid == null) {
            flowOf(emptyList<PhysicalCount>())
        } else {
            openingAndCountRepository.observeCounts().catch { emit(emptyList()) }
        }
    }

    private val observedReversals = activeUid.flatMapLatest { uid ->
        if (uid == null) {
            flowOf(emptyList<ObservedReversal>())
        } else {
            reversalRepository.observeReversals().catch { emit(emptyList()) }
        }
    }

    private val movementsBundle = combine(
        observedPurchases,
        observedDepletions,
        observedLosses,
    ) { purchases, depletions, losses -> Triple(purchases, depletions, losses) }

    private val openingsCountsReversals = combine(
        observedOpenings,
        observedCounts,
        observedReversals,
    ) { openings, counts, reversals -> Triple(openings, counts, reversals) }

    private val catalogAndReversals = combine(
        observedMethods,
        isAdminFlow,
        writeError,
    ) { methods, isAdmin, wErr -> Triple(methods, isAdmin, wErr) }

    val uiState: StateFlow<FeedUiState> = combine(
        formState,
        movementsBundle,
        openingsCountsReversals,
        catalogAndReversals,
    ) { form, (purchasesRes, depletionsRes, lossesRes), (openings, counts, reversals), (methods, isAdmin, wErr) ->
        val (purchases, pErr) = purchasesRes
        val (depletions, dErr) = depletionsRes
        val (losses, lErr) = lossesRes
        val today = OperationalDate.today(clock)
        val periodStart = runCatching { OperationalDate.parse(form.periodStartInput.trim()) }
            .getOrDefault(today.minusDays(13L))
        val periodEnd = runCatching { OperationalDate.parse(form.periodEndInput.trim()) }
            .getOrDefault(today)
        val effectiveMethodId = when {
            form.selectedMethodId != null && methods.any { it.id == form.selectedMethodId } -> form.selectedMethodId
            else -> methods.firstOrNull()?.id
        }
        val reversalModels = reversals.map { it.reversal }
        val summaries = getFeedBalanceUseCase(
            FeedSummaryRequest(
                openings = openings,
                counts = counts,
                purchases = purchases.map { it.purchase },
                depletions = depletions.map { it.depletion },
                losses = losses.map { it.loss },
                reversals = reversalModels,
                periodStart = periodStart,
                periodEnd = periodEnd,
            ),
        )
        val reversedReasons = reversalModels.associate {
            "${it.targetCollection.firestoreValue}_${it.targetId}" to it.reason
        }
        FeedUiState(
            form = form.copy(selectedMethodId = effectiveMethodId),
            isAdmin = isAdmin,
            paymentMethods = methods,
            summaries = summaries,
            purchases = purchases,
            depletions = depletions,
            losses = losses,
            reversedReasons = reversedReasons,
            feedError = listOfNotNull(wErr, pErr, dErr, lErr).firstOrNull(),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(0L),
        initialValue = FeedUiState(form = initialFormState(OperationalDate.today(clock))),
    )

    fun syncSession(session: SessionState) {
        val auth = session as? SessionState.Authenticated
        val nextUid = auth?.uid
        isAdminFlow.value = auth?.member?.role == MemberRole.ADMIN
        if (activeUid.value != nextUid) {
            activeUid.value = nextUid
            writeError.value = null
            if (nextUid == null) {
                formState.value = initialFormState(OperationalDate.today(clock))
            }
        }
    }

    fun voidFeedMovement(
        session: SessionState,
        targetCollection: ReversalTargetCollection,
        targetId: String,
        reason: String,
    ) {
        val auth = session as? SessionState.Authenticated ?: return
        reversalRepository.reverseMovement(
            targetCollection = targetCollection,
            targetId = targetId,
            businessStartDate = auth.business.startDate,
            reason = reason,
            onSuccess = { writeError.value = null },
            onError = { err -> writeError.value = err },
        )
    }

    fun onModeChange(mode: FeedFormMode) {
        formState.update { it.copy(mode = mode, formError = null) }
    }

    fun onIngredientChange(ingredient: FeedIngredient) {
        formState.update { it.copy(ingredient = ingredient, formError = null) }
    }

    fun onBagsChange(value: String) {
        formState.update { it.copy(bagsInput = value, formError = null) }
    }

    fun onTotalCostChange(value: String) {
        formState.update { it.copy(totalCostInput = value, formError = null) }
    }

    fun onMethodSelect(methodId: String) {
        formState.update { it.copy(selectedMethodId = methodId, formError = null) }
    }

    fun onSupplierChange(value: String) {
        formState.update { it.copy(supplierInput = value) }
    }

    fun onLossKgChange(value: String) {
        formState.update { it.copy(lossKgInput = value, formError = null) }
    }

    fun onLossTypeChange(type: FeedLossType) {
        formState.update { it.copy(lossType = type, formError = null) }
    }

    fun onLossReasonChange(value: String) {
        formState.update { it.copy(lossReasonInput = value, formError = null) }
    }

    fun onDateChange(value: String) {
        formState.update { it.copy(dateInput = value, formError = null) }
    }

    fun onNoteChange(value: String) {
        formState.update { it.copy(noteInput = value) }
    }

    fun onPeriodStartChange(value: String) {
        formState.update { it.copy(periodStartInput = value) }
    }

    fun onPeriodEndChange(value: String) {
        formState.update { it.copy(periodEndInput = value) }
    }

    fun submit(
        session: SessionState.Authenticated,
        docId: String = UUID.randomUUID().toString(),
    ) {
        val state = formState.value
        val parsedDate = runCatching { OperationalDate.parse(state.dateInput.trim()) }.getOrElse { ex ->
            formState.update { it.copy(formError = ex.message ?: "Fecha operativa inválida.") }
            return
        }
        val dateErr = runCatching {
            Harvest.validateOperationalDate(parsedDate, session.business.startDate)
        }.exceptionOrNull()
        if (dateErr != null) {
            formState.update { it.copy(formError = dateErr.message ?: "Fecha inválida.") }
            return
        }

        writeError.value = null
        when (state.mode) {
            FeedFormMode.PURCHASE -> submitPurchase(session, state, parsedDate, docId)
            FeedFormMode.DEPLETION -> submitDepletion(session, state, parsedDate, docId)
            FeedFormMode.LOSS -> submitLoss(session, state, parsedDate, docId)
        }
    }

    private fun submitPurchase(
        session: SessionState.Authenticated,
        state: FeedFormState,
        parsedDate: LocalDate,
        docId: String,
    ) {
        val bags = state.bagsInput.trim().toLongOrNull()
        if (bags == null || bags <= 0L) {
            formState.update { it.copy(formError = "Ingresa una cantidad válida de bultos.") }
            return
        }
        val cost = state.totalCostInput.trim().toLongOrNull()
        if (cost == null || cost <= 0L) {
            formState.update { it.copy(formError = "Ingresa un costo total mayor que 0.") }
            return
        }
        val methodId = uiState.value.form.selectedMethodId
        if (methodId.isNullOrBlank()) {
            formState.update { it.copy(formError = "Selecciona una forma de pago activa.") }
            return
        }
        feedRepository.recordPurchase(
            FeedPurchaseRequest(
                id = docId,
                fechaOperativa = parsedDate,
                businessStartDate = session.business.startDate,
                ingredient = state.ingredient,
                bags = bags,
                totalCost = cost,
                methodId = methodId,
                supplier = state.supplierInput,
                note = state.noteInput,
            ),
            onWriteError = { err -> writeError.value = err },
        )
        formState.update {
            it.copy(bagsInput = "", totalCostInput = "", supplierInput = "", noteInput = "", formError = null)
        }
    }

    private fun submitDepletion(
        session: SessionState.Authenticated,
        state: FeedFormState,
        parsedDate: LocalDate,
        docId: String,
    ) {
        val bags = state.bagsInput.trim().toLongOrNull()
        if (bags == null || bags <= 0L) {
            formState.update { it.copy(formError = "Ingresa una cantidad válida de bultos agotados.") }
            return
        }
        feedRepository.recordDepletion(
            FeedDepletionRequest(
                id = docId,
                fechaOperativa = parsedDate,
                businessStartDate = session.business.startDate,
                ingredient = state.ingredient,
                bags = bags,
                note = state.noteInput,
            ),
            onWriteError = { err -> writeError.value = err },
        )
        formState.update { it.copy(bagsInput = "", noteInput = "", formError = null) }
    }

    private fun submitLoss(
        session: SessionState.Authenticated,
        state: FeedFormState,
        parsedDate: LocalDate,
        docId: String,
    ) {
        val kg = state.lossKgInput.trim().toLongOrNull()
        if (kg == null || kg <= 0L) {
            formState.update { it.copy(formError = "Ingresa una cantidad válida en kg.") }
            return
        }
        val reason = state.lossReasonInput.trim()
        if (reason.isEmpty()) {
            formState.update { it.copy(formError = "El motivo de la pérdida es obligatorio.") }
            return
        }
        feedRepository.recordLoss(
            FeedLossRequest(
                id = docId,
                fechaOperativa = parsedDate,
                businessStartDate = session.business.startDate,
                ingredient = state.ingredient,
                kg = kg,
                type = state.lossType,
                reason = reason,
                note = state.noteInput,
            ),
            onWriteError = { err -> writeError.value = err },
        )
        formState.update { it.copy(lossKgInput = "", lossReasonInput = "", noteInput = "", formError = null) }
    }

    private fun initialFormState(today: LocalDate): FeedFormState = FeedFormState(
        dateInput = OperationalDate.format(today),
        periodStartInput = OperationalDate.format(today.minusDays(13L)),
        periodEndInput = OperationalDate.format(today),
    )

    companion object {
        fun previewKg(bagsInput: String): Long? =
            bagsInput.trim().toLongOrNull()?.takeIf { it > 0L }?.times(KG_PER_FEED_BAG)
    }
}
