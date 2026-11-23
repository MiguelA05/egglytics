package com.egglytics.domain.model

import java.time.Instant
import java.time.LocalDate

data class Expense(
    val id: String,
    val fechaOperativa: LocalDate,
    val createdAt: Instant,
    val createdBy: String,
    val categoryId: String,
    val amount: Long,
    val methodId: String,
    val description: String? = null,
    val note: String? = null,
) {
    init {
        require(categoryId.isNotBlank()) { "La categoría de gasto es obligatoria" }
        require(amount > 0L) { "El monto del gasto debe ser mayor que 0 ($amount)" }
        require(methodId.isNotBlank()) { "La forma de pago es obligatoria" }
    }
}

data class Withdrawal(
    val id: String,
    val fechaOperativa: LocalDate,
    val createdAt: Instant,
    val createdBy: String,
    val amount: Long,
    val methodId: String,
    val note: String? = null,
) {
    init {
        require(amount > 0L) { "El monto del retiro debe ser mayor que 0 ($amount)" }
        require(methodId.isNotBlank()) { "La forma de pago es obligatoria" }
    }
}

data class SaleReturnSummary(
    val id: String,
    val fechaOperativa: LocalDate,
    val createdAt: Instant,
    val creditAmount: Long,
)
