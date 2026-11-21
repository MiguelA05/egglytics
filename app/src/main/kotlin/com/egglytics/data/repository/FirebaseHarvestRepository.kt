package com.egglytics.data.repository

import com.egglytics.BuildConfig
import com.egglytics.domain.model.EggClassification
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
import java.time.LocalDate
import java.util.Date
import javax.inject.Inject

class FirebaseHarvestRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val clock: Clock,
) : HarvestRepository {

    override fun observeHarvests(): Flow<List<ObservedHarvest>> = callbackFlow {
        val registration = firestore
            .collection("businesses")
            .document(BuildConfig.BUSINESS_ID)
            .collection("harvests")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val items = snapshot.documents.mapNotNull { doc ->
                        val harvest = doc.toHarvestOrNull() ?: return@mapNotNull null
                        ObservedHarvest(
                            harvest = harvest,
                            isPendingWrite = doc.metadata.hasPendingWrites(),
                        )
                    }
                    trySend(items)
                }
            }
        awaitClose { registration.remove() }
    }

    override fun recordHarvest(
        harvestId: String,
        fechaOperativa: LocalDate,
        graded: Map<EggClassification, Long>,
        discarded: Long,
        note: String?,
        onWriteError: (String) -> Unit,
    ) {
        Harvest.validate(graded, discarded)
        val uid = auth.currentUser?.uid
        if (uid == null) {
            onWriteError("No hay usuario autenticado.")
            return
        }

        val now = Instant.now(clock)
        val gradedPayload = graded
            .filterValues { it >= 0L }
            .mapKeys { (classification, _) -> classification.name }

        val payload = hashMapOf<String, Any>(
            "fechaOperativa" to OperationalDate.format(fechaOperativa),
            "createdAt" to Timestamp(Date.from(now)),
            "syncedAt" to FieldValue.serverTimestamp(),
            "createdBy" to uid,
            "graded" to gradedPayload,
            "discarded" to discarded,
        )
        val trimmedNote = note?.trim().orEmpty()
        if (trimmedNote.isNotEmpty()) {
            payload["note"] = trimmedNote
        }

        firestore
            .collection("businesses")
            .document(BuildConfig.BUSINESS_ID)
            .collection("harvests")
            .document(harvestId)
            .set(payload)
            .addOnFailureListener { ex ->
                onWriteError(ex.localizedMessage ?: "No se pudo sincronizar la recolección.")
            }
    }
}

private fun DocumentSnapshot.toHarvestOrNull(): Harvest? {
    if (!exists()) return null
    val fechaRaw = getString("fechaOperativa") ?: return null
    val fechaOperativa = runCatching { OperationalDate.parse(fechaRaw) }.getOrNull() ?: return null
    val discarded = getLong("discarded") ?: return null
    val createdBy = getString("createdBy").orEmpty()
    val createdAt = getTimestamp("createdAt")?.toDate()?.toInstant() ?: Instant.EPOCH

    val rawGraded = get("graded") as? Map<*, *> ?: return null
    val graded = buildMap {
        rawGraded.forEach { (k, v) ->
            val key = k as? String ?: return@forEach
            val classification = EggClassification.entries.firstOrNull { it.name == key } ?: return@forEach
            val count = (v as? Number)?.toLong() ?: return@forEach
            if (count >= 0L) {
                put(classification, count)
            }
        }
    }

    return runCatching {
        Harvest(
            id = id,
            fechaOperativa = fechaOperativa,
            createdAt = createdAt,
            createdBy = createdBy,
            graded = graded,
            discarded = discarded,
            note = getString("note"),
        )
    }.getOrNull()
}
