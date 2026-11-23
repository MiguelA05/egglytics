package com.egglytics.data.repository

import com.egglytics.BuildConfig
import com.egglytics.domain.model.PaymentMethod
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject

class FirebasePaymentMethodRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) : PaymentMethodRepository {

    override fun observeActivePaymentMethods(): Flow<List<PaymentMethod>> = callbackFlow {
        val registration = firestore
            .collection("businesses")
            .document(BuildConfig.BUSINESS_ID)
            .collection("paymentMethods")
            .whereEqualTo("active", true)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val methods = snapshot.documents
                        .mapNotNull { it.toPaymentMethodOrNull() }
                        .filter { it.active }
                        .sortedBy { it.name }
                    trySend(methods)
                }
            }
        awaitClose { registration.remove() }
    }

    override fun observeAllPaymentMethods(): Flow<List<PaymentMethod>> = callbackFlow {
        val registration = firestore
            .collection("businesses")
            .document(BuildConfig.BUSINESS_ID)
            .collection("paymentMethods")
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val methods = snapshot.documents
                        .mapNotNull { it.toPaymentMethodOrNull() }
                        .sortedBy { it.name }
                    trySend(methods)
                }
            }
        awaitClose { registration.remove() }
    }
}

private fun DocumentSnapshot.toPaymentMethodOrNull(): PaymentMethod? {
    if (!exists()) return null
    val name = getString("name")?.takeIf { it.isNotBlank() } ?: return null
    val active = getBoolean("active") == true
    return PaymentMethod(
        id = id,
        name = name,
        active = active,
    )
}
