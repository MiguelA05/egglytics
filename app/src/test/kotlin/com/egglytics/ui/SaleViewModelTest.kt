package com.egglytics.ui

import com.egglytics.data.model.Business
import com.egglytics.data.repository.ObservedSale
import com.egglytics.data.repository.SaleRepository
import com.egglytics.data.repository.SessionState
import com.egglytics.domain.model.DeliveryStatus
import com.egglytics.domain.model.EggClassification
import com.egglytics.domain.model.Member
import com.egglytics.domain.model.MemberRole
import com.egglytics.domain.model.OperationalDate
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

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `submitSale creates pending sale, markSaleDelivered updates status and signOut clears list`() = runTest {
        val repo = FakeSaleRepository()
        val vm = SaleViewModel(repo, fixedClock)
        vm.syncSession(authSession)
        val collectJob = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        val activePrices = mapOf<EggClassification, ActivePriceResult>(
            EggClassification.AA to ActivePriceResult.Found(activeAaPrice),
        )

        vm.onSaleClassificationChange(EggClassification.B)
        vm.onSaleBucketsChange("2")
        vm.submitSale(authSession, activePrices, "sale_no_price")
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.form.saleError)
        assertTrue(repo.createdIds.isEmpty())

        vm.onSaleClassificationChange(EggClassification.AA)
        vm.onSaleBucketsChange("2")
        vm.onSaleNoteChange("Tienda esquina")
        vm.submitSale(authSession, activePrices, "sale_aa_1")
        advanceUntilIdle()

        assertNull(vm.uiState.value.form.saleError)
        val created = vm.uiState.value.sales.first().sale
        assertEquals(DeliveryStatus.PENDING, created.deliveryStatus)
        assertEquals(37000L, created.total)

        vm.markSaleDelivered("sale_aa_1")
        advanceUntilIdle()
        val delivered = vm.uiState.value.sales.first().sale
        assertEquals(DeliveryStatus.DELIVERED, delivered.deliveryStatus)
        assertEquals(37000L, delivered.total)
        assertEquals(created.lines, delivered.lines)

        vm.syncSession(SessionState.Unauthenticated)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.sales.isEmpty())
        collectJob.cancel()
    }

    @Test
    fun `repeated screen navigation does not accumulate active listeners`() = runTest {
        val repo = FakeSaleRepository()
        val vm = SaleViewModel(repo, fixedClock)
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

    private class FakeSaleRepository : SaleRepository {
        private val flow = MutableStateFlow<List<ObservedSale>>(emptyList())
        val createdIds = mutableListOf<String>()
        var activeListeners = 0

        override fun observeSales(): Flow<List<ObservedSale>> = callbackFlow {
            activeListeners++
            val job = launch {
                flow.collect { trySend(it) }
            }
            awaitClose {
                activeListeners--
                job.cancel()
            }
        }

        override fun createPendingSale(
            saleId: String,
            fechaOperativa: LocalDate,
            businessStartDate: LocalDate,
            classification: EggClassification,
            buckets: Long,
            activePrice: Price,
            note: String?,
            onWriteError: (String) -> Unit,
        ) {
            createdIds.add(saleId)
            val sale = Sale.createSingleLinePending(
                SingleLineSaleRequest(
                    id = saleId,
                    fechaOperativa = fechaOperativa,
                    businessStartDate = businessStartDate,
                    createdAt = Instant.parse("2026-11-21T15:00:00Z"),
                    createdBy = "operator_1",
                    classification = classification,
                    buckets = buckets,
                    activePrice = activePrice,
                    note = note,
                ),
            )
            flow.value = listOf(ObservedSale(sale = sale, isPendingWrite = false)) + flow.value
        }

        override fun markDelivered(saleId: String, onWriteError: (String) -> Unit) {
            flow.value = flow.value.map { observed ->
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
