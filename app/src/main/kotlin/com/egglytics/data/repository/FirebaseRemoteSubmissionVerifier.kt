package com.egglytics.data.repository

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseRemoteSubmissionVerifier @Inject constructor(
    private val firestore: FirebaseFirestore,
) : RemoteSubmissionVerifier {

    override suspend fun verify(receipt: SubmissionReceipt): RemoteVerificationOutcome {
        val businessRef = firestore.collection("businesses").document(receipt.businessId)
        val primaryRef = when (receipt.kind) {
            SubmissionKind.HARVEST -> businessRef.collection("harvests").document(receipt.primaryDocId)
            SubmissionKind.SALE_WITH_PAYMENT -> businessRef.collection("sales").document(receipt.primaryDocId)
        }
        val secondaryRef = receipt.secondaryDocId?.let { paymentId ->
            businessRef.collection("payments").document(paymentId)
        }

        val pendingBeforeDrain = hasLocalPendingWrite(primaryRef) ||
            (secondaryRef != null && hasLocalPendingWrite(secondaryRef))

        if (pendingBeforeDrain) {
            withTimeoutOrNull(WAIT_FOR_PENDING_TIMEOUT_MS) {
                runCatching { firestore.waitForPendingWrites().await() }
            }
        }

        val serverPrimary = runCatching { primaryRef.get(Source.SERVER).await() }
        val serverSecondary = secondaryRef?.let { ref ->
            runCatching { ref.get(Source.SERVER).await() }
        }

        val serverError = serverPrimary.exceptionOrNull() ?: serverSecondary?.exceptionOrNull()
        if (serverError != null) {
            val stillPending = hasLocalPendingWrite(primaryRef) ||
                (secondaryRef != null && hasLocalPendingWrite(secondaryRef))
            return if (stillPending) {
                RemoteVerificationOutcome.StillPendingLocally
            } else {
                RemoteVerificationOutcome.Unconfirmed(
                    "Sin conexión para confirmar con el servidor (${serverError.localizedMessage ?: "sin red"}).",
                )
            }
        }

        val primaryExists = serverPrimary.getOrNull()?.exists() == true
        val secondaryExists = serverSecondary?.getOrNull()?.exists() ?: true
        if (primaryExists && secondaryExists) {
            return RemoteVerificationOutcome.Confirmed
        }

        val stillPendingAfterServerCheck = hasLocalPendingWrite(primaryRef) ||
            (secondaryRef != null && hasLocalPendingWrite(secondaryRef))
        if (stillPendingAfterServerCheck) {
            return RemoteVerificationOutcome.StillPendingLocally
        }

        val missingDetail = when {
            receipt.kind == SubmissionKind.SALE_WITH_PAYMENT && !primaryExists && !secondaryExists ->
                "Ni la venta ni su pago se confirmaron en el servidor"
            receipt.kind == SubmissionKind.SALE_WITH_PAYMENT && !primaryExists ->
                "La venta no se confirmó en el servidor"
            receipt.kind == SubmissionKind.SALE_WITH_PAYMENT && !secondaryExists ->
                "El pago de la venta no se confirmó en el servidor"
            else -> "La recolección no se confirmó en el servidor"
        }

        return RemoteVerificationOutcome.RejectedAfterSync(
            "$missingDetail tras completar la cola pendiente (posible rechazo de reglas; " +
                "el SDK no conserva el motivo exacto tras reiniciar la app).",
        )
    }

    private suspend fun hasLocalPendingWrite(ref: DocumentReference): Boolean =
        runCatching {
            val snap = ref.get(Source.CACHE).await()
            snap.exists() && snap.metadata.hasPendingWrites()
        }.getOrDefault(false)

    private companion object {
        const val WAIT_FOR_PENDING_TIMEOUT_MS = 4_000L
    }
}
