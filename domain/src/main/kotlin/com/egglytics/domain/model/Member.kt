package com.egglytics.domain.model

import java.time.Instant

data class Member(
    val uid: String,
    val name: String,
    val email: String,
    val role: MemberRole,
    val active: Boolean,
    val createdAt: Instant,
)
