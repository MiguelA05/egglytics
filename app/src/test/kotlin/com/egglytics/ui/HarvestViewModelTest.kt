package com.egglytics.ui

import com.egglytics.data.model.Business
import com.egglytics.data.repository.HarvestRepository
import com.egglytics.data.repository.ObservedHarvest
import com.egglytics.data.repository.ObservedReversal
import com.egglytics.data.repository.ReversalRepository
import com.egglytics.data.repository.SessionState
import com.egglytics.domain.model.EggClassification
import com.egglytics.domain.model.Harvest
import com.egglytics.domain.model.Member
import com.egglytics.domain.model.MemberRole
import com.egglytics.domain.model.OperationalDate
import com.egglytics.domain.model.Payment
import com.egglytics.domain.model.Sale
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
class HarvestViewModelTest {

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

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `submitHarvest validates quantities, records harvest and clears on signOut`() = runTest {
        val repo = FakeHarvestRepository()
        val vm = HarvestViewModel(repo, FakeReversalRepository(), fixedClock)
        vm.syncSession(authSession)
        val collectJob = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.submitHarvest(authSession, "h_empty")
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.form.harvestError)
        assertTrue(repo.recordedIds.isEmpty())

        vm.onHarvestQtyChange(EggClassification.AA, "90")
        vm.onDiscardedChange("4")
        vm.onHarvestNoteChange("Recolección tarde")
        vm.submitHarvest(authSession, "h_1")
        advanceUntilIdle()

        assertNull(vm.uiState.value.form.harvestError)
        assertEquals(listOf("h_1"), repo.recordedIds)
        assertTrue(vm.uiState.value.harvests.first().isPendingWrite)

        repo.markConfirmed("h_1")
        advanceUntilIdle()
        assertFalse(vm.uiState.value.harvests.first().isPendingWrite)

        vm.syncSession(SessionState.Unauthenticated)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.harvests.isEmpty())
        collectJob.cancel()
    }

    @Test
    fun `repeated screen navigation does not accumulate active listeners`() = runTest {
        val repo = FakeHarvestRepository()
        val vm = HarvestViewModel(repo, FakeReversalRepository(), fixedClock)
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

    private class FakeHarvestRepository : HarvestRepository {
        private val flow = MutableStateFlow<List<ObservedHarvest>>(emptyList())
        val recordedIds = mutableListOf<String>()
        var activeListeners = 0

        override fun observeHarvests(): Flow<List<ObservedHarvest>> = callbackFlow {
            activeListeners++
            val job = launch {
                flow.collect { trySend(it) }
            }
            awaitClose {
                activeListeners--
                job.cancel()
            }
        }

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
}
