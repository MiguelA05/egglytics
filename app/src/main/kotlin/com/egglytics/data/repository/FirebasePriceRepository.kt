package com.egglytics.data.repository

import com.egglytics.BuildConfig
import com.egglytics.domain.model.EggClassification
import com.egglytics.domain.model.OperationalDate
import com.egglytics.domain.model.Price
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import java.time.LocalDate
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.time.Instant
import javax.inject.Inject

class FirebasePriceRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) : PriceRepository {

    private val prices get() = firestore.collection("businesses").document(BuildConfig.BUSINESS_ID).collection("prices")

    override suspend fun addPrice(classification: EggClassification, amount: Long, validFrom: LocalDate, uid: String) {
        require(amount > 0)
        val document = prices.document()
        // Catalog changes must be confirmed before other sales can use them.
        firestore.runTransaction { transaction ->
            transaction.set(document, mapOf(
                "classification" to classification.name,
                "pricePerBucket" to amount,
                "validFrom" to OperationalDate.format(validFrom),
                "createdBy" to uid,
                "createdAt" to FieldValue.serverTimestamp(),
                "syncedAt" to FieldValue.serverTimestamp(),
            ))
            Unit
        }.await()
    }

    override fun observePrices(): Flow<List<Price>> = callbackFlow {
        val registration = prices
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    trySend(snapshot.documents.mapNotNull { it.toPriceOrNull() })
                }
            }
        awaitClose { registration.remove() }
    }

    override suspend fun getPrices(): List<Price> {
        val snapshot = prices
            .get()
            .await()
        return snapshot.documents.mapNotNull { it.toPriceOrNull() }
    }
}

private fun DocumentSnapshot.toPriceOrNull(): Price? {
    if (!exists()) return null
    val classificationRaw = getString("classification") ?: return null
    val classification = EggClassification.entries.firstOrNull { it.name == classificationRaw } ?: return null
    val pricePerBucket = getLong("pricePerBucket") ?: return null
    if (pricePerBucket <= 0L) return null
    val validFromRaw = getString("validFrom") ?: return null
    val validFrom = runCatching { OperationalDate.parse(validFromRaw) }.getOrNull() ?: return null
    val createdAt = getTimestamp("createdAt")?.let { Instant.ofEpochSecond(it.seconds, it.nanoseconds.toLong()) } ?: Instant.EPOCH
    val createdBy = getString("createdBy").orEmpty()

    return Price(
        id = id,
        classification = classification,
        pricePerBucket = pricePerBucket,
        validFrom = validFrom,
        createdAt = createdAt,
        createdBy = createdBy,
        note = getString("note"),
    )
}
