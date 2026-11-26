package com.egglytics.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.egglytics.data.repository.ExpenseAndWithdrawalRepository
import com.egglytics.data.repository.ExpenseCategoryRepository
import com.egglytics.data.repository.ExpenseRequest
import com.egglytics.data.repository.ObservedExpense
import com.egglytics.data.repository.ObservedReversal
import com.egglytics.data.repository.ObservedWithdrawal
import com.egglytics.data.repository.PaymentMethodRepository
import com.egglytics.data.repository.ReversalRepository
import com.egglytics.data.repository.SessionState
import com.egglytics.data.repository.WithdrawalRequest
import com.egglytics.domain.model.ExpenseCategory
import com.egglytics.domain.model.Harvest
import com.egglytics.domain.model.MemberRole
import com.egglytics.domain.model.OperationalDate
import com.egglytics.domain.model.PaymentMethod
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

enum class ExpenseFormMode {
    EXPENSE,
    WITHDRAWAL,
}

data class ExpensesFormState(
    val mode: ExpenseFormMode = ExpenseFormMode.EXPENSE,
    val amountInput: String = "",
    val selectedCategoryId: String? = null,
    val selectedMethodId: String? = null,
    val dateInput: String = "",
    val descriptionInput: String = "",
    val noteInput: String = "",
    val formError: String? = null,
)

data class ExpensesUiState(
    val form: ExpensesFormState = ExpensesFormState(),
    val isAdmin: Boolean = false,
    val categories: List<ExpenseCategory> = emptyList(),
    val paymentMethods: List<PaymentMethod> = emptyList(),
    val expenses: List<ObservedExpense> = emptyList(),
    val withdrawals: List<ObservedWithdrawal> = emptyList(),
    val reversedReasons: Map<String, String> = emptyMap(),
    val historyReady: Boolean = false,
    val error: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ExpensesViewModel @Inject constructor(
    private val expenseAndWithdrawalRepository: ExpenseAndWithdrawalRepository,
    private val categoryRepository: ExpenseCategoryRepository,
    private val paymentMethodRepository: PaymentMethodRepository,
    private val reversalRepository: ReversalRepository,
    private val clock: Clock,
) : ViewModel() {

    private val activeUid = MutableStateFlow<String?>(null)
    private val isAdminFlow = MutableStateFlow(false)
    private val formState = MutableStateFlow(initialFormState(OperationalDate.today(clock)))
    private val writeError = MutableStateFlow<String?>(null)

    private val observedCategories = activeUid.flatMapLatest { uid ->
        if (uid == null) flowOf(emptyList())
        else categoryRepository.observeActiveCategories().catch { emit(emptyList()) }
    }

    private val observedMethods = activeUid.flatMapLatest { uid ->
        if (uid == null) flowOf(emptyList())
        else paymentMethodRepository.observeActivePaymentMethods().catch { emit(emptyList()) }
    }

    private val observedExpenses = activeUid.flatMapLatest { uid ->
        if (uid == null) flowOf(emptyList<ObservedExpense>() to (null as String?))
        else expenseAndWithdrawalRepository.observeExpenses()
            .map { it to (null as String?) }
            .catch { emit(emptyList<ObservedExpense>() to (it.localizedMessage ?: "Error al consultar gastos.")) }
    }

    private val observedWithdrawals = activeUid.flatMapLatest { uid ->
        if (uid == null) flowOf(emptyList<ObservedWithdrawal>() to (null as String?))
        else expenseAndWithdrawalRepository.observeWithdrawals()
            .map { it to (null as String?) }
            .catch { emit(emptyList<ObservedWithdrawal>() to (it.localizedMessage ?: "Error al consultar retiros.")) }
    }

    private val observedReversals = activeUid.flatMapLatest { uid ->
        if (uid == null) flowOf(emptyList<ObservedReversal>() to (null as String?))
        else reversalRepository.observeReversals()
            .map { it to (null as String?) }
            .catch { emit(emptyList<ObservedReversal>() to (it.localizedMessage ?: "Error al consultar anulaciones.")) }
    }

    private val catalogs = combine(
        observedCategories,
        observedMethods,
        observedReversals,
        isAdminFlow,
        writeError,
    ) { cats, methods, reversals, isAdmin, wErr ->
        ExpensesCatalogBundle(cats, methods, reversals.first, isAdmin, wErr, reversals.second)
    }

    val uiState: StateFlow<ExpensesUiState> = combine(
        formState,
        catalogs,
        observedExpenses,
        observedWithdrawals,
    ) { form, bundle, (expenses, expErr), (withdrawals, wdrErr) ->
        val effectiveCategoryId = when {
            form.selectedCategoryId != null && bundle.categories.any { it.id == form.selectedCategoryId } ->
                form.selectedCategoryId
            else -> bundle.categories.firstOrNull()?.id
        }
        val effectiveMethodId = when {
            form.selectedMethodId != null && bundle.paymentMethods.any { it.id == form.selectedMethodId } ->
                form.selectedMethodId
            else -> bundle.paymentMethods.firstOrNull()?.id
        }
        val reversedReasons = bundle.reversals.associate {
            "${it.reversal.targetCollection.firestoreValue}_${it.reversal.targetId}" to it.reversal.reason
        }
        ExpensesUiState(
            form = form.copy(
                selectedCategoryId = effectiveCategoryId,
                selectedMethodId = effectiveMethodId,
            ),
            isAdmin = bundle.isAdmin,
            categories = bundle.categories,
            paymentMethods = bundle.paymentMethods,
            expenses = expenses,
            withdrawals = withdrawals,
            reversedReasons = reversedReasons,
            historyReady = activeUid.value != null && expErr == null && wdrErr == null && bundle.reversalError == null,
            error = listOfNotNull(expErr, wdrErr, bundle.reversalError, bundle.writeError).firstOrNull(),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(0L),
        initialValue = ExpensesUiState(form = initialFormState(OperationalDate.today(clock))),
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

    fun voidMovement(
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

    fun onModeChange(mode: ExpenseFormMode) = formState.update { it.copy(mode = mode, formError = null) }
    fun onAmountChange(v: String) = formState.update { it.copy(amountInput = v, formError = null) }
    fun onCategorySelect(id: String) = formState.update { it.copy(selectedCategoryId = id, formError = null) }
    fun onMethodSelect(id: String) = formState.update { it.copy(selectedMethodId = id, formError = null) }
    fun onDateChange(v: String) = formState.update { it.copy(dateInput = v, formError = null) }
    fun onDescriptionChange(v: String) = formState.update { it.copy(descriptionInput = v) }
    fun onNoteChange(v: String) = formState.update { it.copy(noteInput = v) }

    fun submit(
        session: SessionState.Authenticated,
        docId: String = UUID.randomUUID().toString(),
    ) {
        val current = uiState.value
        val state = current.form
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
        val amount = state.amountInput.trim().toLongOrNull()
        if (amount == null || amount <= 0L) {
            formState.update { it.copy(formError = "Ingresa un monto mayor que 0.") }
            return
        }
        val methodId = state.selectedMethodId
        if (methodId.isNullOrBlank()) {
            formState.update { it.copy(formError = "Selecciona una forma de pago activa.") }
            return
        }

        writeError.value = null
        when (state.mode) {
            ExpenseFormMode.EXPENSE -> {
                val categoryId = state.selectedCategoryId
                if (categoryId.isNullOrBlank()) {
                    formState.update { it.copy(formError = "Selecciona una categoría de gasto.") }
                    return
                }
                expenseAndWithdrawalRepository.recordExpense(
                    ExpenseRequest(
                        id = docId,
                        fechaOperativa = parsedDate,
                        businessStartDate = session.business.startDate,
                        categoryId = categoryId,
                        amount = amount,
                        methodId = methodId,
                        description = state.descriptionInput,
                        note = state.noteInput,
                    ),
                    onWriteError = { err -> writeError.value = err },
                )
            }

            ExpenseFormMode.WITHDRAWAL -> {
                expenseAndWithdrawalRepository.recordWithdrawal(
                    WithdrawalRequest(
                        id = docId,
                        fechaOperativa = parsedDate,
                        businessStartDate = session.business.startDate,
                        amount = amount,
                        methodId = methodId,
                        note = state.noteInput,
                    ),
                    onWriteError = { err -> writeError.value = err },
                )
            }
        }
        formState.update { it.copy(amountInput = "", descriptionInput = "", noteInput = "", formError = null) }
    }

    private fun initialFormState(today: LocalDate): ExpensesFormState =
        ExpensesFormState(dateInput = OperationalDate.format(today))
}

private data class ExpensesCatalogBundle(
    val categories: List<ExpenseCategory>,
    val paymentMethods: List<PaymentMethod>,
    val reversals: List<ObservedReversal>,
    val isAdmin: Boolean,
    val writeError: String?,
    val reversalError: String?,
)
