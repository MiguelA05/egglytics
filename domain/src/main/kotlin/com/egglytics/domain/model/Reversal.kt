package com.egglytics.domain.model

import java.time.Instant
import java.time.LocalDate

enum class ReversalTargetCollection(val firestoreValue: String) {
    OPENING_BALANCES("openingBalances"),
    HARVESTS("harvests"),
    SALES("sales"),
    PAYMENTS("payments"),
    SALE_RETURNS("saleReturns"),
    FEED_PURCHASES("feedPurchases"),
    FEED_DEPLETIONS("feedDepletions"),
    FEED_LOSSES("feedLosses"),
    EXPENSES("expenses"),
    WITHDRAWALS("withdrawals"),
    ;

    companion object {
        fun fromFirestore(value: String): ReversalTargetCollection? =
            entries.firstOrNull { it.firestoreValue == value }
    }
}

data class Reversal(
    val id: String,
    val fechaOperativa: LocalDate,
    val createdAt: Instant,
    val createdBy: String,
    val targetCollection: ReversalTargetCollection,
    val targetId: String,
    val reason: String,
    val note: String? = null,
) {
    init {
        require(targetId.isNotBlank() && !targetId.contains('/')) {
            "El identificador del documento objetivo es inválido: '$targetId'"
        }
        require(reason.trim().isNotEmpty()) {
            "El motivo de anulación es obligatorio"
        }
    }

    companion object {
        fun harvestReversalId(harvestId: String): String = "rev_harvest_$harvestId"
        fun saleReversalId(saleId: String): String = "rev_sale_$saleId"
        fun paymentReversalId(paymentId: String): String = "rev_payment_$paymentId"
    }
}
