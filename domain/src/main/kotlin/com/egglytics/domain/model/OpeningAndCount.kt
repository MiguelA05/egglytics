package com.egglytics.domain.model

import java.time.Instant
import java.time.LocalDate

enum class OpeningKind(val code: String) {
    EGGS("eggs"),
    FEED("feed"),
    CASH("cash"),
    CUSTOMER_DEBT("customerDebt"),
    ;

    companion object {
        fun fromCode(code: String): OpeningKind? =
            entries.firstOrNull { it.code == code }
    }
}

enum class CountKind(val code: String) {
    EGGS("eggs"),
    FEED("feed"),
    CASH("cash"),
    ;

    companion object {
        fun fromCode(code: String): CountKind? =
            entries.firstOrNull { it.code == code }
    }
}

data class OpeningBalance(
    val id: String,
    val fechaOperativa: LocalDate,
    val createdAt: Instant,
    val createdBy: String,
    val kind: OpeningKind,
    val key: String,
    val quantity: Long,
    val note: String? = null,
) {
    init {
        require(key.isNotBlank()) { "La clave de apertura es obligatoria" }
        require(quantity >= 0L) { "La cantidad inicial no puede ser negativa ($quantity)" }
    }
}

data class CountItem(
    val expected: Long,
    val physical: Long,
    val difference: Long = physical - expected,
    val reason: String? = null,
) {
    init {
        require(physical >= 0L) { "El valor contado no puede ser negativo ($physical)" }
        require(difference == physical - expected) {
            "La diferencia ($difference) debe ser contado - esperado (${physical - expected})"
        }
        if (difference != 0L) {
            require(!reason.isNullOrBlank()) {
                "Se requiere motivo cuando la diferencia es distinta de cero ($difference)"
            }
        }
    }
}

data class PhysicalCount(
    val id: String,
    val fechaOperativa: LocalDate,
    val createdAt: Instant,
    val createdBy: String,
    val kind: CountKind,
    val items: Map<String, CountItem>,
    val isClosing: Boolean,
    val note: String? = null,
) {
    init {
        require(items.isNotEmpty()) { "El conteo debe incluir al menos una clave" }
    }
}

data class DailyClosing(
    val id: String,
    val fechaOperativa: LocalDate,
    val createdAt: Instant,
    val createdBy: String,
    val summary: Map<String, Long>,
    val countIds: List<String>,
    val note: String? = null,
)
