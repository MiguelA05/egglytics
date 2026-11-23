package com.egglytics.data.repository

import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

enum class SubmissionKind {
    HARVEST,
    SALE_WITH_PAYMENT,
}

enum class SubmissionSyncStatus {
    PENDING,
    CONFIRMED,
    REJECTED,
    UNCONFIRMED,
}

data class SubmissionReceipt(
    val submissionId: String,
    val uid: String,
    val businessId: String,
    val kind: SubmissionKind,
    val primaryDocId: String,
    val secondaryDocId: String? = null,
    val fechaOperativa: LocalDate,
    val summaryText: String,
    val createdAtEpochMs: Long,
    val status: SubmissionSyncStatus = SubmissionSyncStatus.PENDING,
    val statusDetail: String? = null,
)

interface SubmissionReceiptStore {
    fun observeReceipts(uid: String, businessId: String): Flow<List<SubmissionReceipt>>

    fun getReceipts(uid: String, businessId: String): List<SubmissionReceipt>

    fun upsert(receipt: SubmissionReceipt)

    fun updateStatus(
        uid: String,
        businessId: String,
        submissionId: String,
        status: SubmissionSyncStatus,
        statusDetail: String? = null,
    )

    fun remove(uid: String, businessId: String, submissionId: String)
}
