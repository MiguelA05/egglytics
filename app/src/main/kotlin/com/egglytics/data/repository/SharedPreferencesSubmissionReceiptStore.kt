package com.egglytics.data.repository

import android.content.Context
import com.egglytics.domain.model.OperationalDate
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SharedPreferencesSubmissionReceiptStore @Inject constructor(
    @ApplicationContext context: Context,
) : SubmissionReceiptStore {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val lock = Any()
    private val allReceipts = MutableStateFlow(loadAllLocked())

    override fun observeReceipts(uid: String, businessId: String): Flow<List<SubmissionReceipt>> =
        allReceipts.map { list ->
            list.filter { it.uid == uid && it.businessId == businessId }
                .sortedByDescending { it.createdAtEpochMs }
        }

    override fun getReceipts(uid: String, businessId: String): List<SubmissionReceipt> =
        allReceipts.value
            .filter { it.uid == uid && it.businessId == businessId }
            .sortedByDescending { it.createdAtEpochMs }

    override fun upsert(receipt: SubmissionReceipt) {
        synchronized(lock) {
            val updated = allReceipts.value
                .filterNot {
                    it.uid == receipt.uid &&
                        it.businessId == receipt.businessId &&
                        it.submissionId == receipt.submissionId
                } + receipt
            persistLocked(updated)
        }
    }

    override fun updateStatus(
        uid: String,
        businessId: String,
        submissionId: String,
        status: SubmissionSyncStatus,
        statusDetail: String?,
    ) {
        synchronized(lock) {
            val updated = allReceipts.value.map { item ->
                if (item.uid == uid && item.businessId == businessId && item.submissionId == submissionId) {
                    item.copy(status = status, statusDetail = statusDetail)
                } else {
                    item
                }
            }
            persistLocked(updated)
        }
    }

    override fun remove(uid: String, businessId: String, submissionId: String) {
        synchronized(lock) {
            val updated = allReceipts.value.filterNot {
                it.uid == uid && it.businessId == businessId && it.submissionId == submissionId
            }
            persistLocked(updated)
        }
    }

    private fun persistLocked(items: List<SubmissionReceipt>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(item.toJson())
        }
        prefs.edit().putString(KEY_RECEIPTS, array.toString()).commit()
        allReceipts.value = items
    }

    private fun loadAllLocked(): List<SubmissionReceipt> {
        val raw = prefs.getString(KEY_RECEIPTS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList(array.length()) {
                for (i in 0 until array.length()) {
                    array.optJSONObject(i)?.toReceiptOrNull()?.let { add(it) }
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun SubmissionReceipt.toJson(): JSONObject = JSONObject().apply {
        put("submissionId", submissionId)
        put("uid", uid)
        put("businessId", businessId)
        put("kind", kind.name)
        put("primaryDocId", primaryDocId)
        if (secondaryDocId != null) put("secondaryDocId", secondaryDocId)
        put("fechaOperativa", OperationalDate.format(fechaOperativa))
        put("summaryText", summaryText)
        put("createdAtEpochMs", createdAtEpochMs)
        put("status", status.name)
        if (statusDetail != null) put("statusDetail", statusDetail)
    }

    private fun JSONObject.toReceiptOrNull(): SubmissionReceipt? = runCatching {
        SubmissionReceipt(
            submissionId = getString("submissionId"),
            uid = getString("uid"),
            businessId = getString("businessId"),
            kind = SubmissionKind.valueOf(getString("kind")),
            primaryDocId = getString("primaryDocId"),
            secondaryDocId = optString("secondaryDocId").takeIf { it.isNotEmpty() },
            fechaOperativa = OperationalDate.parse(getString("fechaOperativa")),
            summaryText = getString("summaryText"),
            createdAtEpochMs = getLong("createdAtEpochMs"),
            status = SubmissionSyncStatus.valueOf(getString("status")),
            statusDetail = optString("statusDetail").takeIf { it.isNotEmpty() },
        )
    }.getOrNull()

    private companion object {
        const val PREFS_NAME = "egglytics_submission_receipts"
        const val KEY_RECEIPTS = "receipts_json"
    }
}
