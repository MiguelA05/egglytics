package com.egglytics.data.repository

import com.egglytics.BuildConfig
import com.egglytics.domain.model.Expense
import com.egglytics.domain.model.Harvest
import com.egglytics.domain.model.OperationalDate
import com.egglytics.domain.model.SaleReturnSummary
import com.egglytics.domain.model.Withdrawal
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.time.Clock
import java.time.Instant
import java.util.Date
import javax.inject.Inject

class FirebaseExpenseAndWithdrawalRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val clock: Clock,
    private val receiptStore: SubmissionReceiptStore,
) : ExpenseAndWithdrawalRepository {

    override fun observeExpenses(): Flow<List<ObservedExpense>> =
        observeCollection("expenses") { doc, pending ->
            doc.toExpenseOrNull()?.let { ObservedExpense(it, pending) }
        }

    override fun observeWithdrawals(): Flow<List<ObservedWithdrawal>> =
        observeCollection("withdrawals") { doc, pending ->
            doc.toWithdrawalOrNull()?.let { ObservedWithdrawal(it, pending) }
        }

    override fun observeSaleReturns(): Flow<List<SaleReturnSummary>> =
        observeCollection("saleReturns") { doc, _ ->
            doc.toSaleReturnSummaryOrNull()
        }

    private fun <T> observeCollection(
        collection: String,
        mapper: (DocumentSnapshot, Boolean) -> T?,
    ): Flow<List<T>> = callbackFlow {
        val registration = firestore
            .collection("businesses")
            .document(BuildConfig.BUSINESS_ID)
            .collection(collection)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    trySend(snapshot.documents.mapNotNull { mapper(it, it.metadata.hasPendingWrites()) })
                }
            }
        awaitClose { registration.remove() }
    }

    override fun recordExpense(request: ExpenseRequest, onWriteError: (String) -> Unit) {
        val uid = auth.currentUser?.uid ?: return onWriteError("No hay usuario autenticado.")
        val now = Instant.now(clock)
        Harvest.validateOperationalDate(request.fechaOperativa, request.businessStartDate)
        val expense = Expense(
            id = request.id,
            fechaOperativa = request.fechaOperativa,
            createdAt = now,
            createdBy = uid,
            categoryId = request.categoryId,
            amount = request.amount,
            methodId = request.methodId,
            description = request.description?.trim()?.takeIf { it.isNotEmpty() },
            note = request.note?.trim()?.takeIf { it.isNotEmpty() },
        )
        val fechaStr = OperationalDate.format(expense.fechaOperativa)
        val payload = hashMapOf<String, Any>(
            "fechaOperativa" to fechaStr,
            "createdAt" to Timestamp(Date.from(expense.createdAt)),
            "syncedAt" to FieldValue.serverTimestamp(),
            "createdBy" to expense.createdBy,
            "categoryId" to expense.categoryId,
            "amount" to expense.amount,
            "methodId" to expense.methodId,
        )
        if (!expense.description.isNullOrBlank()) payload["description"] = expense.description.orEmpty()
        if (!expense.note.isNullOrBlank()) payload["note"] = expense.note.orEmpty()

        writeWithReceipt(
            collection = "expenses",
            docId = expense.id,
            uid = uid,
            kind = SubmissionKind.EXPENSE,
            fechaOperativa = expense.fechaOperativa,
            summaryText = "Gasto $fechaStr · ${expense.amount}",
            payload = payload,
            fallbackError = "No se pudo guardar el gasto.",
            onWriteError = onWriteError,
        )
    }

    override fun recordWithdrawal(request: WithdrawalRequest, onWriteError: (String) -> Unit) {
        val uid = auth.currentUser?.uid ?: return onWriteError("No hay usuario autenticado.")
        val now = Instant.now(clock)
        Harvest.validateOperationalDate(request.fechaOperativa, request.businessStartDate)
        val withdrawal = Withdrawal(
            id = request.id,
            fechaOperativa = request.fechaOperativa,
            createdAt = now,
            createdBy = uid,
            amount = request.amount,
            methodId = request.methodId,
            note = request.note?.trim()?.takeIf { it.isNotEmpty() },
        )
        val fechaStr = OperationalDate.format(withdrawal.fechaOperativa)
        val payload = hashMapOf<String, Any>(
            "fechaOperativa" to fechaStr,
            "createdAt" to Timestamp(Date.from(withdrawal.createdAt)),
            "syncedAt" to FieldValue.serverTimestamp(),
            "createdBy" to withdrawal.createdBy,
            "amount" to withdrawal.amount,
            "methodId" to withdrawal.methodId,
        )
        if (!withdrawal.note.isNullOrBlank()) payload["note"] = withdrawal.note.orEmpty()

        writeWithReceipt(
            collection = "withdrawals",
            docId = withdrawal.id,
            uid = uid,
            kind = SubmissionKind.WITHDRAWAL,
            fechaOperativa = withdrawal.fechaOperativa,
            summaryText = "Retiro $fechaStr · ${withdrawal.amount}",
            payload = payload,
            fallbackError = "No se pudo guardar el retiro.",
            onWriteError = onWriteError,
        )
    }

    private fun writeWithReceipt(
        collection: String,
        docId: String,
        uid: String,
        kind: SubmissionKind,
        fechaOperativa: java.time.LocalDate,
        summaryText: String,
        payload: Map<String, Any>,
        fallbackError: String,
        onWriteError: (String) -> Unit,
    ) {
        receiptStore.upsert(
            SubmissionReceipt(
                submissionId = docId,
                uid = uid,
                businessId = BuildConfig.BUSINESS_ID,
                kind = kind,
                primaryDocId = docId,
                fechaOperativa = fechaOperativa,
                summaryText = summaryText,
                createdAtEpochMs = clock.millis(),
                status = SubmissionSyncStatus.PENDING,
            ),
        )
        firestore
            .collection("businesses")
            .document(BuildConfig.BUSINESS_ID)
            .collection(collection)
            .document(docId)
            .set(payload)
            .addOnSuccessListener {
                receiptStore.updateStatus(uid, BuildConfig.BUSINESS_ID, docId, SubmissionSyncStatus.CONFIRMED, null)
            }
            .addOnFailureListener { ex ->
                val msg = ex.localizedMessage ?: fallbackError
                receiptStore.updateStatus(uid, BuildConfig.BUSINESS_ID, docId, SubmissionSyncStatus.REJECTED, msg)
                onWriteError(msg)
            }
    }
}

private fun DocumentSnapshot.toExpenseOrNull(): Expense? {
    if (!exists()) return null
    val fecha = getString("fechaOperativa")?.let { runCatching { OperationalDate.parse(it) }.getOrNull() } ?: return null
    val categoryId = getString("categoryId") ?: return null
    val amount = getLong("amount") ?: return null
    val methodId = getString("methodId") ?: return null
    return runCatching {
        Expense(
            id = id,
            fechaOperativa = fecha,
            createdAt = getTimestamp("createdAt")?.toDate()?.toInstant() ?: Instant.EPOCH,
            createdBy = getString("createdBy").orEmpty(),
            categoryId = categoryId,
            amount = amount,
            methodId = methodId,
            description = getString("description"),
            note = getString("note"),
        )
    }.getOrNull()
}

private fun DocumentSnapshot.toWithdrawalOrNull(): Withdrawal? {
    if (!exists()) return null
    val fecha = getString("fechaOperativa")?.let { runCatching { OperationalDate.parse(it) }.getOrNull() } ?: return null
    val amount = getLong("amount") ?: return null
    val methodId = getString("methodId") ?: return null
    return runCatching {
        Withdrawal(
            id = id,
            fechaOperativa = fecha,
            createdAt = getTimestamp("createdAt")?.toDate()?.toInstant() ?: Instant.EPOCH,
            createdBy = getString("createdBy").orEmpty(),
            amount = amount,
            methodId = methodId,
            note = getString("note"),
        )
    }.getOrNull()
}

private fun DocumentSnapshot.toSaleReturnSummaryOrNull(): SaleReturnSummary? {
    if (!exists()) return null
    val fecha = getString("fechaOperativa")?.let { runCatching { OperationalDate.parse(it) }.getOrNull() } ?: return null
    val credit = getLong("creditAmount") ?: return null
    return SaleReturnSummary(
        id = id,
        fechaOperativa = fecha,
        createdAt = getTimestamp("createdAt")?.toDate()?.toInstant() ?: Instant.EPOCH,
        creditAmount = credit,
    )
}
