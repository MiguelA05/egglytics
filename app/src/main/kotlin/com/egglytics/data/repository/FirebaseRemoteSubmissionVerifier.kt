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

        val primarySnap = serverPrimary.getOrNull()
        val secondarySnap = serverSecondary?.getOrNull()
        val primaryConfirmed = primarySnap != null && primarySnap.exists() && !primarySnap.metadata.hasPendingWrites()
        val secondaryConfirmed = if (secondaryRef == null) {
            true
        } else {
            secondarySnap != null && secondarySnap.exists() && !secondarySnap.metadata.hasPendingWrites()
        }
        if (primaryConfirmed && secondaryConfirmed) {
            return RemoteVerificationOutcome.Confirmed
        }

        val stillPendingAfterServerCheck = hasLocalPendingWrite(primaryRef) ||
            (secondaryRef != null && hasLocalPendingWrite(secondaryRef)) ||
            (primarySnap?.metadata?.hasPendingWrites() == true) ||
            (secondarySnap?.metadata?.hasPendingWrites() == true)
        if (stillPendingAfterServerCheck) {
            return RemoteVerificationOutcome.StillPendingLocally
        }

        val primaryExists = primarySnap?.exists() == true
        val secondaryExists = secondarySnap?.exists() ?: true
        val missingDetail = when {
            receipt.kind == SubmissionKind.SALE_WITH_PAYMENT && !primaryExists && !secondaryExists ->
                "Faltan en el servidor la venta (${receipt.primaryDocId}) y su pago (${receipt.secondaryDocId})"
            receipt.kind == SubmissionKind.SALE_WITH_PAYMENT && !primaryExists ->
                "Falta en el servidor la venta (${receipt.primaryDocId})"
            receipt.kind == SubmissionKind.SALE_WITH_PAYMENT && !secondaryExists ->
                "Falta en el servidor el pago (${receipt.secondaryDocId})"
            else -> "Falta en el servidor la recolección (${receipt.primaryDocId})"
        }

        return RemoteVerificationOutcome.Unconfirmed(
            "$missingDetail. El envío no pudo confirmarse tras reiniciar " +
                "(posible rechazo o cierre antes de encolar; el SDK no conserva el motivo).",
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
