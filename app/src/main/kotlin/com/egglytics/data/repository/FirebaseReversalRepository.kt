package com.egglytics.data.repository

import com.egglytics.BuildConfig
import com.egglytics.domain.model.Harvest
import com.egglytics.domain.model.OperationalDate
import com.egglytics.domain.model.Payment
import com.egglytics.domain.model.Reversal
import com.egglytics.domain.model.ReversalTargetCollection
import com.egglytics.domain.model.Sale
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
import java.time.LocalDate
import java.util.Date
import javax.inject.Inject

class FirebaseReversalRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val clock: Clock,
) : ReversalRepository {

    override fun observeReversals(): Flow<List<ObservedReversal>> = callbackFlow {
        val registration = firestore
            .collection("businesses")
            .document(BuildConfig.BUSINESS_ID)
            .collection("reversals")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val items = snapshot.documents.mapNotNull { doc ->
                        val reversal = doc.toReversalOrNull() ?: return@mapNotNull null
                        ObservedReversal(
                            reversal = reversal,
                            isPendingWrite = doc.metadata.hasPendingWrites(),
                        )
                    }
                    trySend(items)
                }
            }
        awaitClose { registration.remove() }
    }

    override fun reverseHarvest(
        harvest: Harvest,
        businessStartDate: LocalDate,
        reason: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            onError("No hay usuario autenticado.")
            return
        }
        val trimmedReason = reason.trim()
        if (trimmedReason.isEmpty()) {
            onError("El motivo de anulación es obligatorio.")
            return
        }

        val today = OperationalDate.today(clock)
        Harvest.validateOperationalDate(today, businessStartDate)
        val now = Instant.now(clock)
        val reversalId = Reversal.harvestReversalId(harvest.id)
        val payload = reversalPayload(
            fechaOperativa = today,
            createdAt = now,
            createdBy = uid,
            targetCollection = ReversalTargetCollection.HARVESTS,
            targetId = harvest.id,
            reason = trimmedReason,
        )

        firestore
            .collection("businesses")
            .document(BuildConfig.BUSINESS_ID)
            .collection("reversals")
            .document(reversalId)
            .set(payload)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { ex ->
                onError(ex.localizedMessage ?: "No se pudo anular la recolección.")
            }
    }

    override fun reverseSaleWithPayment(
        sale: Sale,
        payment: Payment,
        businessStartDate: LocalDate,
        reason: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            onError("No hay usuario autenticado.")
            return
        }
        val trimmedReason = reason.trim()
        if (trimmedReason.isEmpty()) {
            onError("El motivo de anulación es obligatorio.")
            return
        }

        val today = OperationalDate.today(clock)
        Harvest.validateOperationalDate(today, businessStartDate)
        val now = Instant.now(clock)

        val businessRef = firestore.collection("businesses").document(BuildConfig.BUSINESS_ID)
        val reversalsRef = businessRef.collection("reversals")

        val paymentReversalId = Reversal.paymentReversalId(payment.id)
        val saleReversalId = Reversal.saleReversalId(sale.id)

        val paymentReversalPayload = reversalPayload(
            fechaOperativa = today,
            createdAt = now,
            createdBy = uid,
            targetCollection = ReversalTargetCollection.PAYMENTS,
            targetId = payment.id,
            reason = trimmedReason,
        )
        val saleReversalPayload = reversalPayload(
            fechaOperativa = today,
            createdAt = now,
            createdBy = uid,
            targetCollection = ReversalTargetCollection.SALES,
            targetId = sale.id,
            reason = trimmedReason,
        )

        val batch = firestore.batch()
        batch.set(reversalsRef.document(paymentReversalId), paymentReversalPayload)
        batch.set(reversalsRef.document(saleReversalId), saleReversalPayload)
        batch.commit()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { ex ->
                onError(ex.localizedMessage ?: "No se pudo anular la venta y su pago.")
            }
    }

    override fun reverseMovement(
        targetCollection: ReversalTargetCollection,
        targetId: String,
        businessStartDate: LocalDate,
        reason: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        val uid = auth.currentUser?.uid ?: return onError("No hay usuario autenticado.")
        val trimmedReason = reason.trim()
        if (trimmedReason.isEmpty()) return onError("El motivo de anulación es obligatorio.")

        val today = OperationalDate.today(clock)
        Harvest.validateOperationalDate(today, businessStartDate)
        val now = Instant.now(clock)
        val reversalId = "rev_${targetCollection.firestoreValue}_$targetId"
        val payload = reversalPayload(
            fechaOperativa = today,
            createdAt = now,
            createdBy = uid,
            targetCollection = targetCollection,
            targetId = targetId,
            reason = trimmedReason,
        )

        firestore
            .collection("businesses")
            .document(BuildConfig.BUSINESS_ID)
            .collection("reversals")
            .document(reversalId)
            .set(payload)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { ex ->
                onError(ex.localizedMessage ?: "No se pudo anular el movimiento.")
            }
    }

    private fun reversalPayload(
        fechaOperativa: LocalDate,
        createdAt: Instant,
        createdBy: String,
        targetCollection: ReversalTargetCollection,
        targetId: String,
        reason: String,
    ): Map<String, Any> = mapOf(
        "fechaOperativa" to OperationalDate.format(fechaOperativa),
        "createdAt" to Timestamp(Date.from(createdAt)),
        "syncedAt" to FieldValue.serverTimestamp(),
        "createdBy" to createdBy,
        "targetCollection" to targetCollection.firestoreValue,
        "targetId" to targetId,
        "reason" to reason,
    )
}

private fun DocumentSnapshot.toReversalOrNull(): Reversal? {
    if (!exists()) return null
    val fechaRaw = getString("fechaOperativa") ?: return null
    val fechaOperativa = runCatching { OperationalDate.parse(fechaRaw) }.getOrNull() ?: return null
    val targetCollRaw = getString("targetCollection") ?: return null
    val targetCollection = ReversalTargetCollection.fromFirestore(targetCollRaw) ?: return null
    val targetId = getString("targetId") ?: return null
    val reason = getString("reason") ?: return null
    val createdBy = getString("createdBy").orEmpty()
    val createdAt = getTimestamp("createdAt")?.toDate()?.toInstant() ?: Instant.EPOCH

    return runCatching {
        Reversal(
            id = id,
            fechaOperativa = fechaOperativa,
            createdAt = createdAt,
            createdBy = createdBy,
            targetCollection = targetCollection,
            targetId = targetId,
            reason = reason,
            note = getString("note"),
        )
    }.getOrNull()
}
