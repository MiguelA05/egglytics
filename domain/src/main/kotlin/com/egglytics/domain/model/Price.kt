package com.egglytics.domain.model

import java.time.Instant
import java.time.LocalDate

data class Price(
    val id: String,
    val classification: EggClassification,
    val pricePerBucket: Long,
    val validFrom: LocalDate,
    val createdAt: Instant,
    val createdBy: String,
    val note: String? = null,
) {
    init {
        validate()
    }

    private fun validate() {
        require(pricePerBucket > 0L) {
            "El precio por cubeta debe ser mayor que 0 (recibido: $pricePerBucket)"
        }
    }
}
