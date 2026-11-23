package com.egglytics.ui

import com.egglytics.data.model.Business
import com.egglytics.data.repository.HarvestRepository
import com.egglytics.data.repository.ObservedHarvest
import com.egglytics.data.repository.ObservedReversal
import com.egglytics.data.repository.ObservedSale
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
import com.egglytics.domain.model.PaymentType
import com.egglytics.domain.model.Sale
import com.egglytics.domain.model.SaleLine
import com.egglytics.domain.usecase.GetDailySummaryUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val fixedClock = Clock.fixed(Instant.parse("2026-11-21T15:00:00Z"), OperationalDate.BOGOTA_ZONE)
    private val today = LocalDate.of(2026, 11, 21)
    private val yesterday = LocalDate.of(2026, 11, 20)
    private val now = Instant.parse("2026-11-21T15:00:00Z")

    private val authenticated = SessionState.Authenticated(
        uid = "op_1",
        email = "operador@example.com",
        member = Member(
            uid = "op_1",
            name = "Madre",
            email = "operador@example.com",
            role = MemberRole.OPERATOR,
            active = true,
            createdAt = now,
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

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `summary includes today harvest, previous pending buckets, unpaid sale and pending sync count`() = runTest {
        val harvestRepo = FakeHarvestRepository(
            listOf(
                ObservedHarvest(
                    harvest = Harvest(
                        id = "h1",
                        fechaOperativa = today,
                        createdAt = now,
                        createdBy = "op_1",
                        graded = mapOf(EggClassification.AA to 120L),
                        discarded = 5L,
                    ),
                    isPendingWrite = true,
                ),
            ),
        )
        val yesterdayPendingSale = Sale(
            id = "s_prev",
            fechaOperativa = yesterday,
            createdAt = now.minusSeconds(86400),
            createdBy = "op_1",
            lines = listOf(SaleLine.create(EggClassification.AA, buckets = 1L, listPrice = 18500L)),
            total = 18500L,
            deliveryStatus = DeliveryStatus.PENDING,
        )
        val todayUnpaidSale = Sale(
            id = "s_today_unpaid",
            fechaOperativa = today,
            createdAt = now,
            createdBy = "op_1",
            lines = listOf(SaleLine.create(EggClassification.AA, buckets = 2L, listPrice = 18500L)),
            total = 37000L,
            deliveryStatus = DeliveryStatus.PENDING,
        )
        val saleRepo = FakeSaleRepository(
            sales = listOf(
                ObservedSale(yesterdayPendingSale, isPendingWrite = false),
                ObservedSale(todayUnpaidSale, isPendingWrite = true),
            ),
            payments = mapOf(
                "s_prev" to listOf(
                    Payment(
                        id = "p_prev",
                        fechaOperativa = yesterday,
                        createdAt = now.minusSeconds(86400),
                        createdBy = "op_1",
                        saleId = "s_prev",
                        amount = 18500L,
                        methodId = "cash",
                        type = PaymentType.PAYMENT,
                    ),
                ),
            ),
        )

        val vm = HomeViewModel(harvestRepo, saleRepo, FakeReversalRepository(), GetDailySummaryUseCase(), fixedClock)
        val job = launch { vm.uiState.collect {} }
        vm.syncSession(authenticated)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(120L, state.summary.gradedEggs)
        assertEquals(5L, state.summary.discardedEggs)
        assertEquals(37000L, state.summary.totalSold)
        assertEquals(0L, state.summary.totalCollected)
        assertEquals(3L, state.summary.pendingDeliveryBuckets)
        assertEquals(2, state.pendingSyncCount)

        vm.syncSession(SessionState.Unauthenticated)
        advanceUntilIdle()
        assertEquals(0L, vm.uiState.value.summary.gradedEggs)
        assertEquals(0L, vm.uiState.value.summary.totalSold)
        assertEquals(0, vm.uiState.value.pendingSyncCount)

        job.cancel()
    }

    @Test
    fun `when payments stream fails, paymentsIncomplete is true while other summary figures remain valid`() = runTest {
        val harvestRepo = FakeHarvestRepository(
            listOf(
                ObservedHarvest(
                    harvest = Harvest(
                        id = "h1",
                        fechaOperativa = today,
                        createdAt = now,
                        createdBy = "op_1",
                        graded = mapOf(EggClassification.AA to 120L),
                        discarded = 5L,
                    ),
                    isPendingWrite = false,
                ),
            ),
        )
        val todaySale = Sale(
            id = "s1",
            fechaOperativa = today,
            createdAt = now,
            createdBy = "op_1",
            lines = listOf(SaleLine.create(EggClassification.AA, buckets = 2L, listPrice = 18500L)),
            total = 37000L,
            deliveryStatus = DeliveryStatus.PENDING,
        )
        val failingPaymentsSaleRepo = object : SaleRepository {
            override fun observeSales(): Flow<List<ObservedSale>> =
                MutableStateFlow(listOf(ObservedSale(todaySale, isPendingWrite = false)))

            override fun observeSalePayments(): Flow<Map<String, List<Payment>>> =
                flow { throw IllegalStateException("Fallo en pagos") }

            override fun createPendingSaleWithFullPayment(
                request: SaleWithPaymentRequest,
                onWriteError: (String) -> Unit,
            ) = Unit

            override fun markDelivered(saleId: String, onWriteError: (String) -> Unit) = Unit
        }

        val vm = HomeViewModel(
            harvestRepo,
            failingPaymentsSaleRepo,
            FakeReversalRepository(),
            GetDailySummaryUseCase(),
            fixedClock,
        )
        val job = launch { vm.uiState.collect {} }
        vm.syncSession(authenticated)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(120L, state.summary.gradedEggs)
        assertEquals(37000L, state.summary.totalSold)
        assertEquals(false, state.harvestsIncomplete)
        assertEquals(false, state.salesIncomplete)
        assertEquals(true, state.paymentsIncomplete)
        assertEquals("Fallo en pagos", state.summaryError)

        job.cancel()
    }

    private class FakeHarvestRepository(
        initial: List<ObservedHarvest>,
    ) : HarvestRepository {
        private val flow = MutableStateFlow(initial)
        override fun observeHarvests(): Flow<List<ObservedHarvest>> = flow
        override fun recordHarvest(
            harvestId: String,
            fechaOperativa: LocalDate,
            graded: Map<EggClassification, Long>,
            discarded: Long,
            note: String?,
            onWriteError: (String) -> Unit,
        ) = Unit
    }

    private class FakeSaleRepository(
        sales: List<ObservedSale>,
        payments: Map<String, List<Payment>>,
    ) : SaleRepository {
        private val salesFlow = MutableStateFlow(sales)
        private val paymentsFlow = MutableStateFlow(payments)
        override fun observeSales(): Flow<List<ObservedSale>> = salesFlow
        override fun observeSalePayments(): Flow<Map<String, List<Payment>>> = paymentsFlow
        override fun createPendingSaleWithFullPayment(
            request: SaleWithPaymentRequest,
            onWriteError: (String) -> Unit,
        ) = Unit

        override fun markDelivered(saleId: String, onWriteError: (String) -> Unit) = Unit
    }

    private class FakeReversalRepository(
        initial: List<ObservedReversal> = emptyList(),
    ) : ReversalRepository {
        private val flow = MutableStateFlow(initial)
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
        ) = Unit
    }
}
