package com.egglytics.data.repository

import com.egglytics.data.model.Business
import com.egglytics.domain.model.Member
import com.egglytics.domain.model.MemberRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class SessionValidationTest {

    private val activeBusiness = Business(
        id = "egglytics_default",
        name = "Granja Familiar",
        currency = "COP",
        timezone = "America/Bogota",
        startDate = LocalDate.of(2026, 11, 1),
        active = true,
    )

    private fun member(role: MemberRole, active: Boolean) = Member(
        uid = "uid_1",
        name = "Madre",
        email = "operador@example.com",
        role = role,
        active = active,
        createdAt = Instant.parse("2026-11-01T12:00:00Z"),
    )

    @Test
    fun `active operator with active business is authenticated`() {
        val result = validateMemberAndBusiness(
            uid = "uid_1",
            email = "operador@example.com",
            member = member(MemberRole.OPERATOR, active = true),
            business = activeBusiness,
        )
        assertTrue(result is SessionState.Authenticated)
        val auth = result as SessionState.Authenticated
        assertEquals(MemberRole.OPERATOR, auth.member.role)
        assertEquals("Granja Familiar", auth.business.name)
    }

    @Test
    fun `active admin with active business is authenticated`() {
        val result = validateMemberAndBusiness(
            uid = "uid_1",
            email = "admin@example.com",
            member = member(MemberRole.ADMIN, active = true),
            business = activeBusiness,
        )
        assertTrue(result is SessionState.Authenticated)
    }

    @Test
    fun `user without membership document is rejected with NO_MEMBERSHIP`() {
        val result = validateMemberAndBusiness(
            uid = "stranger_1",
            email = "stranger@example.com",
            member = null,
            business = activeBusiness,
        )
        assertTrue(result is SessionState.Rejected)
        assertEquals(RejectionReason.NO_MEMBERSHIP, (result as SessionState.Rejected).reason)
    }

    @Test
    fun `inactive member is rejected with INACTIVE_MEMBER`() {
        val result = validateMemberAndBusiness(
            uid = "inactive_1",
            email = "inactive@example.com",
            member = member(MemberRole.OPERATOR, active = false),
            business = activeBusiness,
        )
        assertTrue(result is SessionState.Rejected)
        assertEquals(RejectionReason.INACTIVE_MEMBER, (result as SessionState.Rejected).reason)
    }

    @Test
    fun `inactive business is rejected with BUSINESS_INACTIVE`() {
        val result = validateMemberAndBusiness(
            uid = "uid_1",
            email = "operador@example.com",
            member = member(MemberRole.OPERATOR, active = true),
            business = activeBusiness.copy(active = false),
        )
        assertTrue(result is SessionState.Rejected)
        assertEquals(RejectionReason.BUSINESS_INACTIVE, (result as SessionState.Rejected).reason)
    }
}
