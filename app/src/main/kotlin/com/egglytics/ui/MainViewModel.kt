package com.egglytics.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.egglytics.data.repository.HarvestRepository
import com.egglytics.data.repository.ObservedHarvest
import com.egglytics.data.repository.PriceRepository
import com.egglytics.data.repository.SessionRepository
import com.egglytics.data.repository.SessionState
import com.egglytics.domain.model.EggClassification
import com.egglytics.domain.model.Harvest
import com.egglytics.domain.model.OperationalDate
import com.egglytics.domain.usecase.ActivePriceResult
import com.egglytics.domain.usecase.GetActivePriceUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

data class MainUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val sessionState: SessionState = SessionState.Unauthenticated,
    val operationalDate: LocalDate = LocalDate.now(OperationalDate.BOGOTA_ZONE),
    val activePrices: Map<EggClassification, ActivePriceResult> = emptyMap(),
    val pricesError: String? = null,
    val harvestInputs: Map<EggClassification, String> = EggClassification.entries.associateWith { "" },
    val discardedInput: String = "",
    val harvestDateInput: String = "",
    val harvestNoteInput: String = "",
    val harvestError: String? = null,
    val harvests: List<ObservedHarvest> = emptyList(),
    val harvestsError: String? = null,
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val priceRepository: PriceRepository,
    private val harvestRepository: HarvestRepository,
    private val getActivePriceUseCase: GetActivePriceUseCase,
    private val clock: Clock,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        initialState(OperationalDate.today(clock)),
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var pricesJob: Job? = null
    private var harvestsJob: Job? = null

    init {
        restoreSession()
    }

    fun onEmailChange(value: String) {
        _uiState.update { it.copy(email = value) }
    }

    fun onPasswordChange(value: String) {
        _uiState.update { it.copy(password = value) }
    }

    fun onHarvestQtyChange(classification: EggClassification, value: String) {
        _uiState.update { state ->
            state.copy(
                harvestInputs = state.harvestInputs + (classification to value),
                harvestError = null,
            )
        }
    }

    fun onDiscardedChange(value: String) {
        _uiState.update { it.copy(discardedInput = value, harvestError = null) }
    }

    fun onHarvestDateChange(value: String) {
        _uiState.update { it.copy(harvestDateInput = value, harvestError = null) }
    }

    fun onHarvestNoteChange(value: String) {
        _uiState.update { it.copy(harvestNoteInput = value) }
    }

    fun submitHarvest(harvestId: String = UUID.randomUUID().toString()) {
        val state = _uiState.value
        val session = state.sessionState as? SessionState.Authenticated ?: return

        val parsedDate = runCatching {
            OperationalDate.parse(state.harvestDateInput.trim())
        }.getOrElse { ex ->
            _uiState.update { it.copy(harvestError = ex.message ?: "Fecha operativa inválida.") }
            return
        }

        val graded = mutableMapOf<EggClassification, Long>()
        for (classification in EggClassification.entries) {
            val raw = state.harvestInputs[classification].orEmpty().trim()
            val qty = if (raw.isEmpty()) 0L else raw.toLongOrNull()
            if (qty == null || qty < 0L) {
                _uiState.update {
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
            _uiState.update {
                it.copy(harvestError = "Cantidad inválida para descartados: '$discardedRaw'")
            }
            return
        }

        val validationError = runCatching {
            Harvest.validateOperationalDate(parsedDate, session.business.startDate)
            Harvest.validate(graded, discarded)
        }.exceptionOrNull()

        if (validationError != null) {
            _uiState.update {
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
                _uiState.update { it.copy(harvestError = err) }
            },
        )

        _uiState.update { current ->
            current.copy(
                harvestInputs = EggClassification.entries.associateWith { "" },
                discardedInput = "",
                harvestNoteInput = "",
                harvestError = null,
            )
        }
    }

    fun signIn() {
        val email = _uiState.value.email.trim()
        val password = _uiState.value.password
        if (email.isEmpty() || password.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, pricesError = null, harvestsError = null) }
            val result = sessionRepository.signIn(email, password)
            _uiState.update { it.copy(isLoading = false, sessionState = result) }
            if (result is SessionState.Authenticated) {
                startObservingData()
            } else {
                stopObservingData()
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            stopObservingData()
            sessionRepository.signOut()
            _uiState.update { initialState(OperationalDate.today(clock)) }
        }
    }

    private fun restoreSession() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = sessionRepository.loadCurrentSession()
            _uiState.update { it.copy(isLoading = false, sessionState = result) }
            if (result is SessionState.Authenticated) {
                startObservingData()
            }
        }
    }

    private fun startObservingData() {
        startObservingPrices()
        startObservingHarvests()
    }

    private fun startObservingPrices() {
        pricesJob?.cancel()
        val today = OperationalDate.today(clock)
        pricesJob = viewModelScope.launch {
            priceRepository.observePrices()
                .catch { ex ->
                    _uiState.update {
                        it.copy(pricesError = ex.localizedMessage ?: "Error al consultar precios.")
                    }
                }
                .collect { prices ->
                    val resolved = EggClassification.entries.associateWith { classification ->
                        getActivePriceUseCase(prices, classification, today)
                    }
                    _uiState.update {
                        it.copy(
                            operationalDate = today,
                            activePrices = resolved,
                            pricesError = null,
                        )
                    }
                }
        }
    }

    private fun startObservingHarvests() {
        harvestsJob?.cancel()
        harvestsJob = viewModelScope.launch {
            harvestRepository.observeHarvests()
                .catch { ex ->
                    _uiState.update {
                        it.copy(harvestsError = ex.localizedMessage ?: "Error al consultar recolecciones.")
                    }
                }
                .collect { items ->
                    _uiState.update {
                        it.copy(
                            harvests = items,
                            harvestsError = null,
                        )
                    }
                }
        }
    }

    private fun stopObservingData() {
        pricesJob?.cancel()
        pricesJob = null
        harvestsJob?.cancel()
        harvestsJob = null
    }

    private fun initialState(today: LocalDate): MainUiState = MainUiState(
        operationalDate = today,
        harvestDateInput = OperationalDate.format(today),
    )
}
