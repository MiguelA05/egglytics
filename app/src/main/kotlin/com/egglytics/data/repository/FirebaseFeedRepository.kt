package com.egglytics.data.repository

import com.egglytics.BuildConfig
import com.egglytics.domain.model.FeedDepletion
import com.egglytics.domain.model.FeedIngredient
import com.egglytics.domain.model.FeedLoss
import com.egglytics.domain.model.FeedLossType
import com.egglytics.domain.model.FeedPurchase
import com.egglytics.domain.model.Harvest
import com.egglytics.domain.model.OperationalDate
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

class FirebaseFeedRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val clock: Clock,
    private val receiptStore: SubmissionReceiptStore,
) : FeedRepository {

    override fun observePurchases(): Flow<List<ObservedFeedPurchase>> =
        observeCollection("feedPurchases") { doc, pending ->
            doc.toFeedPurchaseOrNull()?.let { ObservedFeedPurchase(it, pending) }
        }

    override fun observeDepletions(): Flow<List<ObservedFeedDepletion>> =
        observeCollection("feedDepletions") { doc, pending ->
            doc.toFeedDepletionOrNull()?.let { ObservedFeedDepletion(it, pending) }
        }

    override fun observeLosses(): Flow<List<ObservedFeedLoss>> =
        observeCollection("feedLosses") { doc, pending ->
            doc.toFeedLossOrNull()?.let { ObservedFeedLoss(it, pending) }
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
                    val items = snapshot.documents.mapNotNull { doc ->
                        mapper(doc, doc.metadata.hasPendingWrites())
                    }
                    trySend(items)
                }
            }
        awaitClose { registration.remove() }
    }

    override fun recordPurchase(
        request: FeedPurchaseRequest,
        onWriteError: (String) -> Unit,
    ) {
        val uid = auth.currentUser?.uid ?: return onWriteError("No hay usuario autenticado.")
        val now = Instant.now(clock)
        Harvest.validateOperationalDate(request.fechaOperativa, request.businessStartDate)
        val purchase = FeedPurchase(
            id = request.id,
            fechaOperativa = request.fechaOperativa,
            createdAt = now,
            createdBy = uid,
            ingredient = request.ingredient,
            bags = request.bags,
            totalCost = request.totalCost,
            methodId = request.methodId,
            supplier = request.supplier?.trim()?.takeIf { it.isNotEmpty() },
            note = request.note?.trim()?.takeIf { it.isNotEmpty() },
        )
        val fechaStr = OperationalDate.format(purchase.fechaOperativa)
        val payload = hashMapOf<String, Any>(
            "fechaOperativa" to fechaStr,
            "createdAt" to Timestamp(Date.from(purchase.createdAt)),
            "syncedAt" to FieldValue.serverTimestamp(),
            "createdBy" to purchase.createdBy,
            "ingredient" to purchase.ingredient.code,
            "bags" to purchase.bags,
            "totalKg" to purchase.totalKg,
            "totalCost" to purchase.totalCost,
            "methodId" to purchase.methodId,
        )
        if (!purchase.supplier.isNullOrBlank()) payload["supplier"] = purchase.supplier.orEmpty()
        if (!purchase.note.isNullOrBlank()) payload["note"] = purchase.note.orEmpty()

        writeWithReceipt(
            collection = "feedPurchases",
            docId = purchase.id,
            uid = uid,
            kind = SubmissionKind.FEED_PURCHASE,
            fechaOperativa = purchase.fechaOperativa,
            summaryText = "Compra alimento $fechaStr · ${purchase.ingredient.code} ${purchase.bags} bultos (${purchase.totalKg} kg)",
            payload = payload,
            fallbackError = "No se pudo guardar la compra de alimento.",
            onWriteError = onWriteError,
        )
    }

    override fun recordDepletion(
        request: FeedDepletionRequest,
        onWriteError: (String) -> Unit,
    ) {
        val uid = auth.currentUser?.uid ?: return onWriteError("No hay usuario autenticado.")
        val now = Instant.now(clock)
        Harvest.validateOperationalDate(request.fechaOperativa, request.businessStartDate)
        val depletion = FeedDepletion(
            id = request.id,
            fechaOperativa = request.fechaOperativa,
            createdAt = now,
            createdBy = uid,
            ingredient = request.ingredient,
            bags = request.bags,
            note = request.note?.trim()?.takeIf { it.isNotEmpty() },
        )
        val fechaStr = OperationalDate.format(depletion.fechaOperativa)
        val payload = hashMapOf<String, Any>(
            "fechaOperativa" to fechaStr,
            "createdAt" to Timestamp(Date.from(depletion.createdAt)),
            "syncedAt" to FieldValue.serverTimestamp(),
            "createdBy" to depletion.createdBy,
            "ingredient" to depletion.ingredient.code,
            "bags" to depletion.bags,
            "kg" to depletion.kg,
        )
        if (!depletion.note.isNullOrBlank()) payload["note"] = depletion.note.orEmpty()

        writeWithReceipt(
            collection = "feedDepletions",
            docId = depletion.id,
            uid = uid,
            kind = SubmissionKind.FEED_DEPLETION,
            fechaOperativa = depletion.fechaOperativa,
            summaryText = "Agotamiento $fechaStr · ${depletion.ingredient.code} ${depletion.bags} bultos (${depletion.kg} kg)",
            payload = payload,
            fallbackError = "No se pudo guardar el agotamiento de alimento.",
            onWriteError = onWriteError,
        )
    }

    override fun recordLoss(
        request: FeedLossRequest,
        onWriteError: (String) -> Unit,
    ) {
        val uid = auth.currentUser?.uid ?: return onWriteError("No hay usuario autenticado.")
        val now = Instant.now(clock)
        Harvest.validateOperationalDate(request.fechaOperativa, request.businessStartDate)
        val loss = FeedLoss(
            id = request.id,
            fechaOperativa = request.fechaOperativa,
            createdAt = now,
            createdBy = uid,
            ingredient = request.ingredient,
            kg = request.kg,
            type = request.type,
            reason = request.reason.trim(),
            note = request.note?.trim()?.takeIf { it.isNotEmpty() },
        )
        val fechaStr = OperationalDate.format(loss.fechaOperativa)
        val payload = hashMapOf<String, Any>(
            "fechaOperativa" to fechaStr,
            "createdAt" to Timestamp(Date.from(loss.createdAt)),
            "syncedAt" to FieldValue.serverTimestamp(),
            "createdBy" to loss.createdBy,
            "ingredient" to loss.ingredient.code,
            "kg" to loss.kg,
            "type" to loss.type.code,
            "reason" to loss.reason,
        )
        if (!loss.note.isNullOrBlank()) payload["note"] = loss.note.orEmpty()

        writeWithReceipt(
            collection = "feedLosses",
            docId = loss.id,
            uid = uid,
            kind = SubmissionKind.FEED_LOSS,
            fechaOperativa = loss.fechaOperativa,
            summaryText = "Pérdida alimento $fechaStr · ${loss.ingredient.code} ${loss.kg} kg",
            payload = payload,
            fallbackError = "No se pudo guardar la pérdida de alimento.",
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
                val message = ex.localizedMessage ?: fallbackError
                receiptStore.updateStatus(uid, BuildConfig.BUSINESS_ID, docId, SubmissionSyncStatus.REJECTED, message)
                onWriteError(message)
            }
    }
}

private fun DocumentSnapshot.toFeedPurchaseOrNull(): FeedPurchase? {
    if (!exists()) return null
    val fecha = getString("fechaOperativa")?.let { runCatching { OperationalDate.parse(it) }.getOrNull() } ?: return null
    val ingredient = getString("ingredient")?.let { runCatching { FeedIngredient.fromCode(it) }.getOrNull() } ?: return null
    val bags = getLong("bags") ?: return null
    val totalKg = getLong("totalKg") ?: return null
    val totalCost = getLong("totalCost") ?: return null
    val methodId = getString("methodId") ?: return null
    return runCatching {
        FeedPurchase(
            id = id,
            fechaOperativa = fecha,
            createdAt = getTimestamp("createdAt")?.toDate()?.toInstant() ?: Instant.EPOCH,
            createdBy = getString("createdBy").orEmpty(),
            ingredient = ingredient,
            bags = bags,
            totalKg = totalKg,
            totalCost = totalCost,
            methodId = methodId,
            supplier = getString("supplier"),
            note = getString("note"),
        )
    }.getOrNull()
}

private fun DocumentSnapshot.toFeedDepletionOrNull(): FeedDepletion? {
    if (!exists()) return null
    val fecha = getString("fechaOperativa")?.let { runCatching { OperationalDate.parse(it) }.getOrNull() } ?: return null
    val ingredient = getString("ingredient")?.let { runCatching { FeedIngredient.fromCode(it) }.getOrNull() } ?: return null
    val bags = getLong("bags") ?: return null
    val kg = getLong("kg") ?: return null
    return runCatching {
        FeedDepletion(
            id = id,
            fechaOperativa = fecha,
            createdAt = getTimestamp("createdAt")?.toDate()?.toInstant() ?: Instant.EPOCH,
            createdBy = getString("createdBy").orEmpty(),
            ingredient = ingredient,
            bags = bags,
            kg = kg,
            note = getString("note"),
        )
    }.getOrNull()
}

private fun DocumentSnapshot.toFeedLossOrNull(): FeedLoss? {
    if (!exists()) return null
    val fecha = getString("fechaOperativa")?.let { runCatching { OperationalDate.parse(it) }.getOrNull() } ?: return null
    val ingredient = getString("ingredient")?.let { runCatching { FeedIngredient.fromCode(it) }.getOrNull() } ?: return null
    val kg = getLong("kg") ?: return null
    val type = getString("type")?.let { FeedLossType.fromCode(it) } ?: return null
    val reason = getString("reason") ?: return null
    return runCatching {
        FeedLoss(
            id = id,
            fechaOperativa = fecha,
            createdAt = getTimestamp("createdAt")?.toDate()?.toInstant() ?: Instant.EPOCH,
            createdBy = getString("createdBy").orEmpty(),
            ingredient = ingredient,
            kg = kg,
            type = type,
            reason = reason,
            note = getString("note"),
        )
    }.getOrNull()
}
