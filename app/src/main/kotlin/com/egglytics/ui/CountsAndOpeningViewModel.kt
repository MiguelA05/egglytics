package com.egglytics.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.egglytics.data.repository.CountSubmitRequest
import com.egglytics.data.repository.ExpenseAndWithdrawalRepository
import com.egglytics.data.repository.FeedRepository
import com.egglytics.data.repository.OpeningAndCountRepository
import com.egglytics.data.repository.OpeningEntryInput
import com.egglytics.data.repository.PaymentMethodRepository
import com.egglytics.data.repository.ReversalRepository
import com.egglytics.data.repository.SaleRepository
import com.egglytics.data.repository.SessionState
import com.egglytics.domain.model.CountItem
import com.egglytics.domain.model.DailyClosing
import com.egglytics.domain.model.FeedIngredient
import com.egglytics.domain.model.Harvest
import com.egglytics.domain.model.MemberRole
import com.egglytics.domain.model.OpeningBalance
import com.egglytics.domain.model.OpeningKind
import com.egglytics.domain.model.OperationalDate
import com.egglytics.domain.model.PaymentMethod
import com.egglytics.domain.model.PhysicalCount
import com.egglytics.domain.model.Sale
import com.egglytics.domain.usecase.CashAndResultRequest
import com.egglytics.domain.usecase.ClosingInputs
import com.egglytics.domain.usecase.FeedSummaryRequest
import com.egglytics.domain.usecase.GetCashAndResultUseCase
import com.egglytics.domain.usecase.GetFeedBalanceUseCase
import com.egglytics.domain.usecase.buildClosingSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

enum class CountsMode {
    COUNT_OR_CLOSING,
    OPENING,
}

data class CountsFormState(
    val mode: CountsMode = CountsMode.COUNT_OR_CLOSING,
    val dateInput: String = "",
    val feedPhysicalInputs: Map<FeedIngredient, String> = emptyMap(),
    val feedReasonInputs: Map<FeedIngredient, String> = emptyMap(),
    val cashPhysicalInputs: Map<String, String> = emptyMap(),
    val cashReasonInputs: Map<String, String> = emptyMap(),
    val isClosing: Boolean = false,
    val noteInput: String = "",
    val formError: String? = null,
    val lastCountedSummary: String? = null,
)

data class CountsAndOpeningUiState(
    val sessionUid: String? = null,
    val readError: String? = null,
    val closingInputs: ClosingInputs = ClosingInputs(),
    val form: CountsFormState = CountsFormState(),
    val isAdmin: Boolean = false,
    val hasPendingOpenings: Boolean = false,
    val expectedFeedKg: Map<FeedIngredient, Long> = emptyMap(),
    val expectedCashByMethod: Map<String, Long> = emptyMap(),
    val paymentMethods: List<PaymentMethod> = emptyList(),
    val openings: List<OpeningBalance> = emptyList(),
    val counts: List<PhysicalCount> = emptyList(),
    val closings: List<DailyClosing> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CountsAndOpeningViewModel @Inject constructor(
    private val openingAndCountRepository: OpeningAndCountRepository,
    private val paymentMethodRepository: PaymentMethodRepository,
    private val feedRepository: FeedRepository,
    private val saleRepository: SaleRepository,
    private val expenseAndWithdrawalRepository: ExpenseAndWithdrawalRepository,
    private val reversalRepository: ReversalRepository,
    private val getFeedBalanceUseCase: GetFeedBalanceUseCase,
    private val getCashAndResultUseCase: GetCashAndResultUseCase,
    private val clock: Clock,
) : ViewModel() {

    private val activeUid = MutableStateFlow<String?>(null)
    private val isAdmin = MutableStateFlow(false)
    private val selectedMode = MutableStateFlow(CountsMode.COUNT_OR_CLOSING)
    private var countDraft = MutableStateFlow(initialFormState(OperationalDate.today(clock)))
    private var openingDraft = MutableStateFlow(countDraft.value.copy(mode = CountsMode.OPENING))
    private fun draft(mode: CountsMode) = if (mode == CountsMode.OPENING) openingDraft else countDraft
    private val formState get() = draft(selectedMode.value)

    val uiState: StateFlow<CountsAndOpeningUiState> = activeUid.flatMapLatest { uid ->
        val initial = CountsAndOpeningUiState(form = formState.value)
        if (uid == null) {
            flowOf(initial)
        } else {
            observeCounts(uid)
                .onStart { emit(initial) }
                .catch {
                    emit(initial.copy(readError = "No se pudieron cargar todos los saldos. Vuelve a abrir Conteos para reintentar."))
                }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(0L, replayExpirationMillis = 0L),
        initialValue = CountsAndOpeningUiState(form = initialFormState(OperationalDate.today(clock))),
    )

    private fun observeCounts(uid: String): Flow<CountsAndOpeningUiState> {
        val openingsFlow = openingAndCountRepository.observeOpenings()
        val hasPendingOpeningsFlow = openingAndCountRepository.observeHasPendingOpenings()
        val countsFlow = openingAndCountRepository.observeCounts()
        val closingsFlow = openingAndCountRepository.observeClosings()
        val methodsFlow = paymentMethodRepository.observeAllPaymentMethods()
        val feedPurchasesFlow = feedRepository.observePurchases()
        val feedDepletionsFlow = feedRepository.observeDepletions()
        val feedLossesFlow = feedRepository.observeLosses()
        val salesFlow = saleRepository.observeSales()
        val paymentsFlow = saleRepository.observeAllSalePayments()
        val expensesFlow = expenseAndWithdrawalRepository.observeExpenses()
        val withdrawalsFlow = expenseAndWithdrawalRepository.observeWithdrawals()
        val reversalsFlow = reversalRepository.observeReversals()

        val feedBundle = combine(feedPurchasesFlow, feedDepletionsFlow, feedLossesFlow) { p, d, l ->
            Triple(p.map { it.purchase }, d.map { it.depletion }, l.map { it.loss })
        }
        val cashBundle = combine(methodsFlow, salesFlow, paymentsFlow, expensesFlow, withdrawalsFlow) { m, s, p, e, w ->
            CashBundle(
                methods = m,
                sales = s.map { it.sale },
                payments = p.values.flatten().map { it.payment },
                expenses = e.map { it.expense },
                withdrawals = w.map { it.withdrawal },
            )
        }
        val baseBundle = combine(openingsFlow, hasPendingOpeningsFlow, countsFlow, closingsFlow, reversalsFlow) { o, p, c, cl, r ->
            BaseBundle(o, p, c, cl, r.map { it.reversal })
        }

        return combine(
            selectedMode.flatMapLatest { draft(it) },
            isAdmin,
            baseBundle,
            feedBundle,
            cashBundle,
        ) { form, admin, base, feed, cash ->
            val closingInputs = ClosingInputs(
                sales = cash.sales,
                payments = cash.payments,
                expenses = cash.expenses,
                feedPurchases = feed.first,
                withdrawals = cash.withdrawals,
                reversals = base.reversals,
            )
            val today = OperationalDate.today(clock)
            val feedSummaries = getFeedBalanceUseCase(
                FeedSummaryRequest(
                    openings = base.openings,
                    counts = base.counts,
                    purchases = feed.first,
                    depletions = feed.second,
                    losses = feed.third,
                    reversals = base.reversals,
                    periodStart = today,
                    periodEnd = today,
                ),
            )
            val cashSummary = getCashAndResultUseCase(
                CashAndResultRequest(
                    methods = cash.methods,
                    openings = base.openings,
                    counts = base.counts,
                    sales = cash.sales,
                    payments = cash.payments,
                    expenses = cash.expenses,
                    feedPurchases = feed.first,
                    feedDepletions = feed.second,
                    withdrawals = cash.withdrawals,
                    reversals = base.reversals,
                    periodStart = today,
                    periodEnd = today,
                ),
            )
            CountsAndOpeningUiState(
                sessionUid = uid,
                closingInputs = closingInputs,
                form = form,
                isAdmin = admin,
                hasPendingOpenings = base.hasPendingOpenings,
                expectedFeedKg = feedSummaries.mapValues { (_, v) -> v.balanceKg },
                expectedCashByMethod = cashSummary.balancesByMethod.associate { it.method.id to it.balance },
                paymentMethods = cash.methods,
                openings = base.openings,
                counts = base.counts,
                closings = base.closings,
            )
        }
    }

    fun syncSession(session: SessionState) {
        val auth = session as? SessionState.Authenticated
        isAdmin.value = auth?.member?.role == MemberRole.ADMIN
        val nextUid = auth?.uid
        if (activeUid.value != nextUid) {
            countDraft = MutableStateFlow(initialFormState(OperationalDate.today(clock)))
            openingDraft = MutableStateFlow(countDraft.value.copy(mode = CountsMode.OPENING))
            selectedMode.value = CountsMode.COUNT_OR_CLOSING
            activeUid.value = nextUid
        }
    }

    fun onModeChange(mode: CountsMode) {
        if (mode != CountsMode.OPENING || isAdmin.value) selectedMode.value = mode
    }
    fun onDateChange(v: String) = formState.update { it.copy(dateInput = v, formError = null) }
    fun onFeedPhysicalChange(ing: FeedIngredient, v: String) =
        formState.update { it.copy(feedPhysicalInputs = it.feedPhysicalInputs + (ing to v), formError = null) }
    fun onFeedReasonChange(ing: FeedIngredient, v: String) =
        formState.update { it.copy(feedReasonInputs = it.feedReasonInputs + (ing to v), formError = null) }
    fun onCashPhysicalChange(id: String, v: String) =
        formState.update { it.copy(cashPhysicalInputs = it.cashPhysicalInputs + (id to v), formError = null) }
    fun onCashReasonChange(id: String, v: String) =
        formState.update { it.copy(cashReasonInputs = it.cashReasonInputs + (id to v), formError = null) }
    fun onIsClosingChange(v: Boolean) = formState.update { it.copy(isClosing = v, formError = null) }
    fun onNoteChange(v: String) = formState.update { it.copy(noteInput = v) }

    fun submit(session: SessionState.Authenticated) {
        val current = uiState.value
        if (current.sessionUid != session.uid || current.readError != null) return
        val state = current.form
        val parsedDate = runCatching { OperationalDate.parse(state.dateInput.trim()) }.getOrElse { ex ->
            formState.update { it.copy(formError = ex.message ?: "Fecha inválida.") }
            return
        }
        val dateErr = runCatching {
            Harvest.validateOperationalDate(parsedDate, session.business.startDate)
        }.exceptionOrNull()
        if (dateErr != null) {
            formState.update { it.copy(formError = dateErr.message ?: "Fecha inválida.") }
            return
        }

        if (state.mode == CountsMode.OPENING) {
            submitOpening(session, state, parsedDate, current.paymentMethods)
        } else {
            submitCountOrClosing(session, state, parsedDate, current)
        }
    }

    private fun submitOpening(
        session: SessionState.Authenticated,
        state: CountsFormState,
        parsedDate: LocalDate,
        methods: List<PaymentMethod>,
    ) {
        if (session.member.role != MemberRole.ADMIN) {
            formState.update { it.copy(formError = "Solo un administrador puede registrar apertura.") }
            return
        }
        val entries = mutableListOf<OpeningEntryInput>()
        FeedIngredient.entries.forEach { ing ->
            val raw = state.feedPhysicalInputs[ing].orEmpty().trim()
            if (raw.isNotEmpty()) {
                val qty = raw.toLongOrNull()?.takeIf { it >= 0L }
                    ?: return formState.update { it.copy(formError = "Kg inválido para ${ing.code}.") }
                entries.add(OpeningEntryInput(OpeningKind.FEED, ing.code, qty))
            }
        }
        methods.forEach { m ->
            val raw = state.cashPhysicalInputs[m.id].orEmpty().trim()
            if (raw.isNotEmpty()) {
                val qty = raw.toLongOrNull()?.takeIf { it >= 0L }
                    ?: return formState.update { it.copy(formError = "Monto inválido para ${m.name}.") }
                entries.add(OpeningEntryInput(OpeningKind.CASH, m.id, qty))
            }
        }
        if (entries.isEmpty()) {
            formState.update { it.copy(formError = "Ingresa al menos un saldo inicial.") }
            return
        }
        val submittedDraft = openingDraft
        openingAndCountRepository.recordOpenings(
            fechaOperativa = parsedDate,
            businessStartDate = session.business.startDate,
            entries = entries,
            onSuccess = {
                submittedDraft.update {
                    it.copy(
                        feedPhysicalInputs = emptyMap(),
                        cashPhysicalInputs = emptyMap(),
                        formError = null,
                        lastCountedSummary = "Apertura guardada (${entries.size} saldos iniciales).",
                    )
                }
            },
            onError = { err -> submittedDraft.update { it.copy(formError = err) } },
        )
    }

    private fun submitCountOrClosing(
        session: SessionState.Authenticated,
        state: CountsFormState,
        parsedDate: LocalDate,
        current: CountsAndOpeningUiState,
    ) {
        val feedItems = mutableMapOf<String, CountItem>()
        val omitted = mutableListOf<String>()

        for (ing in FeedIngredient.entries) {
            val raw = state.feedPhysicalInputs[ing].orEmpty().trim()
            if (raw.isEmpty()) {
                omitted.add(ing.code)
                continue
            }
            val physical = raw.toLongOrNull()?.takeIf { it >= 0L }
                ?: return formState.update { it.copy(formError = "Kg inválido para ${ing.code}.") }
            val expected = current.expectedFeedKg[ing] ?: 0L
            val reason = state.feedReasonInputs[ing]?.trim()
            val item = runCatching { CountItem(expected, physical, physical - expected, reason) }.getOrElse { ex ->
                formState.update { it.copy(formError = "${ing.code}: ${ex.message}") }
                return
            }
            feedItems[ing.code] = item
        }

        val cashItems = mutableMapOf<String, CountItem>()
        for (method in current.paymentMethods) {
            val raw = state.cashPhysicalInputs[method.id].orEmpty().trim()
            if (raw.isEmpty()) {
                omitted.add(method.name)
                continue
            }
            val physical = raw.toLongOrNull()?.takeIf { it >= 0L }
                ?: return formState.update { it.copy(formError = "Monto inválido para ${method.name}.") }
            val expected = current.expectedCashByMethod[method.id] ?: 0L
            val reason = state.cashReasonInputs[method.id]?.trim()
            val item = runCatching { CountItem(expected, physical, physical - expected, reason) }.getOrElse { ex ->
                formState.update { it.copy(formError = "${method.name}: ${ex.message}") }
                return
            }
            cashItems[method.id] = item
        }

        if (!state.isClosing && feedItems.isEmpty() && cashItems.isEmpty()) {
            formState.update { it.copy(formError = "Cuenta al menos un ítem de alimento o caja.") }
            return
        }

        val countedKeys = (feedItems.keys + cashItems.keys).joinToString(", ").ifEmpty { "ninguno" }
        val omittedKeys = omitted.joinToString(", ").ifEmpty { "ninguno" }
        val closingSummary = if (state.isClosing) {
            buildClosingSummary(
                inputs = current.closingInputs,
                fecha = parsedDate,
                feedItems = feedItems,
                cashItems = cashItems,
                omittedCount = omitted.size.toLong(),
            )
        } else {
            emptyMap()
        }

        val submittedDraft = countDraft
        openingAndCountRepository.recordCountOrClosing(
            CountSubmitRequest(
                fechaOperativa = parsedDate,
                businessStartDate = session.business.startDate,
                feedItems = feedItems,
                cashItems = cashItems,
                isClosing = state.isClosing,
                closingSummary = closingSummary,
                note = state.noteInput,
            ),
            onSuccess = {
                val prefix = if (state.isClosing) "Cierre guardado" else "Conteo guardado"
                submittedDraft.update {
                    it.copy(
                        feedPhysicalInputs = emptyMap(),
                        feedReasonInputs = emptyMap(),
                        cashPhysicalInputs = emptyMap(),
                        cashReasonInputs = emptyMap(),
                        noteInput = "",
                        formError = null,
                        lastCountedSummary = "$prefix · Contados: $countedKeys · Omitidos: $omittedKeys",
                    )
                }
            },
            onError = { err -> submittedDraft.update { it.copy(formError = err) } },
        )
    }

    private fun initialFormState(today: LocalDate): CountsFormState =
        CountsFormState(dateInput = OperationalDate.format(today))

    private data class CashBundle(
        val methods: List<PaymentMethod>,
        val sales: List<Sale>,
        val payments: List<com.egglytics.domain.model.Payment>,
        val expenses: List<com.egglytics.domain.model.Expense>,
        val withdrawals: List<com.egglytics.domain.model.Withdrawal>,
    )

    private data class BaseBundle(
        val openings: List<OpeningBalance>,
        val hasPendingOpenings: Boolean,
        val counts: List<PhysicalCount>,
        val closings: List<DailyClosing>,
        val reversals: List<com.egglytics.domain.model.Reversal>,
    )

}
