package com.egglytics.data.repository

import com.egglytics.data.model.Business
import com.egglytics.domain.model.Member

enum class RejectionReason {
    NO_MEMBERSHIP,
    INACTIVE_MEMBER,
    BUSINESS_INACTIVE,
    AUTH_ERROR,
}

sealed interface SessionState {
    data object Unauthenticated : SessionState

    data class Authenticated(
        val uid: String,
        val email: String?,
        val member: Member,
        val business: Business,
    ) : SessionState

    data class Rejected(
        val reason: RejectionReason,
        val message: String,
    ) : SessionState
}

interface SessionRepository {
    suspend fun signIn(email: String, password: String): SessionState
    suspend fun loadCurrentSession(): SessionState
    suspend fun signOut()
}

fun validateMemberAndBusiness(
    uid: String,
    email: String?,
    member: Member?,
    business: Business?,
): SessionState {
    if (member == null) {
        return SessionState.Rejected(
            reason = RejectionReason.NO_MEMBERSHIP,
            message = "Usuario autenticado sin membresía en este negocio.",
        )
    }
    if (!member.active) {
        return SessionState.Rejected(
            reason = RejectionReason.INACTIVE_MEMBER,
            message = "Tu membresía está desactivada. Contacta al administrador.",
        )
    }
    if (business == null || !business.active) {
        return SessionState.Rejected(
            reason = RejectionReason.BUSINESS_INACTIVE,
            message = "El negocio no está disponible o se encuentra inactivo.",
        )
    }
    return SessionState.Authenticated(
        uid = uid,
        email = email,
        member = member,
        business = business,
    )
}
