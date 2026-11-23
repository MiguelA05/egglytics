package com.egglytics.data.repository

import com.egglytics.BuildConfig
import com.egglytics.domain.model.CountItem
import com.egglytics.domain.model.CountKind
import com.egglytics.domain.model.DailyClosing
import com.egglytics.domain.model.Harvest
import com.egglytics.domain.model.OpeningBalance
import com.egglytics.domain.model.OpeningKind
import com.egglytics.domain.model.OperationalDate
import com.egglytics.domain.model.PhysicalCount
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
import java.util.UUID
import javax.inject.Inject

class FirebaseOpeningAndCountRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val clock: Clock,
) : OpeningAndCountRepository {

    override fun observeOpenings(): Flow<List<OpeningBalance>> =
        observeCollection("openingBalances") { it.toOpeningBalanceOrNull() }

    override fun observeCounts(): Flow<List<PhysicalCount>> =
        observeCollection("counts") { it.toPhysicalCountOrNull() }

    override fun observeClosings(): Flow<List<DailyClosing>> =
        observeCollection("closings") { it.toDailyClosingOrNull() }

    private fun <T> observeCollection(
        collection: String,
        mapper: (DocumentSnapshot) -> T?,
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
                    trySend(snapshot.documents.mapNotNull(mapper))
                }
            }
        awaitClose { registration.remove() }
    }

    override fun recordOpenings(
        fechaOperativa: LocalDate,
        businessStartDate: LocalDate,
        entries: List<OpeningEntryInput>,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        val uid = auth.currentUser?.uid ?: return onError("No hay usuario autenticado.")
        if (entries.isEmpty()) return onError("Ingresa al menos un saldo de apertura.")
        Harvest.validateOperationalDate(fechaOperativa, businessStartDate)

        val now = Instant.now(clock)
        val fechaStr = OperationalDate.format(fechaOperativa)
        val createdAtTs = Timestamp(Date.from(now))
        val businessRef = firestore.collection("businesses").document(BuildConfig.BUSINESS_ID)
        val batch = firestore.batch()

        entries.forEach { entry ->
            val docId = "ob_${entry.kind.code}_${entry.key}"
            val payload = mapOf<String, Any>(
                "fechaOperativa" to fechaStr,
                "createdAt" to createdAtTs,
                "syncedAt" to FieldValue.serverTimestamp(),
                "createdBy" to uid,
                "kind" to entry.kind.code,
                "key" to entry.key,
                "quantity" to entry.quantity,
            )
            batch.set(businessRef.collection("openingBalances").document(docId), payload)
        }

        batch.commit()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { ex -> onError(ex.localizedMessage ?: "No se pudo guardar la apertura.") }
    }

    override fun recordCountOrClosing(
        request: CountSubmitRequest,
        onSuccess: (List<String>) -> Unit,
        onError: (String) -> Unit,
    ) {
        val uid = auth.currentUser?.uid ?: return onError("No hay usuario autenticado.")
        Harvest.validateOperationalDate(request.fechaOperativa, request.businessStartDate)
        if (!request.isClosing && request.feedItems.isEmpty() && request.cashItems.isEmpty()) {
            onError("Cuenta al menos un ítem de alimento o caja.")
            return
        }

        val now = Instant.now(clock)
        val fechaStr = OperationalDate.format(request.fechaOperativa)
        val createdAtTs = Timestamp(Date.from(now))
        val businessRef = firestore.collection("businesses").document(BuildConfig.BUSINESS_ID)
        val batch = firestore.batch()
        val countIds = mutableListOf<String>()

        fun addCountDoc(kind: CountKind, items: Map<String, CountItem>) {
            if (items.isEmpty()) return
            val countId = UUID.randomUUID().toString()
            countIds.add(countId)
            val itemsPayload = items.mapValues { (_, item) ->
                buildMap<String, Any> {
                    put("expected", item.expected)
                    put("physical", item.physical)
                    put("difference", item.difference)
                    if (!item.reason.isNullOrBlank()) put("reason", item.reason.orEmpty())
                }
            }
            val payload = hashMapOf<String, Any>(
                "fechaOperativa" to fechaStr,
                "createdAt" to createdAtTs,
                "syncedAt" to FieldValue.serverTimestamp(),
                "createdBy" to uid,
                "kind" to kind.code,
                "items" to itemsPayload,
                "isClosing" to request.isClosing,
            )
            if (!request.note.isNullOrBlank()) payload["note"] = request.note.trim()
            batch.set(businessRef.collection("counts").document(countId), payload)
        }

        addCountDoc(CountKind.FEED, request.feedItems)
        addCountDoc(CountKind.CASH, request.cashItems)

        if (request.isClosing) {
            val closingId = UUID.randomUUID().toString()
            val closingPayload = hashMapOf<String, Any>(
                "fechaOperativa" to fechaStr,
                "createdAt" to createdAtTs,
                "syncedAt" to FieldValue.serverTimestamp(),
                "createdBy" to uid,
                "summary" to request.closingSummary,
                "countIds" to countIds,
            )
            if (!request.note.isNullOrBlank()) closingPayload["note"] = request.note.trim()
            batch.set(businessRef.collection("closings").document(closingId), closingPayload)
        }

        batch.commit()
            .addOnSuccessListener { onSuccess(countIds) }
            .addOnFailureListener { ex -> onError(ex.localizedMessage ?: "No se pudo guardar el conteo/cierre.") }
    }
}

private fun DocumentSnapshot.toOpeningBalanceOrNull(): OpeningBalance? {
    if (!exists()) return null
    val fecha = getString("fechaOperativa")?.let { runCatching { OperationalDate.parse(it) }.getOrNull() } ?: return null
    val kind = getString("kind")?.let { OpeningKind.fromCode(it) } ?: return null
    val key = getString("key") ?: return null
    val qty = getLong("quantity") ?: return null
    return runCatching {
        OpeningBalance(
            id = id,
            fechaOperativa = fecha,
            createdAt = getTimestamp("createdAt")?.toDate()?.toInstant() ?: Instant.EPOCH,
            createdBy = getString("createdBy").orEmpty(),
            kind = kind,
            key = key,
            quantity = qty,
            note = getString("note"),
        )
    }.getOrNull()
}

private fun DocumentSnapshot.toPhysicalCountOrNull(): PhysicalCount? {
    if (!exists()) return null
    val fecha = getString("fechaOperativa")?.let { runCatching { OperationalDate.parse(it) }.getOrNull() } ?: return null
    val kind = getString("kind")?.let { CountKind.fromCode(it) } ?: return null
    val isClosing = getBoolean("isClosing") ?: return null
    val rawItems = get("items") as? Map<*, *> ?: return null
    val items = buildMap {
        rawItems.forEach { (k, v) ->
            val key = k as? String ?: return@forEach
            val m = v as? Map<*, *> ?: return@forEach
            val expected = (m["expected"] as? Number)?.toLong() ?: return@forEach
            val physical = (m["physical"] as? Number)?.toLong() ?: return@forEach
            val diff = (m["difference"] as? Number)?.toLong() ?: (physical - expected)
            val reason = m["reason"] as? String
            runCatching { CountItem(expected, physical, diff, reason) }.getOrNull()?.let { put(key, it) }
        }
    }
    if (items.isEmpty()) return null
    return runCatching {
        PhysicalCount(
            id = id,
            fechaOperativa = fecha,
            createdAt = getTimestamp("createdAt")?.toDate()?.toInstant() ?: Instant.EPOCH,
            createdBy = getString("createdBy").orEmpty(),
            kind = kind,
            items = items,
            isClosing = isClosing,
            note = getString("note"),
        )
    }.getOrNull()
}

private fun DocumentSnapshot.toDailyClosingOrNull(): DailyClosing? {
    if (!exists()) return null
    val fecha = getString("fechaOperativa")?.let { runCatching { OperationalDate.parse(it) }.getOrNull() } ?: return null
    val rawSummary = get("summary") as? Map<*, *> ?: emptyMap<String, Long>()
    val summary = rawSummary.entries.mapNotNull { (k, v) ->
        val key = k as? String ?: return@mapNotNull null
        val valLong = (v as? Number)?.toLong() ?: return@mapNotNull null
        key to valLong
    }.toMap()
    val countIds = (get("countIds") as? List<*>)?.mapNotNull { it as? String }.orEmpty()
    return DailyClosing(
        id = id,
        fechaOperativa = fecha,
        createdAt = getTimestamp("createdAt")?.toDate()?.toInstant() ?: Instant.EPOCH,
        createdBy = getString("createdBy").orEmpty(),
        summary = summary,
        countIds = countIds,
        note = getString("note"),
    )
}
