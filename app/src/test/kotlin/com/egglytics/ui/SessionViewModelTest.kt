package com.egglytics.ui

import com.egglytics.data.model.Business
import com.egglytics.data.repository.PriceRepository
import com.egglytics.data.repository.RejectionReason
import com.egglytics.data.repository.SessionRepository
import com.egglytics.data.repository.SessionState
import com.egglytics.domain.model.EggClassification
import com.egglytics.domain.model.Member
import com.egglytics.domain.model.MemberRole
import com.egglytics.domain.model.OperationalDate
import com.egglytics.domain.model.Price
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class SessionViewModelTest {

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
    fun `operator login loads business, active prices and starts at HOME`() = runTest {
        val sessionRepo = FakeSessionRepository(
            signInHandler = { email, _ ->
                if (email == "operador@example.com") {
                    SessionState.Authenticated("operator_1", email, operatorMember, business)
                } else {
                    SessionState.Rejected(RejectionReason.NO_MEMBERSHIP, "Sin membresía")
                }
            },
        )
        val priceRepo = FakePriceRepository(listOf(activeAaPrice))

        val vm = SessionViewModel(sessionRepo, priceRepo, GetActivePriceUseCase(), fixedClock)
        advanceUntilIdle()

        vm.onEmailChange("operador@example.com")
        vm.onPasswordChange("secret123")
        vm.signIn()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.sessionState is SessionState.Authenticated)
        assertEquals(AppDestination.HOME, state.destination)
        assertEquals(ActivePriceResult.Found(activeAaPrice), state.activePrices[EggClassification.AA])
        vm.editPrice(EggClassification.AA)
        vm.savePrice("22000", "2026-11-21")
        advanceUntilIdle()
        assertEquals(null, vm.uiState.value.priceEditor)
        assertEquals(listOf(activeAaPrice), priceRepo.getPrices())
    }

    @Test
    fun `user without membership is rejected and does not load prices`() = runTest {
        val sessionRepo = FakeSessionRepository(
            signInHandler = { _, _ ->
                SessionState.Rejected(RejectionReason.NO_MEMBERSHIP, "Sin membresía")
            },
        )
        val vm = SessionViewModel(
            sessionRepo,
            FakePriceRepository(listOf(activeAaPrice)),
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
    fun `navigation switches between HOME, HARVESTS, SALES and signOut resets state`() = runTest {
        val sessionRepo = FakeSessionRepository(
            initial = SessionState.Authenticated("operator_1", "operador@example.com", operatorMember, business),
        )
        val vm = SessionViewModel(
            sessionRepo,
            FakePriceRepository(listOf(activeAaPrice)),
            GetActivePriceUseCase(),
            fixedClock,
        )
        advanceUntilIdle()

        vm.navigateTo(AppDestination.HARVESTS)
        assertEquals(AppDestination.HARVESTS, vm.uiState.value.destination)
        vm.navigateHome()
        assertEquals(AppDestination.HOME, vm.uiState.value.destination)
        vm.navigateTo(AppDestination.SALES)
        assertEquals(AppDestination.SALES, vm.uiState.value.destination)

        vm.signOut()
        advanceUntilIdle()
        assertEquals(SessionState.Unauthenticated, vm.uiState.value.sessionState)
        assertEquals(AppDestination.HOME, vm.uiState.value.destination)
        assertTrue(vm.uiState.value.activePrices.isEmpty())
        assertTrue(sessionRepo.signedOut)
    }

    @Test
    fun `admin adds dated prices and invalid amounts create no version`() = runTest {
        val admin = operatorMember.copy(role = MemberRole.ADMIN)
        val repo = FakePriceRepository(listOf(activeAaPrice))
        val vm = SessionViewModel(FakeSessionRepository(SessionState.Authenticated(admin.uid, admin.email, admin, business)),
            repo, GetActivePriceUseCase(), fixedClock)
        advanceUntilIdle()
        vm.editPrice(EggClassification.AA)
        vm.savePrice("0", "2026-11-21")
        assertTrue(vm.uiState.value.priceEditor?.error != null)
        assertEquals(1, repo.getPrices().size)
        vm.savePrice("21000", "2026-11-21")
        advanceUntilIdle()
        vm.editPrice(EggClassification.AA)
        vm.savePrice("24000", "2026-12-01")
        advanceUntilIdle()
        val catalog = vm.uiState.value.catalog
        assertEquals(3, catalog.size)
        assertEquals(admin.uid, catalog.last().createdBy)
        assertEquals(21000L, (vm.uiState.value.activePrices[EggClassification.AA] as ActivePriceResult.Found).price.pricePerBucket)
        val historic = GetActivePriceUseCase()(catalog, EggClassification.AA, LocalDate.parse("2026-11-20"))
        assertEquals(ActivePriceResult.Found(activeAaPrice), historic)
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
        override suspend fun addPrice(classification: EggClassification, amount: Long, validFrom: LocalDate, uid: String) {
            flow.value += Price("new", classification, amount, validFrom, Instant.parse("2026-11-21T15:00:00Z"), uid)
        }
        override fun observePrices(): Flow<List<Price>> = flow
        override suspend fun getPrices(): List<Price> = flow.value
    }
}
