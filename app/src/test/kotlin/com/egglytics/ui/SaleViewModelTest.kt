package com.egglytics.ui

import com.egglytics.data.model.Business
import com.egglytics.data.repository.ObservedReversal
import com.egglytics.data.repository.ObservedSale
import com.egglytics.data.repository.PaymentMethodRepository
import com.egglytics.data.repository.ReversalRepository
import com.egglytics.data.repository.SaleRepository
import com.egglytics.data.repository.SaleWithPaymentRequest
import com.egglytics.data.repository.SessionState
import com.egglytics.domain.model.DeliveryStatus
import com.egglytics.domain.model.EggClassification
import com.egglytics.domain.model.Harvest
import com.egglytics.domain.model.Member
import com.egglytics.domain.model.MemberRole
import com.egglytics.domain.model.OperationalDate
import com.egglytics.domain.model.Payment
import com.egglytics.domain.model.PaymentMethod
import com.egglytics.domain.model.Price
import com.egglytics.domain.model.Reversal
import com.egglytics.domain.model.ReversalTargetCollection
import com.egglytics.domain.model.Sale
import com.egglytics.domain.model.SingleLineSaleRequest
import com.egglytics.domain.usecase.ActivePriceResult
import com.egglytics.domain.usecase.ValidateSaleReversalUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class SaleViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val fixedClock = Clock.fixed(Instant.parse("2026-11-21T15:00:00Z"), OperationalDate.BOGOTA_ZONE)

    private val authSession = SessionState.Authenticated(
        uid = "operator_1",
        email = "operador@example.com",
        member = Member(
            uid = "operator_1",
            name = "Madre",
            email = "operador@example.com",
            role = MemberRole.OPERATOR,
            active = true,
            createdAt = Instant.parse("2026-11-01T12:00:00Z"),
        ),
        business = Business(
            id = "egglytics_default",
            name = "Granja Familiar",
            currency = "COP",
            timezone = "America/Bogota",
            startDate = LocalDate.of(2026, 11, 1),
            active = true,
        ),
    )

    private val activeAaPrice = Price(
        id = "p_aa",
        classification = EggClassification.AA,
        pricePerBucket = 18500L,
        validFrom = LocalDate.of(2026, 11, 18),
        createdAt = Instant.parse("2026-11-18T10:00:00Z"),
        createdBy = "admin_1",
    )

    private val cashMethod = PaymentMethod(id = "cash", name = "Efectivo", active = true)
    private val nequiMethod = PaymentMethod(id = "nequi", name = "Nequi", active = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `local enqueue unlocks form while offline pending, prevents double-tap, and delivers`() = runTest {
        val repo = FakeSaleRepository(emitLocallyImmediately = false)
        val methodRepo = FakePaymentMethodRepository(listOf(cashMethod, nequiMethod))
        val vm = SaleViewModel(
            repo,
            methodRepo,
            FakeReversalRepository(),
            ValidateSaleReversalUseCase(),
            fixedClock,
        )
        vm.syncSession(authSession)
        val collectJob = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        val activePrices = mapOf<EggClassification, ActivePriceResult>(
            EggClassification.AA to ActivePriceResult.Found(activeAaPrice),
        )

        vm.onSaleClassificationChange(EggClassification.AA)
        vm.onPaymentMethodSelect("nequi")
        vm.onSaleBucketsChange("2")
        vm.onSaleNoteChange("Contado Nequi")
        vm.submitSale(authSession, activePrices, "sale_aa_1", "pay_aa_1")
        advanceUntilIdle()

        // Double tap before local snapshot arrives must be ignored
        assertTrue(vm.uiState.value.form.isSubmitting)
        vm.submitSale(authSession, activePrices, "sale_aa_2", "pay_aa_2")
        advanceUntilIdle()
        assertEquals(listOf("sale_aa_1"), repo.createdSaleIds)

        // Local snapshot emits pending write -> unlocks form immediately without server confirmation
        repo.emitLatestLocally(isPendingWrite = true)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.form.isSubmitting)
        assertNull(vm.uiState.value.form.saleError)
        assertEquals("", vm.uiState.value.form.saleBucketsInput)
        val created = vm.uiState.value.sales.first()
        assertTrue(created.isPendingWrite)
        assertEquals(DeliveryStatus.PENDING, created.sale.deliveryStatus)
        assertEquals(37000L, created.sale.total)
        assertEquals(37000L, vm.uiState.value.paidAmountBySaleId["sale_aa_1"])

        vm.markSaleDelivered("sale_aa_1")
        advanceUntilIdle()
        val delivered = vm.uiState.value.sales.first().sale
        assertEquals(DeliveryStatus.DELIVERED, delivered.deliveryStatus)
        assertEquals(37000L, delivered.total)
        assertEquals(created.sale.lines, delivered.lines)

        vm.syncSession(SessionState.Unauthenticated)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.sales.isEmpty())
        collectJob.cancel()
    }

    @Test
    fun `remote rejection of previous batch shows error without overwriting current form edits`() = runTest {
        val repo = FakeSaleRepository(emitLocallyImmediately = true)
        val methodRepo = FakePaymentMethodRepository(listOf(cashMethod))
        val vm = SaleViewModel(
            repo,
            methodRepo,
            FakeReversalRepository(),
            ValidateSaleReversalUseCase(),
            fixedClock,
        )
        vm.syncSession(authSession)
        val collectJob = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        val activePrices = mapOf<EggClassification, ActivePriceResult>(
            EggClassification.AA to ActivePriceResult.Found(activeAaPrice),
        )
        vm.onSaleBucketsChange("2")
        vm.submitSale(authSession, activePrices, "sale_1", "pay_1")
        advanceUntilIdle()
        assertFalse(vm.uiState.value.form.isSubmitting)

        // User starts preparing a second sale while first batch fails remotely
        vm.onSaleBucketsChange("5")
        vm.onSaleNoteChange("En edicion")
        repo.triggerRemoteError("sale_1", "PERMISSION_DENIED")
        advanceUntilIdle()

        assertNotNull(vm.uiState.value.salesError)
        assertEquals("5", vm.uiState.value.form.saleBucketsInput)
        assertEquals("En edicion", vm.uiState.value.form.saleNoteInput)
        collectJob.cancel()
    }

    @Test
    fun `repeated screen navigation does not accumulate active listeners`() = runTest {
        val repo = FakeSaleRepository()
        val methodRepo = FakePaymentMethodRepository(listOf(cashMethod))
        val vm = SaleViewModel(
            repo,
            methodRepo,
            FakeReversalRepository(),
            ValidateSaleReversalUseCase(),
            fixedClock,
        )
        vm.syncSession(authSession)

        repeat(3) {
            val job = launch { vm.uiState.collect {} }
            advanceUntilIdle()
            assertEquals(1, repo.activeListeners)
            job.cancel()
            advanceUntilIdle()
            assertEquals(0, repo.activeListeners)
        }
    }

    @Test
    fun `only admin can void confirmed sale with full payment and failed batch leaves no partial reversal`() = runTest {
        val repo = FakeSaleRepository()
        val methodRepo = FakePaymentMethodRepository(listOf(cashMethod))
        val reversalRepo = FakeReversalRepository()
        val vm = SaleViewModel(
            repo,
            methodRepo,
            reversalRepo,
            ValidateSaleReversalUseCase(),
            fixedClock,
        )
        val adminSession = authSession.copy(
            uid = "admin_1",
            member = authSession.member.copy(uid = "admin_1", role = MemberRole.ADMIN),
        )
        vm.syncSession(adminSession)
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        val activePrices = mapOf<EggClassification, ActivePriceResult>(
            EggClassification.AA to ActivePriceResult.Found(activeAaPrice),
        )
        vm.onSaleBucketsChange("2")
        vm.submitSale(adminSession, activePrices, saleId = "s_void", paymentId = "p_void")
        repo.markConfirmed("s_void")
        advanceUntilIdle()

        val observedSale = vm.uiState.value.sales.first()
        reversalRepo.shouldFailNextBatch = true
        vm.voidSale(adminSession, observedSale, "Cliente canceló")
        advanceUntilIdle()

        assertTrue(vm.uiState.value.reversedSaleReasons.isEmpty())
        assertEquals("Fallo de lote", vm.uiState.value.salesError)

        reversalRepo.shouldFailNextBatch = false
        vm.voidSale(adminSession, observedSale, "Cliente canceló")
        advanceUntilIdle()

        assertEquals("Cliente canceló", vm.uiState.value.reversedSaleReasons["s_void"])
        assertEquals(0L, vm.uiState.value.paidAmountBySaleId["s_void"])

        // Operator cannot void
        vm.syncSession(authSession)
        advanceUntilIdle()
        assertEquals(false, vm.uiState.value.canVoid)

        job.cancel()
    }

    private class FakePaymentMethodRepository(initial: List<PaymentMethod>) : PaymentMethodRepository {
        private val flow = MutableStateFlow(initial)
        override fun observeActivePaymentMethods(): Flow<List<PaymentMethod>> = flow
    }

    private class FakeSaleRepository(private val emitLocallyImmediately: Boolean = true) : SaleRepository {
        private val salesFlow = MutableStateFlow<List<ObservedSale>>(emptyList())
        private val paymentsFlow = MutableStateFlow<Map<String, List<Payment>>>(emptyMap())
        val createdSaleIds = mutableListOf<String>()
        var activeListeners = 0

        private var lastRequest: SaleWithPaymentRequest? = null
        private val errorHandlers = mutableMapOf<String, (String) -> Unit>()

        override fun observeSales(): Flow<List<ObservedSale>> = callbackFlow {
            activeListeners++
            val job = launch { salesFlow.collect { trySend(it) } }
            awaitClose {
                activeListeners--
                job.cancel()
            }
        }

        override fun observeSalePayments(): Flow<Map<String, List<Payment>>> = paymentsFlow

        override fun createPendingSaleWithFullPayment(
            request: SaleWithPaymentRequest,
            onWriteError: (String) -> Unit,
        ) {
            createdSaleIds.add(request.saleId)
            lastRequest = request
            errorHandlers[request.saleId] = onWriteError
            if (emitLocallyImmediately) {
                emitLatestLocally(isPendingWrite = true)
            }
        }

        fun emitLatestLocally(isPendingWrite: Boolean) {
            val req = lastRequest ?: return
            val sale = Sale.createSingleLinePending(
                SingleLineSaleRequest(
                    id = req.saleId,
                    fechaOperativa = req.fechaOperativa,
                    businessStartDate = req.businessStartDate,
                    createdAt = Instant.parse("2026-11-21T15:00:00Z"),
                    createdBy = "operator_1",
                    classification = req.classification,
                    buckets = req.buckets,
                    activePrice = req.activePrice,
                    note = req.note,
                ),
            )
            val payment = Payment.forFullSalePayment(req.paymentId, sale, req.methodId)
            salesFlow.value = listOf(ObservedSale(sale = sale, isPendingWrite = isPendingWrite)) + salesFlow.value
            paymentsFlow.value = paymentsFlow.value + (sale.id to listOf(payment))
            lastRequest = null
        }

        fun triggerRemoteError(saleId: String, message: String) {
            salesFlow.value = salesFlow.value.filterNot { it.sale.id == saleId }
            paymentsFlow.value = paymentsFlow.value - saleId
            errorHandlers[saleId]?.invoke(message)
        }

        fun markConfirmed(saleId: String) {
            salesFlow.value = salesFlow.value.map {
                if (it.sale.id == saleId) it.copy(isPendingWrite = false) else it
            }
        }

        override fun markDelivered(saleId: String, onWriteError: (String) -> Unit) {
            salesFlow.value = salesFlow.value.map { observed ->
                if (observed.sale.id == saleId) {
                    val s = observed.sale
                    observed.copy(
                        sale = s.copy(
                            deliveryStatus = DeliveryStatus.DELIVERED,
                            deliveredAt = Instant.parse("2026-11-21T16:00:00Z"),
                            deliveredBy = "operator_1",
                        ),
                    )
                } else {
                    observed
                }
            }
        }
    }

    private class FakeReversalRepository : ReversalRepository {
        private val flow = MutableStateFlow<List<ObservedReversal>>(emptyList())
        var shouldFailNextBatch = false

        override fun observeReversals(): Flow<List<ObservedReversal>> = flow

        override fun reverseHarvest(
            harvest: Harvest,
            businessStartDate: LocalDate,
            reason: String,
            onSuccess: () -> Unit,
            onError: (String) -> Unit,
        ) = Unit

        override fun reverseSaleWithPayment(
            sale: Sale,
            payment: Payment,
            businessStartDate: LocalDate,
            reason: String,
            onSuccess: () -> Unit,
            onError: (String) -> Unit,
        ) {
            if (shouldFailNextBatch) {
                onError("Fallo de lote")
                return
            }
            val now = Instant.parse("2026-11-21T16:00:00Z")
            val today = LocalDate.of(2026, 11, 21)
            val revPayment = Reversal(
                id = Reversal.paymentReversalId(payment.id),
                fechaOperativa = today,
                createdAt = now,
                createdBy = "admin_1",
                targetCollection = ReversalTargetCollection.PAYMENTS,
                targetId = payment.id,
                reason = reason,
            )
            val revSale = Reversal(
                id = Reversal.saleReversalId(sale.id),
                fechaOperativa = today,
                createdAt = now,
                createdBy = "admin_1",
                targetCollection = ReversalTargetCollection.SALES,
                targetId = sale.id,
                reason = reason,
            )
            flow.value = listOf(
                ObservedReversal(revSale, isPendingWrite = false),
                ObservedReversal(revPayment, isPendingWrite = false),
            ) + flow.value
            onSuccess()
        }
    }
}
