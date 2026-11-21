package com.egglytics.domain.model

import java.time.Instant
import java.time.LocalDate

data class Payment(
    val id: String,
    val fechaOperativa: LocalDate,
    val createdAt: Instant,
    val createdBy: String,
    val saleId: String? = null,
    val openingDebtId: String? = null,
    val amount: Long,
    val methodId: String,
    val type: PaymentType,
    val saleReturnId: String? = null,
    val reason: String? = null,
    val note: String? = null,
) {
    init {
        require(amount > 0L) { "El monto del pago debe ser mayor que 0 ($amount)" }
        require(methodId.isNotBlank()) { "La forma de pago es obligatoria" }
        require((saleId != null) != (openingDebtId != null)) {
            "El pago debe asociarse exactamente a una venta o a una deuda de apertura"
        }
    }

    companion object {
        fun forFullSalePayment(
            id: String,
            sale: Sale,
            methodId: String,
        ): Payment {
            require(methodId.isNotBlank()) { "La forma de pago es obligatoria" }
            return Payment(
                id = id,
                fechaOperativa = sale.fechaOperativa,
                createdAt = sale.createdAt,
                createdBy = sale.createdBy,
                saleId = sale.id,
                amount = sale.total,
                methodId = methodId,
                type = PaymentType.PAYMENT,
                note = sale.note,
            )
        }
    }
}
