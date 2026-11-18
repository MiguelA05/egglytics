package com.egglytics.domain.model

enum class PaymentType(val code: String) {
    PAYMENT("payment"),
    REFUND("refund"),
    OVERPAYMENT_REFUND("overpayment_refund");

    companion object {
        fun fromCode(code: String): PaymentType =
            entries.firstOrNull { it.code == code }
                ?: throw IllegalArgumentException("Tipo de pago desconocido: '$code'")
    }
}
