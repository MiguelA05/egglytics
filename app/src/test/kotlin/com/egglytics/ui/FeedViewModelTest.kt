package com.egglytics.ui

import com.egglytics.data.model.Business
import com.egglytics.data.repository.CountSubmitRequest
import com.egglytics.data.repository.FeedDepletionRequest
import com.egglytics.data.repository.FeedLossRequest
import com.egglytics.data.repository.FeedPurchaseRequest
import com.egglytics.data.repository.FeedRepository
import com.egglytics.data.repository.ObservedFeedDepletion
import com.egglytics.data.repository.ObservedFeedLoss
import com.egglytics.data.repository.ObservedFeedPurchase
import com.egglytics.data.repository.ObservedReversal
import com.egglytics.data.repository.OpeningAndCountRepository
import com.egglytics.data.repository.OpeningEntryInput
import com.egglytics.data.repository.PaymentMethodRepository
import com.egglytics.data.repository.ReversalRepository
import com.egglytics.data.repository.SessionState
import com.egglytics.domain.model.DailyClosing
import com.egglytics.domain.model.FeedDepletion
import com.egglytics.domain.model.FeedIngredient
import com.egglytics.domain.model.FeedLoss
import com.egglytics.domain.model.FeedPurchase
import com.egglytics.domain.model.Harvest
import com.egglytics.domain.model.Member
import com.egglytics.domain.model.MemberRole
import com.egglytics.domain.model.OpeningBalance
import com.egglytics.domain.model.OperationalDate
import com.egglytics.domain.model.Payment
import com.egglytics.domain.model.PaymentMethod
import com.egglytics.domain.model.PhysicalCount
import com.egglytics.domain.model.Sale
import com.egglytics.domain.usecase.GetFeedBalanceUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class FeedViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val fixedClock = Clock.fixed(Instant.parse("2026-11-23T15:00:00Z"), OperationalDate.BOGOTA_ZONE)

    private val authSession = SessionState.Authenticated(
        uid = "op_1",
        email = "op@example.com",
        member = Member(
            uid = "op_1",
            name = "Operador",
            email = "op@example.com",
            role = MemberRole.OPERATOR,
            active = true,
            createdAt = Instant.parse("2026-11-01T12:00:00Z"),
        ),
        business = Business(
            id = "egglytics_default",
            name = "Huevos Frescos",
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
    fun `records purchase, depletion and loss and updates expected kg balance`() = runTest {
        val feedRepo = FakeFeedRepository()
        val vm = FeedViewModel(
            feedRepo,
            FakePaymentMethodRepository(),
            FakeOpeningAndCountRepository(),
            FakeReversalRepository(),
            GetFeedBalanceUseCase(),
            fixedClock,
        )
        vm.syncSession(authSession)
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        // 3 bags concentrate (+120 kg)
        vm.onBagsChange("3")
        vm.onTotalCostChange("240000")
        vm.submit(authSession, "fp_1")
        advanceUntilIdle()
        assertEquals(120L, vm.uiState.value.summaries[FeedIngredient.CONCENTRATE]?.balanceKg)

        // 1 bag depletion (-40 kg)
        vm.onModeChange(FeedFormMode.DEPLETION)
        vm.onBagsChange("1")
        vm.submit(authSession, "fd_1")
        advanceUntilIdle()
        assertEquals(80L, vm.uiState.value.summaries[FeedIngredient.CONCENTRATE]?.balanceKg)

        // Loss without reason is rejected
        vm.onModeChange(FeedFormMode.LOSS)
        vm.onLossKgChange("5")
        vm.submit(authSession, "fl_bad")
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.form.formError)

        // Loss with reason (-5 kg)
        vm.onLossReasonChange("Humedad")
        vm.submit(authSession, "fl_1")
        advanceUntilIdle()
        assertEquals(75L, vm.uiState.value.summaries[FeedIngredient.CONCENTRATE]?.balanceKg)

        job.cancel()
    }

    private class FakeOpeningAndCountRepository : OpeningAndCountRepository {
        private val openings = MutableStateFlow<List<OpeningBalance>>(emptyList())
        private val counts = MutableStateFlow<List<PhysicalCount>>(emptyList())
        private val closings = MutableStateFlow<List<DailyClosing>>(emptyList())
        override fun observeOpenings(): Flow<List<OpeningBalance>> = openings
        override fun observeCounts(): Flow<List<PhysicalCount>> = counts
        override fun observeClosings(): Flow<List<DailyClosing>> = closings
        override fun recordOpenings(
            fechaOperativa: LocalDate,
            businessStartDate: LocalDate,
            entries: List<OpeningEntryInput>,
            onSuccess: () -> Unit,
            onError: (String) -> Unit,
        ) = Unit

        override fun recordCountOrClosing(
            request: CountSubmitRequest,
            onSuccess: (List<String>) -> Unit,
            onError: (String) -> Unit,
        ) = Unit
    }

    private class FakePaymentMethodRepository : PaymentMethodRepository {
        private val flow = MutableStateFlow(listOf(PaymentMethod("cash", "Efectivo", true)))
        override fun observeActivePaymentMethods(): Flow<List<PaymentMethod>> = flow
    }

    private class FakeReversalRepository : ReversalRepository {
        private val flow = MutableStateFlow<List<ObservedReversal>>(emptyList())
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

    private class FakeFeedRepository : FeedRepository {
        private val purchases = MutableStateFlow<List<ObservedFeedPurchase>>(emptyList())
        private val depletions = MutableStateFlow<List<ObservedFeedDepletion>>(emptyList())
        private val losses = MutableStateFlow<List<ObservedFeedLoss>>(emptyList())

        override fun observePurchases(): Flow<List<ObservedFeedPurchase>> = purchases
        override fun observeDepletions(): Flow<List<ObservedFeedDepletion>> = depletions
        override fun observeLosses(): Flow<List<ObservedFeedLoss>> = losses

        override fun recordPurchase(request: FeedPurchaseRequest, onWriteError: (String) -> Unit) {
            val item = FeedPurchase(
                id = request.id,
                fechaOperativa = request.fechaOperativa,
                createdAt = Instant.parse("2026-11-23T15:00:00Z"),
                createdBy = "op_1",
                ingredient = request.ingredient,
                bags = request.bags,
                totalCost = request.totalCost,
                methodId = request.methodId,
                supplier = request.supplier,
                note = request.note,
            )
            purchases.value = listOf(ObservedFeedPurchase(item, true)) + purchases.value
        }

        override fun recordDepletion(request: FeedDepletionRequest, onWriteError: (String) -> Unit) {
            val item = FeedDepletion(
                id = request.id,
                fechaOperativa = request.fechaOperativa,
                createdAt = Instant.parse("2026-11-23T15:00:00Z"),
                createdBy = "op_1",
                ingredient = request.ingredient,
                bags = request.bags,
                note = request.note,
            )
            depletions.value = listOf(ObservedFeedDepletion(item, true)) + depletions.value
        }

        override fun recordLoss(request: FeedLossRequest, onWriteError: (String) -> Unit) {
            val item = FeedLoss(
                id = request.id,
                fechaOperativa = request.fechaOperativa,
                createdAt = Instant.parse("2026-11-23T15:00:00Z"),
                createdBy = "op_1",
                ingredient = request.ingredient,
                kg = request.kg,
                type = request.type,
                reason = request.reason,
                note = request.note,
            )
            losses.value = listOf(ObservedFeedLoss(item, true)) + losses.value
        }
    }
}
