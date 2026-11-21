package com.egglytics.ui

import com.egglytics.data.model.Business
import com.egglytics.data.repository.HarvestRepository
import com.egglytics.data.repository.ObservedHarvest
import com.egglytics.data.repository.ObservedSale
import com.egglytics.data.repository.PriceRepository
import com.egglytics.data.repository.RejectionReason
import com.egglytics.data.repository.SaleRepository
import com.egglytics.data.repository.SessionRepository
import com.egglytics.data.repository.SessionState
import com.egglytics.domain.model.DeliveryStatus
import com.egglytics.domain.model.EggClassification
import com.egglytics.domain.model.Harvest
import com.egglytics.domain.model.Member
import com.egglytics.domain.model.MemberRole
import com.egglytics.domain.model.OperationalDate
import com.egglytics.domain.model.Price
import com.egglytics.domain.model.Sale
import com.egglytics.domain.model.SingleLineSaleRequest
import com.egglytics.domain.usecase.ActivePriceResult
import com.egglytics.domain.usecase.GetActivePriceUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
class MainViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val fixedClock = Clock.fixed(Instant.parse("2026-11-21T15:00:00Z"), OperationalDate.BOGOTA_ZONE)

    private val business = Business(
        id = "egglytics_default",
        name = "Granja Familiar",
        currency = "COP",
        timezone = "America/Bogota",
        startDate = LocalDate.of(2026, 11, 1),
        active = true,
    )

    private val operatorMember = Member(
        uid = "operator_1",
        name = "Madre",
        email = "operador@example.com",
        role = MemberRole.OPERATOR,
        active = true,
        createdAt = Instant.parse("2026-11-01T12:00:00Z"),
    )

    private val activeAaPrice = Price(
        id = "p_aa_new",
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
    fun `operator login loads business and resolves active prices per classification`() = runTest {
        val sessionRepo = FakeSessionRepository(
            signInHandler = { email, _ ->
                if (email == "operador@example.com") {
                    SessionState.Authenticated("operator_1", email, operatorMember, business)
                } else {
                    SessionState.Rejected(RejectionReason.NO_MEMBERSHIP, "Sin membresía")
                }
            },
        )
        val priceRepo = FakePriceRepository(
            listOf(
                Price(
                    id = "p_aa_old",
                    classification = EggClassification.AA,
                    pricePerBucket = 17000L,
                    validFrom = LocalDate.of(2026, 11, 1),
                    createdAt = Instant.parse("2026-11-01T10:00:00Z"),
                    createdBy = "admin_1",
                ),
                activeAaPrice,
                Price(
                    id = "p_a_future",
                    classification = EggClassification.A,
                    pricePerBucket = 19000L,
                    validFrom = LocalDate.of(2026, 11, 25),
                    createdAt = Instant.parse("2026-11-20T10:00:00Z"),
                    createdBy = "admin_1",
                ),
            ),
        )
        val vm = MainViewModel(
            sessionRepo,
            priceRepo,
            FakeHarvestRepository(),
            FakeSaleRepository(),
            GetActivePriceUseCase(),
            fixedClock,
        )
        advanceUntilIdle()

        vm.onEmailChange("operador@example.com")
        vm.onPasswordChange("secret123")
        vm.signIn()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.sessionState is SessionState.Authenticated)
        assertEquals(ActivePriceResult.Found(activeAaPrice), state.activePrices[EggClassification.AA])
        assertEquals(ActivePriceResult.NotFound, state.activePrices[EggClassification.A])
        assertEquals(ActivePriceResult.NotFound, state.activePrices[EggClassification.B])
    }

    @Test
    fun `user without membership is rejected and does not load prices`() = runTest {
        val sessionRepo = FakeSessionRepository(
            signInHandler = { _, _ ->
                SessionState.Rejected(RejectionReason.NO_MEMBERSHIP, "Sin membresía")
            },
        )
        val vm = MainViewModel(
            sessionRepo,
            FakePriceRepository(emptyList()),
            FakeHarvestRepository(),
            FakeSaleRepository(),
            GetActivePriceUseCase(),
            fixedClock,
        )
        advanceUntilIdle()

        vm.onEmailChange("stranger@example.com")
        vm.onPasswordChange("secret123")
        vm.signIn()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.sessionState is SessionState.Rejected)
        assertEquals(RejectionReason.NO_MEMBERSHIP, (state.sessionState as SessionState.Rejected).reason)
        assertTrue(state.activePrices.isEmpty())
    }

    @Test
    fun `submitHarvest validates quantities and distinguishes pending vs confirmed`() = runTest {
        val sessionRepo = FakeSessionRepository(
            initial = SessionState.Authenticated("operator_1", "operador@example.com", operatorMember, business),
        )
        val harvestRepo = FakeHarvestRepository()

        val vm = MainViewModel(
            sessionRepo,
            FakePriceRepository(emptyList()),
            harvestRepo,
            FakeSaleRepository(),
            GetActivePriceUseCase(),
            fixedClock,
        )
        advanceUntilIdle()

        vm.submitHarvest("h_empty")
        assertNotNull(vm.uiState.value.harvestError)
        assertTrue(harvestRepo.recordedIds.isEmpty())

        vm.onHarvestQtyChange(EggClassification.AA, "90")
        vm.onHarvestQtyChange(EggClassification.A, "30")
        vm.onDiscardedChange("4")
        vm.onHarvestNoteChange("Recolección tarde")
        vm.submitHarvest("h_custom_1")
        advanceUntilIdle()

        assertNull(vm.uiState.value.harvestError)
        assertEquals(listOf("h_custom_1"), harvestRepo.recordedIds)
        assertEquals(1, vm.uiState.value.harvests.size)
        assertTrue(vm.uiState.value.harvests.first().isPendingWrite)

        harvestRepo.markConfirmed("h_custom_1")
        advanceUntilIdle()
        assertFalse(vm.uiState.value.harvests.first().isPendingWrite)
    }

    @Test
    fun `submitSale creates pending sale with active price and markSaleDelivered updates status`() = runTest {
        val sessionRepo = FakeSessionRepository(
            initial = SessionState.Authenticated("operator_1", "operador@example.com", operatorMember, business),
        )
        val saleRepo = FakeSaleRepository()

        val vm = MainViewModel(
            sessionRepo,
            FakePriceRepository(listOf(activeAaPrice)),
            FakeHarvestRepository(),
            saleRepo,
            GetActivePriceUseCase(),
            fixedClock,
        )
        advanceUntilIdle()

        // Sin precio vigente para B -> rechazada
        vm.onSaleClassificationChange(EggClassification.B)
        vm.onSaleBucketsChange("2")
        vm.submitSale("sale_no_price")
        assertNotNull(vm.uiState.value.saleError)
        assertTrue(saleRepo.createdIds.isEmpty())

        // Con AA (18500) y 2 cubetas -> 37000 pending, luego entregada
        vm.onSaleClassificationChange(EggClassification.AA)
        vm.onSaleBucketsChange("2")
        vm.onSaleNoteChange("Tienda esquina")
        vm.submitSale("sale_aa_1")
        advanceUntilIdle()

        assertNull(vm.uiState.value.saleError)
        assertEquals(listOf("sale_aa_1"), saleRepo.createdIds)
        val created = vm.uiState.value.sales.first().sale
        assertEquals(DeliveryStatus.PENDING, created.deliveryStatus)
        assertEquals(37000L, created.total)
        assertEquals(60L, created.lines.first().eggs)

        vm.markSaleDelivered("sale_aa_1")
        advanceUntilIdle()
        val delivered = vm.uiState.value.sales.first().sale
        assertEquals(DeliveryStatus.DELIVERED, delivered.deliveryStatus)
        assertEquals(37000L, delivered.total)
        assertEquals(created.lines, delivered.lines)
    }

    @Test
    fun `signOut resets session to Unauthenticated`() = runTest {
        val sessionRepo = FakeSessionRepository(
            initial = SessionState.Authenticated("operator_1", "operador@example.com", operatorMember, business),
        )
        val vm = MainViewModel(
            sessionRepo,
            FakePriceRepository(emptyList()),
            FakeHarvestRepository(),
            FakeSaleRepository(),
            GetActivePriceUseCase(),
            fixedClock,
        )
        advanceUntilIdle()
        assertTrue(vm.uiState.value.sessionState is SessionState.Authenticated)

        vm.signOut()
        advanceUntilIdle()
        assertEquals(SessionState.Unauthenticated, vm.uiState.value.sessionState)
        assertTrue(sessionRepo.signedOut)
    }

    private class FakeSessionRepository(
        initial: SessionState = SessionState.Unauthenticated,
        private val signInHandler: suspend (String, String) -> SessionState = { _, _ -> initial },
    ) : SessionRepository {
        private var current: SessionState = initial
        var signedOut = false

        override suspend fun signIn(email: String, password: String): SessionState {
            current = signInHandler(email, password)
            return current
        }

        override suspend fun loadCurrentSession(): SessionState = current

        override suspend fun signOut() {
            signedOut = true
            current = SessionState.Unauthenticated
        }
    }

    private class FakePriceRepository(initial: List<Price>) : PriceRepository {
        private val flow = MutableStateFlow(initial)
        override fun observePrices(): Flow<List<Price>> = flow
        override suspend fun getPrices(): List<Price> = flow.value
    }

    private class FakeHarvestRepository : HarvestRepository {
        private val flow = MutableStateFlow<List<ObservedHarvest>>(emptyList())
        val recordedIds = mutableListOf<String>()

        override fun observeHarvests(): Flow<List<ObservedHarvest>> = flow

        override fun recordHarvest(
            harvestId: String,
            fechaOperativa: LocalDate,
            graded: Map<EggClassification, Long>,
            discarded: Long,
            note: String?,
            onWriteError: (String) -> Unit,
        ) {
            recordedIds.add(harvestId)
            val harvest = Harvest(
                id = harvestId,
                fechaOperativa = fechaOperativa,
                createdAt = Instant.parse("2026-11-21T15:00:00Z"),
                createdBy = "operator_1",
                graded = graded,
                discarded = discarded,
                note = note,
            )
            flow.value = listOf(ObservedHarvest(harvest = harvest, isPendingWrite = true)) + flow.value
        }

        fun markConfirmed(harvestId: String) {
            flow.value = flow.value.map {
                if (it.harvest.id == harvestId) it.copy(isPendingWrite = false) else it
            }
        }
    }

    private class FakeSaleRepository : SaleRepository {
        private val flow = MutableStateFlow<List<ObservedSale>>(emptyList())
        val createdIds = mutableListOf<String>()

        override fun observeSales(): Flow<List<ObservedSale>> = flow

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
