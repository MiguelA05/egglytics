package com.egglytics.data.repository

import com.egglytics.BuildConfig
import com.egglytics.data.model.Business
import com.egglytics.domain.model.Member
import com.egglytics.domain.model.MemberRole
import com.egglytics.domain.model.OperationalDate
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.tasks.await
import java.time.Instant
import javax.inject.Inject

class FirebaseSessionRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
) : SessionRepository {

    override suspend fun signIn(email: String, password: String): SessionState {
        return try {
            auth.signInWithEmailAndPassword(email.trim(), password).await()
            loadCurrentSession()
        } catch (ex: FirebaseException) {
            SessionState.Rejected(
                reason = RejectionReason.AUTH_ERROR,
                message = ex.localizedMessage ?: "No se pudo iniciar sesión.",
            )
        }
    }

    override suspend fun loadCurrentSession(): SessionState {
        val user = auth.currentUser ?: return SessionState.Unauthenticated
        val businessId = BuildConfig.BUSINESS_ID

        return try {
            val memberSnap = firestore
                .collection("businesses")
                .document(businessId)
                .collection("members")
                .document(user.uid)
                .get()
                .await()

            val member = memberSnap.toMemberOrNull()
            if (member == null || !member.active) {
                auth.signOut()
                return validateMemberAndBusiness(user.uid, user.email, member, null)
            }

            val businessSnap = firestore
                .collection("businesses")
                .document(businessId)
                .get()
                .await()

            val business = businessSnap.toBusinessOrNull()
            val result = validateMemberAndBusiness(user.uid, user.email, member, business)
            if (result is SessionState.Rejected) {
                auth.signOut()
            }
            result
        } catch (ex: FirebaseFirestoreException) {
            auth.signOut()
            val reason = if (ex.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                RejectionReason.NO_MEMBERSHIP
            } else {
                RejectionReason.AUTH_ERROR
            }
            val msg = if (reason == RejectionReason.NO_MEMBERSHIP) {
                "Acceso denegado: no tienes membresía activa en este negocio."
            } else {
                ex.localizedMessage ?: "Error al verificar la membresía."
            }
            SessionState.Rejected(reason = reason, message = msg)
        }
    }

    override suspend fun signOut() {
        auth.signOut()
    }
}

private fun DocumentSnapshot.toMemberOrNull(): Member? {
    if (!exists()) return null
    val roleRaw = getString("role") ?: return null
    val role = when (roleRaw) {
        "admin" -> MemberRole.ADMIN
        "operator" -> MemberRole.OPERATOR
        else -> return null
    }
    return Member(
        uid = id,
        name = getString("name").orEmpty(),
        email = getString("email").orEmpty(),
        role = role,
        active = getBoolean("active") == true,
        createdAt = getTimestamp("createdAt")?.toDate()?.toInstant() ?: Instant.EPOCH,
    )
}

private fun DocumentSnapshot.toBusinessOrNull(): Business? {
    if (!exists()) return null
    val startDateRaw = getString("startDate") ?: return null
    return Business(
        id = id,
        name = getString("name").orEmpty(),
        currency = getString("currency") ?: "COP",
        timezone = getString("timezone") ?: "America/Bogota",
        startDate = OperationalDate.parse(startDateRaw),
        active = getBoolean("active") == true,
    )
}
