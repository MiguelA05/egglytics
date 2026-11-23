package com.egglytics.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class SubmissionReconcilerTest {

    private val receipt = SubmissionReceipt(
        submissionId = "s_1",
        uid = "op_1",
        businessId = "egglytics_default",
        kind = SubmissionKind.SALE_WITH_PAYMENT,
        primaryDocId = "s_1",
        secondaryDocId = "p_1",
        fechaOperativa = LocalDate.of(2026, 11, 23),
        summaryText = "Venta 2026-11-23 · AA 2 cubetas (37000)",
        createdAtEpochMs = 1_000L,
        status = SubmissionSyncStatus.PENDING,
    )

    @Test
    fun `distinguishes pending, confirmed, rejected and unconfirmed without false rejection when offline`() = runTest {
        val store = InMemoryReceiptStore().apply { upsert(receipt) }

        val offlineReconciler = SubmissionReconciler(store) {
            RemoteVerificationOutcome.Unconfirmed("Sin conexión")
        }
        offlineReconciler.reconcile("op_1", "egglytics_default")
        assertEquals(SubmissionSyncStatus.UNCONFIRMED, store.getReceipts("op_1", "egglytics_default").single().status)

        val stillPendingReconciler = SubmissionReconciler(store) {
            RemoteVerificationOutcome.StillPendingLocally
        }
        stillPendingReconciler.reconcile("op_1", "egglytics_default")
        assertEquals(SubmissionSyncStatus.PENDING, store.getReceipts("op_1", "egglytics_default").single().status)

        val rejectedReconciler = SubmissionReconciler(store) {
            RemoteVerificationOutcome.RejectedAfterSync("Rechazado tras drenar cola")
        }
        rejectedReconciler.reconcile("op_1", "egglytics_default")
        val afterReject = store.getReceipts("op_1", "egglytics_default").single()
        assertEquals(SubmissionSyncStatus.REJECTED, afterReject.status)
        assertEquals("Rechazado tras drenar cola", afterReject.statusDetail)

        // Confirmed receipt is not overwritten by subsequent reconciles
        val store2 = InMemoryReceiptStore().apply { upsert(receipt.copy(submissionId = "s_2", primaryDocId = "s_2")) }
        val confirmedReconciler = SubmissionReconciler(store2) { RemoteVerificationOutcome.Confirmed }
        confirmedReconciler.reconcile("op_1", "egglytics_default")
        assertEquals(SubmissionSyncStatus.CONFIRMED, store2.getReceipts("op_1", "egglytics_default").single().status)
    }

    private class InMemoryReceiptStore : SubmissionReceiptStore {
        private val flow = MutableStateFlow<List<SubmissionReceipt>>(emptyList())

        override fun observeReceipts(uid: String, businessId: String): Flow<List<SubmissionReceipt>> =
            flow.map { list -> list.filter { it.uid == uid && it.businessId == businessId } }

        override fun getReceipts(uid: String, businessId: String): List<SubmissionReceipt> =
            flow.value.filter { it.uid == uid && it.businessId == businessId }

        override fun upsert(receipt: SubmissionReceipt) {
            flow.value = flow.value.filterNot {
                it.uid == receipt.uid && it.businessId == receipt.businessId && it.submissionId == receipt.submissionId
            } + receipt
        }

        override fun updateStatus(
            uid: String,
            businessId: String,
            submissionId: String,
            status: SubmissionSyncStatus,
            statusDetail: String?,
        ) {
            flow.value = flow.value.map {
                if (it.uid == uid && it.businessId == businessId && it.submissionId == submissionId) {
                    it.copy(status = status, statusDetail = statusDetail)
                } else {
                    it
                }
            }
        }

        override fun remove(uid: String, businessId: String, submissionId: String) {
            flow.value = flow.value.filterNot {
                it.uid == uid && it.businessId == businessId && it.submissionId == submissionId
            }
        }
    }
}
