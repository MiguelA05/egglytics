package com.egglytics.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.egglytics.data.repository.PriceRepository
import com.egglytics.data.repository.SessionRepository
import com.egglytics.data.repository.SessionState
import com.egglytics.domain.model.EggClassification
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
import javax.inject.Inject

enum class AppDestination {
    HOME,
    HARVESTS,
    SALES,
}

data class SessionUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val sessionState: SessionState = SessionState.Unauthenticated,
    val destination: AppDestination = AppDestination.HOME,
    val operationalDate: LocalDate = LocalDate.now(OperationalDate.BOGOTA_ZONE),
    val activePrices: Map<EggClassification, ActivePriceResult> = emptyMap(),
    val pricesError: String? = null,
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

    private fun stopObservingPrices() {
        pricesJob?.cancel()
        pricesJob = null
    }
}
