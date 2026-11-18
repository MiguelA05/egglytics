package com.egglytics.domain.model

enum class MemberRole(val code: String) {
    ADMIN("admin"),
    OPERATOR("operator");

    companion object {
        fun fromCode(code: String): MemberRole =
            entries.firstOrNull { it.code == code }
                ?: throw IllegalArgumentException("Rol de miembro desconocido: '$code'")
    }
}
