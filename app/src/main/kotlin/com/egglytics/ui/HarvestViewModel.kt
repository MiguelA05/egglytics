package com.egglytics.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.egglytics.data.repository.HarvestRepository
import com.egglytics.data.repository.ObservedHarvest
import com.egglytics.data.repository.ObservedReversal
import com.egglytics.data.repository.ReversalRepository
import com.egglytics.data.repository.SessionState
import com.egglytics.domain.model.EggClassification
import com.egglytics.domain.model.Harvest
import com.egglytics.domain.model.MemberRole
import com.egglytics.domain.model.OperationalDate
import com.egglytics.domain.model.ReversalTargetCollection
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

data class HarvestFormState(
    val harvestInputs: Map<EggClassification, String> = EggClassification.entries.associateWith { "" },
    val discardedInput: String = "",
    val harvestDateInput: String = "",
    val harvestNoteInput: String = "",
    val harvestError: String? = null,
)

data class HarvestUiState(
    val form: HarvestFormState = HarvestFormState(),
    val harvests: List<ObservedHarvest> = emptyList(),
    val reversedHarvestReasons: Map<String, String> = emptyMap(),
    val voidingHarvestIds: Set<String> = emptySet(),
    val canVoid: Boolean = false,
    val harvestsError: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HarvestViewModel @Inject constructor(
    private val harvestRepository: HarvestRepository,
    private val reversalRepository: ReversalRepository,
    private val clock: Clock,
) : ViewModel() {

    private val activeUid = MutableStateFlow<String?>(null)
    private val isAdmin = MutableStateFlow(false)
    private val formState = MutableStateFlow(initialFormState(OperationalDate.today(clock)))
    private val voidingIds = MutableStateFlow<Set<String>>(emptySet())
    private val voidError = MutableStateFlow<String?>(null)

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

    val uiState: StateFlow<HarvestUiState> = combine(
        formState,
        observedHarvests,
        observedReversals,
        voidingIds,
        combine(isAdmin, voidError) { admin, err -> admin to err },
    ) { form, (items, err), (reversals, revErr), voiding, (admin, vErr) ->
        val reversedMap = reversals
            .asSequence()
            .filter { it.reversal.targetCollection == ReversalTargetCollection.HARVESTS }
            .associate { it.reversal.targetId to it.reversal.reason }
        HarvestUiState(
            form = form,
            harvests = items,
            reversedHarvestReasons = reversedMap,
            voidingHarvestIds = voiding,
            canVoid = admin,
            harvestsError = listOfNotNull(vErr, err, revErr).firstOrNull(),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(0L),
        initialValue = HarvestUiState(form = initialFormState(OperationalDate.today(clock))),
    )

    fun syncSession(session: SessionState) {
        val auth = session as? SessionState.Authenticated
        val nextUid = auth?.uid
        isAdmin.value = auth?.member?.role == MemberRole.ADMIN
        if (activeUid.value != nextUid) {
            activeUid.value = nextUid
            voidingIds.value = emptySet()
            voidError.value = null
            if (nextUid == null) {
                formState.value = initialFormState(OperationalDate.today(clock))
            }
        }
    }

    fun onHarvestQtyChange(classification: EggClassification, value: String) {
        formState.update { state ->
            state.copy(
                harvestInputs = state.harvestInputs + (classification to value),
                harvestError = null,
            )
        }
    }

    fun onDiscardedChange(value: String) {
        formState.update { it.copy(discardedInput = value, harvestError = null) }
    }

    fun onHarvestDateChange(value: String) {
        formState.update { it.copy(harvestDateInput = value, harvestError = null) }
    }

    fun onHarvestNoteChange(value: String) {
        formState.update { it.copy(harvestNoteInput = value) }
    }

    fun submitHarvest(
        session: SessionState.Authenticated,
        harvestId: String = UUID.randomUUID().toString(),
    ) {
        val state = formState.value
        val parsedDate = runCatching {
            OperationalDate.parse(state.harvestDateInput.trim())
        }.getOrElse { ex ->
            formState.update { it.copy(harvestError = ex.message ?: "Fecha operativa inválida.") }
            return
        }

        val graded = mutableMapOf<EggClassification, Long>()
        for (classification in EggClassification.entries) {
            val raw = state.harvestInputs[classification].orEmpty().trim()
            val qty = if (raw.isEmpty()) 0L else raw.toLongOrNull()
            if (qty == null || qty < 0L) {
                formState.update {
                    it.copy(harvestError = "Cantidad inválida para ${classification.name}: '$raw'")
                }
                return
            }
            if (qty > 0L) {
                graded[classification] = qty
            }
        }

        val discardedRaw = state.discardedInput.trim()
        val discarded = if (discardedRaw.isEmpty()) 0L else discardedRaw.toLongOrNull()
        if (discarded == null || discarded < 0L) {
            formState.update {
                it.copy(harvestError = "Cantidad inválida para descartados: '$discardedRaw'")
            }
            return
        }

        val validationError = runCatching {
            Harvest.validateOperationalDate(parsedDate, session.business.startDate)
            Harvest.validate(graded, discarded)
        }.exceptionOrNull()

        if (validationError != null) {
            formState.update {
                it.copy(harvestError = validationError.message ?: "Recolección inválida.")
            }
            return
        }

        harvestRepository.recordHarvest(
            harvestId = harvestId,
            fechaOperativa = parsedDate,
            graded = graded,
            discarded = discarded,
            note = state.harvestNoteInput,
            onWriteError = { err ->
                formState.update { it.copy(harvestError = err) }
            },
        )

        formState.update { current ->
            current.copy(
                harvestInputs = EggClassification.entries.associateWith { "" },
                discardedInput = "",
                harvestNoteInput = "",
                harvestError = null,
            )
        }
    }

    fun voidHarvest(
        session: SessionState.Authenticated,
        observed: ObservedHarvest,
        reason: String,
    ) {
        if (session.member.role != MemberRole.ADMIN) {
            voidError.value = "Solo un administrador puede anular recolecciones."
            return
        }
        if (observed.isPendingWrite) {
            voidError.value = "Espera a que la recolección se confirme antes de anularla."
            return
        }
        val harvestId = observed.harvest.id
        if (harvestId in uiState.value.reversedHarvestReasons || harvestId in voidingIds.value) {
            return
        }
        val trimmedReason = reason.trim()
        if (trimmedReason.isEmpty()) {
            voidError.value = "El motivo de anulación es obligatorio."
            return
        }

        voidError.value = null
        voidingIds.update { it + harvestId }
        reversalRepository.reverseHarvest(
            harvest = observed.harvest,
            businessStartDate = session.business.startDate,
            reason = trimmedReason,
            onSuccess = {
                voidingIds.update { it - harvestId }
            },
            onError = { err ->
                voidingIds.update { it - harvestId }
                voidError.value = err
            },
        )
    }

    private fun initialFormState(today: LocalDate): HarvestFormState = HarvestFormState(
        harvestDateInput = OperationalDate.format(today),
    )
}
