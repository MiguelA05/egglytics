package com.egglytics.ui

import com.egglytics.data.model.Business
import com.egglytics.data.repository.CountSubmitRequest
import com.egglytics.data.repository.ExpenseAndWithdrawalRepository
import com.egglytics.data.repository.ExpenseRequest
import com.egglytics.data.repository.FeedDepletionRequest
import com.egglytics.data.repository.FeedLossRequest
import com.egglytics.data.repository.FeedPurchaseRequest
import com.egglytics.data.repository.FeedRepository
import com.egglytics.data.repository.ObservedExpense
import com.egglytics.data.repository.ObservedFeedDepletion
import com.egglytics.data.repository.ObservedFeedLoss
import com.egglytics.data.repository.ObservedFeedPurchase
import com.egglytics.data.repository.ObservedReversal
import com.egglytics.data.repository.ObservedSale
import com.egglytics.data.repository.ObservedSalePayment
import com.egglytics.data.repository.ObservedWithdrawal
import com.egglytics.data.repository.OpeningAndCountRepository
import com.egglytics.data.repository.OpeningEntryInput
import com.egglytics.data.repository.PaymentMethodRepository
import com.egglytics.data.repository.ReversalRepository
import com.egglytics.data.repository.SaleRepository
import com.egglytics.data.repository.SaleWithPaymentRequest
import com.egglytics.data.repository.SessionState
import com.egglytics.data.repository.WithdrawalRequest
import com.egglytics.domain.model.DailyClosing
import com.egglytics.domain.model.DeliveryStatus
import com.egglytics.domain.model.EggClassification
import com.egglytics.domain.model.Expense
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
import com.egglytics.domain.model.PaymentType
import com.egglytics.domain.model.PhysicalCount
import com.egglytics.domain.model.Sale
import com.egglytics.domain.model.SaleLine
import com.egglytics.domain.model.SaleReturnSummary
import com.egglytics.domain.model.Withdrawal
import com.egglytics.domain.usecase.GetCashAndResultUseCase
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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class CountsAndOpeningViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val fixedClock = Clock.fixed(Instant.parse("2026-11-23T15:00:00Z"), OperationalDate.BOGOTA_ZONE)
    private val today = LocalDate.of(2026, 11, 23)

    private val adminSession = SessionState.Authenticated(
        uid = "admin_1",
        email = "admin@example.com",
        member = Member(
            uid = "admin_1",
            name = "Admin",
            email = "admin@example.com",
            role = MemberRole.ADMIN,
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
    fun setUp() = Dispatchers.setMain(testDispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `partial closing summary includes RF-21 totals and separates operating expenses from feed purchases`() = runTest {
        val ocRepo = FakeOpeningAndCountRepository()
        val vm = CountsAndOpeningViewModel(
            openingAndCountRepository = ocRepo,
            paymentMethodRepository = FakePaymentMethodRepository(),
            feedRepository = FakeFeedRepository(
                listOf(
                    ObservedFeedPurchase(
                        FeedPurchase(
                            id = "fp_1",
                            fechaOperativa = today,
                            createdAt = Instant.parse("2026-11-23T12:00:00Z"),
                            createdBy = "admin_1",
                            ingredient = FeedIngredient.CONCENTRATE,
                            bags = 1L,
                            totalCost = 80000L,
                            methodId = "cash",
                        ),
                        false,
                    ),
                ),
            ),
            saleRepository = FakeSaleRepository(
                sales = listOf(
                    ObservedSale(
                        Sale(
                            id = "s_1",
                            fechaOperativa = today,
                            createdAt = Instant.parse("2026-11-23T11:00:00Z"),
                            createdBy = "admin_1",
                            lines = listOf(SaleLine.create(EggClassification.AA, 2L, 18500L, 18500L)),
                            total = 37000L,
                            deliveryStatus = DeliveryStatus.PENDING,
                        ),
                        false,
                    ),
                ),
                payments = mapOf(
                    "s_1" to listOf(
                        ObservedSalePayment(
                            Payment(
                                id = "p_1",
                                fechaOperativa = today,
                                createdAt = Instant.parse("2026-11-23T11:00:00Z"),
                                createdBy = "admin_1",
                                saleId = "s_1",
                                amount = 37000L,
                                methodId = "cash",
                                type = PaymentType.PAYMENT,
                            ),
                            false,
                        ),
                    ),
                ),
            ),
            expenseAndWithdrawalRepository = FakeExpenseRepo(
                expenses = listOf(
                    ObservedExpense(
                        Expense(
                            id = "e_1",
                            fechaOperativa = today,
                            createdAt = Instant.parse("2026-11-23T13:00:00Z"),
                            createdBy = "admin_1",
                            amount = 20000L,
                            categoryId = "fuel",
                            methodId = "cash",
                        ),
                        false,
                    ),
                ),
                withdrawals = listOf(
                    ObservedWithdrawal(
                        Withdrawal(
                            id = "w_1",
                            fechaOperativa = today,
                            createdAt = Instant.parse("2026-11-23T14:00:00Z"),
                            createdBy = "admin_1",
                            amount = 15000L,
                            methodId = "cash",
                        ),
                        false,
                    ),
                ),
            ),
            reversalRepository = FakeReversalRepository(),
            getFeedBalanceUseCase = GetFeedBalanceUseCase(),
            getCashAndResultUseCase = GetCashAndResultUseCase(),
            clock = fixedClock,
        )

        vm.syncSession(adminSession)
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.onIsClosingChange(true)
        vm.onCashPhysicalChange("cash", "-80000") // reject check? no, physical >= 0; expected cash = 37000 - 80000 - 20000 - 15000 = -78000
        vm.onCashPhysicalChange("cash", "1000")
        vm.onCashReasonChange("cash", "Ajuste apertura")
        vm.submit(adminSession)
        advanceUntilIdle()

        val submitted = ocRepo.lastCountSubmit
        assertNotNull(submitted)
        assertEquals(true, submitted?.isClosing)
        assertEquals(37000L, submitted?.closingSummary?.get("salesTotal"))
        assertEquals(37000L, submitted?.closingSummary?.get("collectionsTotal"))
        assertEquals(20000L, submitted?.closingSummary?.get("operatingExpensesTotal"))
        assertEquals(80000L, submitted?.closingSummary?.get("feedPurchasesTotal"))
        assertEquals(15000L, submitted?.closingSummary?.get("withdrawalsTotal"))
        assertEquals(1L, submitted?.closingSummary?.get("countedCashItemsCount"))
        assertEquals(3L, submitted?.closingSummary?.get("omittedItemsCount"))
        job.cancel()
    }

    @Test
    fun `rejected opening preserves inputs so admin can correct and retry`() = runTest {
        val ocRepo = FakeOpeningAndCountRepository().apply { failNextOpening = true }
        val vm = CountsAndOpeningViewModel(
            openingAndCountRepository = ocRepo,
            paymentMethodRepository = FakePaymentMethodRepository(),
            feedRepository = FakeFeedRepository(emptyList()),
            saleRepository = FakeSaleRepository(emptyList(), emptyMap()),
            expenseAndWithdrawalRepository = FakeExpenseRepo(emptyList(), emptyList()),
            reversalRepository = FakeReversalRepository(),
            getFeedBalanceUseCase = GetFeedBalanceUseCase(),
            getCashAndResultUseCase = GetCashAndResultUseCase(),
            clock = fixedClock,
        )

        vm.syncSession(adminSession)
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.onModeChange(CountsMode.OPENING)
        vm.onCashPhysicalChange("cash", "100000")
        vm.submit(adminSession)
        advanceUntilIdle()

        assertEquals("Rechazo simulado", vm.uiState.value.form.formError)
        assertEquals("100000", vm.uiState.value.form.cashPhysicalInputs["cash"])

        ocRepo.failNextOpening = false
        vm.onCashPhysicalChange("cash", "120000")
        vm.submit(adminSession)
        advanceUntilIdle()

        assertNull(vm.uiState.value.form.formError)
        assertEquals(1, vm.uiState.value.openings.size)
        assertEquals(120000L, vm.uiState.value.openings.first().quantity)
        job.cancel()
    }

    private class FakeOpeningAndCountRepository : OpeningAndCountRepository {
        private val openings = MutableStateFlow<List<OpeningBalance>>(emptyList())
        private val counts = MutableStateFlow<List<PhysicalCount>>(emptyList())
        private val closings = MutableStateFlow<List<DailyClosing>>(emptyList())
        var failNextOpening = false
        var lastCountSubmit: CountSubmitRequest? = null

        override fun observeOpenings(): Flow<List<OpeningBalance>> = openings
        override fun observeCounts(): Flow<List<PhysicalCount>> = counts
        override fun observeClosings(): Flow<List<DailyClosing>> = closings

        override fun recordOpenings(
            fechaOperativa: LocalDate,
            businessStartDate: LocalDate,
            entries: List<OpeningEntryInput>,
            onSuccess: () -> Unit,
            onError: (String) -> Unit,
        ) {
            if (failNextOpening) {
                onError("Rechazo simulado")
                return
            }
            openings.value = entries.map {
                OpeningBalance("ob_${it.kind.code}_${it.key}", fechaOperativa, Instant.now(), "admin_1", it.kind, it.key, it.quantity)
            }
            onSuccess()
        }

        override fun recordCountOrClosing(
            request: CountSubmitRequest,
            onSuccess: (List<String>) -> Unit,
            onError: (String) -> Unit,
        ) {
            lastCountSubmit = request
            onSuccess(listOf("c_1"))
        }
    }

    private class FakePaymentMethodRepository : PaymentMethodRepository {
        private val flow = MutableStateFlow(
            listOf(PaymentMethod("cash", "Efectivo", true), PaymentMethod("nequi", "Nequi", true)),
        )
        override fun observeActivePaymentMethods(): Flow<List<PaymentMethod>> = flow
    }

    private class FakeFeedRepository(purchases: List<ObservedFeedPurchase>) : FeedRepository {
        private val p = MutableStateFlow(purchases)
        private val d = MutableStateFlow<List<ObservedFeedDepletion>>(emptyList())
        private val l = MutableStateFlow<List<ObservedFeedLoss>>(emptyList())
        override fun observePurchases(): Flow<List<ObservedFeedPurchase>> = p
        override fun observeDepletions(): Flow<List<ObservedFeedDepletion>> = d
        override fun observeLosses(): Flow<List<ObservedFeedLoss>> = l
        override fun recordPurchase(request: FeedPurchaseRequest, onWriteError: (String) -> Unit) = Unit
        override fun recordDepletion(request: FeedDepletionRequest, onWriteError: (String) -> Unit) = Unit
        override fun recordLoss(request: FeedLossRequest, onWriteError: (String) -> Unit) = Unit
    }

    private class FakeSaleRepository(
        sales: List<ObservedSale>,
        payments: Map<String, List<ObservedSalePayment>>,
    ) : SaleRepository {
        private val s = MutableStateFlow(sales)
        private val p = MutableStateFlow(payments)
        private val rawP = MutableStateFlow(payments.mapValues { (_, list) -> list.map { it.payment } })
        override fun observeSales(): Flow<List<ObservedSale>> = s
        override fun observeSalePayments(): Flow<Map<String, List<Payment>>> = rawP
        override fun observeAllSalePayments(): Flow<Map<String, List<ObservedSalePayment>>> = p
        override fun createPendingSaleWithFullPayment(request: SaleWithPaymentRequest, onWriteError: (String) -> Unit) = Unit
        override fun markDelivered(saleId: String, onWriteError: (String) -> Unit) = Unit
    }

    private class FakeExpenseRepo(
        expenses: List<ObservedExpense>,
        withdrawals: List<ObservedWithdrawal>,
    ) : ExpenseAndWithdrawalRepository {
        private val e = MutableStateFlow(expenses)
        private val w = MutableStateFlow(withdrawals)
        private val r = MutableStateFlow<List<SaleReturnSummary>>(emptyList())
        override fun observeExpenses(): Flow<List<ObservedExpense>> = e
        override fun observeWithdrawals(): Flow<List<ObservedWithdrawal>> = w
        override fun observeSaleReturns(): Flow<List<SaleReturnSummary>> = r
        override fun recordExpense(request: ExpenseRequest, onWriteError: (String) -> Unit) = Unit
        override fun recordWithdrawal(request: WithdrawalRequest, onWriteError: (String) -> Unit) = Unit
    }

    private class FakeReversalRepository : ReversalRepository {
        private val flow = MutableStateFlow<List<ObservedReversal>>(emptyList())
        override fun observeReversals(): Flow<List<ObservedReversal>> = flow
        override fun reverseHarvest(harvest: Harvest, businessStartDate: LocalDate, reason: String, onSuccess: () -> Unit, onError: (String) -> Unit) = Unit
        override fun reverseSaleWithPayment(sale: Sale, payment: Payment, businessStartDate: LocalDate, reason: String, onSuccess: () -> Unit, onError: (String) -> Unit) = Unit
    }
}
