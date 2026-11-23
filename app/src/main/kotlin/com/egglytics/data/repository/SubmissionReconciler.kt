package com.egglytics.data.repository

sealed interface RemoteVerificationOutcome {
    data object Confirmed : RemoteVerificationOutcome
    data object StillPendingLocally : RemoteVerificationOutcome
    data class RejectedAfterSync(val reason: String) : RemoteVerificationOutcome
    data class Unconfirmed(val detail: String) : RemoteVerificationOutcome
}

fun interface RemoteSubmissionVerifier {
    suspend fun verify(receipt: SubmissionReceipt): RemoteVerificationOutcome
}

class SubmissionReconciler(
    private val store: SubmissionReceiptStore,
    private val verifier: RemoteSubmissionVerifier,
) {
    suspend fun reconcile(uid: String, businessId: String) {
        val candidates = store.getReceipts(uid, businessId).filter {
            it.status == SubmissionSyncStatus.PENDING || it.status == SubmissionSyncStatus.UNCONFIRMED
        }
        for (receipt in candidates) {
            when (val outcome = verifier.verify(receipt)) {
                RemoteVerificationOutcome.Confirmed -> store.updateStatus(
                    uid = uid,
                    businessId = businessId,
                    submissionId = receipt.submissionId,
                    status = SubmissionSyncStatus.CONFIRMED,
                    statusDetail = null,
                )

                RemoteVerificationOutcome.StillPendingLocally -> store.updateStatus(
                    uid = uid,
                    businessId = businessId,
                    submissionId = receipt.submissionId,
                    status = SubmissionSyncStatus.PENDING,
                    statusDetail = "Pendiente de sincronización con el servidor.",
                )

                is RemoteVerificationOutcome.RejectedAfterSync -> store.updateStatus(
                    uid = uid,
                    businessId = businessId,
                    submissionId = receipt.submissionId,
                    status = SubmissionSyncStatus.REJECTED,
                    statusDetail = outcome.reason,
                )

                is RemoteVerificationOutcome.Unconfirmed -> store.updateStatus(
                    uid = uid,
                    businessId = businessId,
                    submissionId = receipt.submissionId,
                    status = SubmissionSyncStatus.UNCONFIRMED,
                    statusDetail = outcome.detail,
                )
            }
        }
    }
}
