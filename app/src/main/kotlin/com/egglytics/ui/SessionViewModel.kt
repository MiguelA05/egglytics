package com.egglytics.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.egglytics.data.repository.PriceRepository
import com.egglytics.data.repository.SessionRepository
import com.egglytics.data.repository.SessionState
import com.egglytics.domain.model.EggClassification
import com.egglytics.domain.model.OperationalDate
import com.egglytics.domain.model.MemberRole
import com.egglytics.domain.model.Price
import kotlinx.coroutines.CancellationException
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
import javax.inject.Inject

enum class AppDestination {
    HOME,
    HARVESTS,
    SALES,
    FEED,
    EXPENSES,
    CASH,
    COUNTS,
}

data class PriceEditor(
    val classification: EggClassification,
    val amount: String,
    val validFrom: String,
    val saving: Boolean = false,
    val error: String? = null,
)

data class SessionUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val sessionState: SessionState = SessionState.Unauthenticated,
    val destination: AppDestination = AppDestination.HOME,
    val operationalDate: LocalDate = LocalDate.now(OperationalDate.BOGOTA_ZONE),
    val activePrices: Map<EggClassification, ActivePriceResult> = emptyMap(),
    val pricesError: String? = null,
    val catalog: List<Price> = emptyList(),
    val priceEditor: PriceEditor? = null,
    val priceNotice: String? = null,
)

@HiltViewModel
class SessionViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val priceRepository: PriceRepository,
    private val getActivePriceUseCase: GetActivePriceUseCase,
    private val clock: Clock,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SessionUiState(operationalDate = OperationalDate.today(clock)),
    )
    val uiState: StateFlow<SessionUiState> = _uiState.asStateFlow()

    private var pricesJob: Job? = null

    init {
        restoreSession()
    }

    fun onEmailChange(value: String) {
        _uiState.update { it.copy(email = value) }
    }

    fun onPasswordChange(value: String) {
        _uiState.update { it.copy(password = value) }
    }

    fun navigateTo(destination: AppDestination) {
        if (_uiState.value.sessionState is SessionState.Authenticated) {
            _uiState.update { it.copy(destination = destination) }
        }
    }

    fun navigateHome() {
        _uiState.update { it.copy(destination = AppDestination.HOME) }
    }

    fun editPrice(classification: EggClassification) {
        val state = _uiState.value
        val session = state.sessionState as? SessionState.Authenticated ?: return
        if (session.member.role != MemberRole.ADMIN || state.priceEditor?.saving == true) return
        val today = OperationalDate.today(clock)
        val current = getActivePriceUseCase(state.catalog, classification, today) as? ActivePriceResult.Found
        _uiState.update { it.copy(priceEditor = PriceEditor(classification,
            current?.price?.pricePerBucket?.toString().orEmpty(), OperationalDate.format(today)), priceNotice = null) }
    }

    fun dismissPriceEditor() {
        _uiState.update { if (it.priceEditor?.saving == true) it else it.copy(priceEditor = null) }
    }

    fun savePrice(amountInput: String, dateInput: String) {
        val session = _uiState.value.sessionState as? SessionState.Authenticated ?: return
        val editor = _uiState.value.priceEditor ?: return
        if (session.member.role != MemberRole.ADMIN || editor.saving) return
        val amount = amountInput.trim().toLongOrNull()
        val date = runCatching { OperationalDate.parse(dateInput.trim()) }.getOrNull()
        val error = when {
            amount == null || amount <= 0 -> "Ingresa un valor entero mayor que cero."
            date == null -> "Ingresa una fecha válida en formato YYYY-MM-DD."
            else -> null
        }
        if (error != null || amount == null || date == null) {
            _uiState.update { it.copy(priceEditor = editor.copy(error = error)) }
            return
        }
        val saving = editor.copy(amount = amountInput, validFrom = dateInput, saving = true, error = null)
        _uiState.update { it.copy(priceEditor = saving) }
        viewModelScope.launch {
            try {
                priceRepository.addPrice(editor.classification, amount, date, session.uid)
                _uiState.update { if (it.priceEditor !== saving) it else it.copy(priceEditor = null,
                    priceNotice = "Precio de ${editor.classification.name} guardado con vigencia desde $date.") }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                _uiState.update { if (it.priceEditor !== saving) it else it.copy(priceEditor = saving.copy(
                    saving = false, error = "No se pudo guardar. Comprueba la conexión y tus permisos e inténtalo de nuevo.")) }
            }
        }
    }

    fun signIn() {
        val email = _uiState.value.email.trim()
        val password = _uiState.value.password
        if (email.isEmpty() || password.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, pricesError = null) }
            val result = sessionRepository.signIn(email, password)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    sessionState = result,
                    destination = AppDestination.HOME,
                )
            }
            if (result is SessionState.Authenticated) {
                startObservingPrices()
            } else {
                stopObservingPrices()
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            stopObservingPrices()
            sessionRepository.signOut()
            _uiState.update {
                SessionUiState(operationalDate = OperationalDate.today(clock))
            }
        }
    }

    private fun restoreSession() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = sessionRepository.loadCurrentSession()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    sessionState = result,
                    destination = AppDestination.HOME,
                )
            }
            if (result is SessionState.Authenticated) {
                startObservingPrices()
            }
        }
    }

    private fun startObservingPrices() {
        pricesJob?.cancel()
        pricesJob = viewModelScope.launch {
            priceRepository.observePrices()
                .catch { ex ->
                    _uiState.update {
                        it.copy(pricesError = ex.localizedMessage ?: "Error al consultar precios.")
                    }
                }
                .collect { prices ->
                    _uiState.update { it.withPrices(prices).copy(pricesError = null) }
                }
        }
    }

    suspend fun trackPriceDate() {
        while (true) {
            val now = clock.instant().atZone(OperationalDate.BOGOTA_ZONE)
            if (_uiState.value.operationalDate != now.toLocalDate()) {
                _uiState.update { it.withPrices(it.catalog) }
            }
            val midnight = now.toLocalDate().plusDays(1).atStartOfDay(OperationalDate.BOGOTA_ZONE)
            kotlinx.coroutines.delay(java.time.Duration.between(now, midnight).toMillis().coerceAtLeast(1))
        }
    }

    private fun SessionUiState.withPrices(prices: List<Price>): SessionUiState {
        val today = OperationalDate.today(clock)
        return copy(catalog = prices, operationalDate = today, activePrices = EggClassification.entries.associateWith {
            getActivePriceUseCase(prices, it, today)
        })
    }

    private fun stopObservingPrices() {
        pricesJob?.cancel()
        pricesJob = null
    }
}
