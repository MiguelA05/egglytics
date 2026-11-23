package com.egglytics.data.repository

import com.egglytics.BuildConfig
import com.egglytics.domain.model.DeliveryStatus
import com.egglytics.domain.model.EggClassification
import com.egglytics.domain.model.OperationalDate
import com.egglytics.domain.model.Payment
import com.egglytics.domain.model.PaymentType
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
import java.util.Date
import javax.inject.Inject

class FirebaseSaleRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val clock: Clock,
    private val receiptStore: SubmissionReceiptStore,
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

    override fun observeSalePayments(): Flow<Map<String, List<Payment>>> = callbackFlow {
        val registration = firestore
            .collection("businesses")
            .document(BuildConfig.BUSINESS_ID)
            .collection("payments")
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val grouped = snapshot.documents
                        .mapNotNull { it.toSalePaymentOrNull() }
                        .groupBy { it.saleId.orEmpty() }
                    trySend(grouped)
                }
            }
        awaitClose { registration.remove() }
    }

    override fun observeAllSalePayments(): Flow<Map<String, List<ObservedSalePayment>>> = callbackFlow {
        val registration = firestore
            .collection("businesses")
            .document(BuildConfig.BUSINESS_ID)
            .collection("payments")
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val grouped = snapshot.documents
                        .mapNotNull { doc ->
                            val payment = doc.toAnySalePaymentOrNull() ?: return@mapNotNull null
                            ObservedSalePayment(
                                payment = payment,
                                isPendingWrite = doc.metadata.hasPendingWrites(),
                            )
                        }
                        .groupBy { it.payment.saleId.orEmpty() }
                    trySend(grouped)
                }
            }
        awaitClose { registration.remove() }
    }

    override fun observeSaleReturnIdsBySaleId(): Flow<Map<String, List<String>>> = callbackFlow {
        val registration = firestore
            .collection("businesses")
            .document(BuildConfig.BUSINESS_ID)
            .collection("saleReturns")
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val grouped = snapshot.documents
                        .mapNotNull { doc ->
                            val saleId = doc.getString("saleId") ?: return@mapNotNull null
                            saleId to doc.id
                        }
                        .groupBy({ it.first }, { it.second })
                    trySend(grouped)
                }
            }
        awaitClose { registration.remove() }
    }

    override fun createPendingSaleWithFullPayment(
        request: SaleWithPaymentRequest,
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
                id = request.saleId,
                fechaOperativa = request.fechaOperativa,
                businessStartDate = request.businessStartDate,
                createdAt = now,
                createdBy = uid,
                classification = request.classification,
                buckets = request.buckets,
                activePrice = request.activePrice,
                note = request.note,
            ),
        )
        val payment = Payment.forFullSalePayment(
            id = request.paymentId,
            sale = sale,
            methodId = request.methodId,
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

        val fechaStr = OperationalDate.format(sale.fechaOperativa)
        val createdAtTs = Timestamp(Date.from(sale.createdAt))

        val salePayload = hashMapOf<String, Any>(
            "fechaOperativa" to fechaStr,
            "createdAt" to createdAtTs,
            "syncedAt" to FieldValue.serverTimestamp(),
            "createdBy" to sale.createdBy,
            "lines" to linesPayload,
            "total" to sale.total,
            "deliveryStatus" to sale.deliveryStatus.firestoreValue,
        )
        if (!sale.note.isNullOrBlank()) {
            salePayload["note"] = sale.note.orEmpty()
        }

        val paymentPayload = hashMapOf<String, Any>(
            "fechaOperativa" to fechaStr,
            "createdAt" to createdAtTs,
            "syncedAt" to FieldValue.serverTimestamp(),
            "createdBy" to payment.createdBy,
            "saleId" to sale.id,
            "amount" to payment.amount,
            "methodId" to payment.methodId,
            "type" to payment.type.code,
        )
        if (!payment.note.isNullOrBlank()) {
            paymentPayload["note"] = payment.note.orEmpty()
        }

        val summaryText = "Venta $fechaStr · ${request.classification.name} " +
            "${request.buckets} cubetas (${sale.total})"
        receiptStore.upsert(
            SubmissionReceipt(
                submissionId = sale.id,
                uid = uid,
                businessId = BuildConfig.BUSINESS_ID,
                kind = SubmissionKind.SALE_WITH_PAYMENT,
                primaryDocId = sale.id,
                secondaryDocId = payment.id,
                fechaOperativa = sale.fechaOperativa,
                summaryText = summaryText,
                createdAtEpochMs = now.toEpochMilli(),
                status = SubmissionSyncStatus.PENDING,
            ),
        )

        val businessRef = firestore.collection("businesses").document(BuildConfig.BUSINESS_ID)
        val batch = firestore.batch()
        batch.set(businessRef.collection("sales").document(sale.id), salePayload)
        batch.set(businessRef.collection("payments").document(payment.id), paymentPayload)
        batch.commit()
            .addOnSuccessListener {
                receiptStore.updateStatus(
                    uid = uid,
                    businessId = BuildConfig.BUSINESS_ID,
                    submissionId = sale.id,
                    status = SubmissionSyncStatus.CONFIRMED,
                    statusDetail = null,
                )
            }
            .addOnFailureListener { ex ->
                val message = ex.localizedMessage ?: "No se pudo guardar la venta con su pago."
                receiptStore.updateStatus(
                    uid = uid,
                    businessId = BuildConfig.BUSINESS_ID,
                    submissionId = sale.id,
                    status = SubmissionSyncStatus.REJECTED,
                    statusDetail = message,
                )
                onWriteError(message)
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

private fun DocumentSnapshot.toSalePaymentOrNull(): Payment? {
    val payment = toAnySalePaymentOrNull() ?: return null
    return payment.takeIf { it.type == PaymentType.PAYMENT }
}

private fun DocumentSnapshot.toAnySalePaymentOrNull(): Payment? {
    if (!exists()) return null
    val saleId = getString("saleId") ?: return null
    val typeRaw = getString("type") ?: return null
    val type = runCatching { PaymentType.fromCode(typeRaw) }.getOrNull() ?: return null
    val fechaRaw = getString("fechaOperativa") ?: return null
    val fechaOperativa = runCatching { OperationalDate.parse(fechaRaw) }.getOrNull() ?: return null
    val amount = getLong("amount") ?: return null
    val methodId = getString("methodId") ?: return null
    val createdBy = getString("createdBy").orEmpty()
    val createdAt = getTimestamp("createdAt")?.toDate()?.toInstant() ?: Instant.EPOCH

    return runCatching {
        Payment(
            id = id,
            fechaOperativa = fechaOperativa,
            createdAt = createdAt,
            createdBy = createdBy,
            saleId = saleId,
            amount = amount,
            methodId = methodId,
            type = type,
            saleReturnId = getString("saleReturnId"),
            reason = getString("reason"),
            note = getString("note"),
        )
    }.getOrNull()
}
