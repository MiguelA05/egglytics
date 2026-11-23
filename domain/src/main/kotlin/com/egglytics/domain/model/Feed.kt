package com.egglytics.domain.model

import java.time.Instant
import java.time.LocalDate

const val KG_PER_FEED_BAG: Long = 40L

enum class FeedLossType(val code: String) {
    WASTE("waste"),
    LOSS("loss"),
    OTHER("other"),
    ;

    companion object {
        fun fromCode(code: String): FeedLossType? =
            entries.firstOrNull { it.code == code }
    }
}

data class FeedPurchase(
    val id: String,
    val fechaOperativa: LocalDate,
    val createdAt: Instant,
    val createdBy: String,
    val ingredient: FeedIngredient,
    val bags: Long,
    val totalKg: Long = bags * KG_PER_FEED_BAG,
    val totalCost: Long,
    val methodId: String,
    val supplier: String? = null,
    val note: String? = null,
) {
    init {
        require(bags > 0L) { "La cantidad de bultos debe ser mayor que 0 ($bags)" }
        require(totalKg == bags * KG_PER_FEED_BAG) {
            "Los kg totales ($totalKg) deben ser bultos * $KG_PER_FEED_BAG"
        }
        require(totalCost > 0L) { "El costo total debe ser mayor que 0 ($totalCost)" }
        require(methodId.isNotBlank()) { "La forma de pago es obligatoria" }
    }
}

data class FeedDepletion(
    val id: String,
    val fechaOperativa: LocalDate,
    val createdAt: Instant,
    val createdBy: String,
    val ingredient: FeedIngredient,
    val bags: Long,
    val kg: Long = bags * KG_PER_FEED_BAG,
    val note: String? = null,
) {
    init {
        require(bags > 0L) { "La cantidad de bultos agotados debe ser mayor que 0 ($bags)" }
        require(kg == bags * KG_PER_FEED_BAG) {
            "Los kg agotados ($kg) deben ser bultos * $KG_PER_FEED_BAG"
        }
    }
}

data class FeedLoss(
    val id: String,
    val fechaOperativa: LocalDate,
    val createdAt: Instant,
    val createdBy: String,
    val ingredient: FeedIngredient,
    val kg: Long,
    val type: FeedLossType,
    val reason: String,
    val note: String? = null,
) {
    init {
        require(kg > 0L) { "Los kg perdidos deben ser mayores que 0 ($kg)" }
        require(reason.trim().isNotEmpty()) { "El motivo de pérdida es obligatorio" }
    }
}
