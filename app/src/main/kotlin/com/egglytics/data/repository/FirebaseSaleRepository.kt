package com.egglytics.data.repository

import com.egglytics.BuildConfig
import com.egglytics.domain.model.DeliveryStatus
import com.egglytics.domain.model.EggClassification
import com.egglytics.domain.model.OperationalDate
import com.egglytics.domain.model.Price
import com.egglytics.domain.model.Sale
import com.egglytics.domain.model.SaleLine
import com.egglytics.domain.model.SingleLineSaleRequest
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

class FirebaseSaleRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val clock: Clock,
) : SaleRepository {

    override fun observeSales(): Flow<List<ObservedSale>> = callbackFlow {
        val registration = firestore
            .collection("businesses")
            .document(BuildConfig.BUSINESS_ID)
            .collection("sales")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val items = snapshot.documents.mapNotNull { doc ->
                        val sale = doc.toSaleOrNull() ?: return@mapNotNull null
                        ObservedSale(
                            sale = sale,
                            isPendingWrite = doc.metadata.hasPendingWrites(),
                        )
                    }
                    trySend(items)
                }
            }
        awaitClose { registration.remove() }
    }

    override fun createPendingSale(
        saleId: String,
        fechaOperativa: LocalDate,
        businessStartDate: LocalDate,
        classification: EggClassification,
        buckets: Long,
        activePrice: Price,
        note: String?,
        onWriteError: (String) -> Unit,
    ) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            onWriteError("No hay usuario autenticado.")
            return
        }

        val now = Instant.now(clock)
        val sale = Sale.createSingleLinePending(
            SingleLineSaleRequest(
                id = saleId,
                fechaOperativa = fechaOperativa,
                businessStartDate = businessStartDate,
                createdAt = now,
                createdBy = uid,
                classification = classification,
                buckets = buckets,
                activePrice = activePrice,
                note = note,
            ),
        )

        val linesPayload = sale.lines.map { line ->
            mapOf(
                "classification" to line.classification.name,
                "buckets" to line.buckets,
                "eggs" to line.eggs,
                "listPrice" to line.listPrice,
                "unitPrice" to line.unitPrice,
                "subtotal" to line.subtotal,
            )
        }

        val payload = hashMapOf<String, Any>(
            "fechaOperativa" to OperationalDate.format(sale.fechaOperativa),
            "createdAt" to Timestamp(Date.from(sale.createdAt)),
            "syncedAt" to FieldValue.serverTimestamp(),
            "createdBy" to sale.createdBy,
            "lines" to linesPayload,
            "total" to sale.total,
            "deliveryStatus" to sale.deliveryStatus.firestoreValue,
        )
        if (!sale.note.isNullOrBlank()) {
            payload["note"] = sale.note.orEmpty()
        }

        firestore
            .collection("businesses")
            .document(BuildConfig.BUSINESS_ID)
            .collection("sales")
            .document(sale.id)
            .set(payload)
            .addOnFailureListener { ex ->
                onWriteError(ex.localizedMessage ?: "No se pudo sincronizar la venta.")
            }
    }

    override fun markDelivered(
        saleId: String,
        onWriteError: (String) -> Unit,
    ) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            onWriteError("No hay usuario autenticado.")
            return
        }

        val deliveredAt = Instant.now(clock)
        val updates = mapOf<String, Any>(
            "deliveryStatus" to DeliveryStatus.DELIVERED.firestoreValue,
            "deliveredAt" to Timestamp(Date.from(deliveredAt)),
            "deliveredBy" to uid,
        )

        firestore
            .collection("businesses")
            .document(BuildConfig.BUSINESS_ID)
            .collection("sales")
            .document(saleId)
            .update(updates)
            .addOnFailureListener { ex ->
                onWriteError(ex.localizedMessage ?: "No se pudo marcar la entrega.")
            }
    }
}

private fun DocumentSnapshot.toSaleOrNull(): Sale? {
    if (!exists()) return null
    val fechaRaw = getString("fechaOperativa") ?: return null
    val fechaOperativa = runCatching { OperationalDate.parse(fechaRaw) }.getOrNull() ?: return null
    val createdAt = getTimestamp("createdAt")?.toDate()?.toInstant() ?: Instant.EPOCH
    val createdBy = getString("createdBy").orEmpty()
    val total = getLong("total") ?: return null
    val statusRaw = getString("deliveryStatus") ?: return null
    val deliveryStatus = DeliveryStatus.fromFirestore(statusRaw) ?: return null

    val rawLines = get("lines") as? List<*> ?: return null
    val lines = rawLines.mapNotNull { item ->
        val map = item as? Map<*, *> ?: return@mapNotNull null
        val clsRaw = map["classification"] as? String ?: return@mapNotNull null
        val cls = EggClassification.entries.firstOrNull { it.name == clsRaw } ?: return@mapNotNull null
        val buckets = (map["buckets"] as? Number)?.toLong() ?: return@mapNotNull null
        val eggs = (map["eggs"] as? Number)?.toLong() ?: return@mapNotNull null
        val listPrice = (map["listPrice"] as? Number)?.toLong() ?: return@mapNotNull null
        val unitPrice = (map["unitPrice"] as? Number)?.toLong() ?: return@mapNotNull null
        val subtotal = (map["subtotal"] as? Number)?.toLong() ?: return@mapNotNull null
        runCatching {
            SaleLine(
                classification = cls,
                buckets = buckets,
                eggs = eggs,
                listPrice = listPrice,
                unitPrice = unitPrice,
                subtotal = subtotal,
            )
        }.getOrNull()
    }
    if (lines.size != rawLines.size || lines.isEmpty()) return null

    return runCatching {
        Sale(
            id = id,
            fechaOperativa = fechaOperativa,
            createdAt = createdAt,
            createdBy = createdBy,
            lines = lines,
            total = total,
            deliveryStatus = deliveryStatus,
            deliveredAt = getTimestamp("deliveredAt")?.toDate()?.toInstant(),
            deliveredBy = getString("deliveredBy"),
            note = getString("note"),
        )
    }.getOrNull()
}
