package com.egglytics.ui

import com.egglytics.data.model.Business
import com.egglytics.data.repository.ObservedSale
import com.egglytics.data.repository.PaymentMethodRepository
import com.egglytics.data.repository.SaleRepository
import com.egglytics.data.repository.SaleWithPaymentRequest
import com.egglytics.data.repository.SessionState
import com.egglytics.domain.model.DeliveryStatus
import com.egglytics.domain.model.EggClassification
import com.egglytics.domain.model.Member
import com.egglytics.domain.model.MemberRole
import com.egglytics.domain.model.OperationalDate
import com.egglytics.domain.model.Payment
import com.egglytics.domain.model.PaymentMethod
import com.egglytics.domain.model.Price
import com.egglytics.domain.model.Sale
import com.egglytics.domain.model.SingleLineSaleRequest
import com.egglytics.domain.usecase.ActivePriceResult
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
    fun `submitSale creates pending sale and full payment atomically, prevents double-tap, and delivers`() = runTest {
        val repo = FakeSaleRepository(autoComplete = false)
        val methodRepo = FakePaymentMethodRepository(listOf(cashMethod, nequiMethod))
        val vm = SaleViewModel(repo, methodRepo, fixedClock)
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

        // Double tap while submitting must be ignored
        assertTrue(vm.uiState.value.form.isSubmitting)
        vm.submitSale(authSession, activePrices, "sale_aa_2", "pay_aa_2")
        advanceUntilIdle()
        assertEquals(listOf("sale_aa_1"), repo.createdSaleIds)

        repo.completePendingSuccess()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.form.isSubmitting)
        assertNull(vm.uiState.value.form.saleError)
        val created = vm.uiState.value.sales.first().sale
        assertEquals(DeliveryStatus.PENDING, created.deliveryStatus)
        assertEquals(37000L, created.total)
        assertEquals(37000L, vm.uiState.value.paidAmountBySaleId["sale_aa_1"])

        vm.markSaleDelivered("sale_aa_1")
        advanceUntilIdle()
        val delivered = vm.uiState.value.sales.first().sale
        assertEquals(DeliveryStatus.DELIVERED, delivered.deliveryStatus)
        assertEquals(37000L, delivered.total)
        assertEquals(created.lines, delivered.lines)
        assertEquals(37000L, vm.uiState.value.paidAmountBySaleId["sale_aa_1"])

        vm.syncSession(SessionState.Unauthenticated)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.sales.isEmpty())
        collectJob.cancel()
    }

    @Test
    fun `batch rejection exposes error and leaves no sale or payment persisted`() = runTest {
        val repo = FakeSaleRepository(autoComplete = false)
        val methodRepo = FakePaymentMethodRepository(listOf(cashMethod))
        val vm = SaleViewModel(repo, methodRepo, fixedClock)
        vm.syncSession(authSession)
        val collectJob = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        val activePrices = mapOf<EggClassification, ActivePriceResult>(
            EggClassification.AA to ActivePriceResult.Found(activeAaPrice),
        )
        vm.onSaleBucketsChange("2")
        vm.submitSale(authSession, activePrices, "sale_fail", "pay_fail")
        advanceUntilIdle()

        repo.completePendingError("PERMISSION_DENIED")
        advanceUntilIdle()

        assertFalse(vm.uiState.value.form.isSubmitting)
        assertNotNull(vm.uiState.value.form.saleError)
        assertTrue(vm.uiState.value.sales.isEmpty())
        assertTrue(vm.uiState.value.paidAmountBySaleId.isEmpty())
        collectJob.cancel()
    }

    @Test
    fun `repeated screen navigation does not accumulate active listeners`() = runTest {
        val repo = FakeSaleRepository()
        val methodRepo = FakePaymentMethodRepository(listOf(cashMethod))
        val vm = SaleViewModel(repo, methodRepo, fixedClock)
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

    private class FakePaymentMethodRepository(initial: List<PaymentMethod>) : PaymentMethodRepository {
        private val flow = MutableStateFlow(initial)
        override fun observeActivePaymentMethods(): Flow<List<PaymentMethod>> = flow
    }

    private class FakeSaleRepository(private val autoComplete: Boolean = true) : SaleRepository {
        private val salesFlow = MutableStateFlow<List<ObservedSale>>(emptyList())
        private val paymentsFlow = MutableStateFlow<Map<String, List<Payment>>>(emptyMap())
        val createdSaleIds = mutableListOf<String>()
        var activeListeners = 0

        private var pendingRequest: SaleWithPaymentRequest? = null
        private var pendingSuccess: (() -> Unit)? = null
        private var pendingError: ((String) -> Unit)? = null

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
            onWriteSuccess: () -> Unit,
            onWriteError: (String) -> Unit,
        ) {
            createdSaleIds.add(request.saleId)
            pendingRequest = request
            pendingSuccess = onWriteSuccess
            pendingError = onWriteError
            if (autoComplete) {
                completePendingSuccess()
            }
        }

        fun completePendingSuccess() {
            val req = pendingRequest ?: return
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
            salesFlow.value = listOf(ObservedSale(sale = sale, isPendingWrite = false)) + salesFlow.value
            paymentsFlow.value = paymentsFlow.value + (sale.id to listOf(payment))
            pendingSuccess?.invoke()
            pendingRequest = null
        }

        fun completePendingError(message: String) {
            pendingError?.invoke(message)
            pendingRequest = null
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
}
