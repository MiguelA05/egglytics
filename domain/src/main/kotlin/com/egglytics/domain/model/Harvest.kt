package com.egglytics.domain.model

import java.time.Instant
import java.time.LocalDate

data class Harvest(
    val id: String,
    val fechaOperativa: LocalDate,
    val createdAt: Instant,
    val createdBy: String,
    val graded: Map<EggClassification, Long>,
    val discarded: Long,
    val note: String? = null,
) {
    init {
        validate(graded, discarded)
    }

    companion object {
        fun validate(
            graded: Map<EggClassification, Long>,
            discarded: Long,
        ) {
            graded.forEach { (classification, count) ->
                require(count >= 0L) {
                    "La cantidad de huevos para ${classification.name} no puede ser negativa ($count)"
                }
            }
            require(discarded >= 0L) {
                "La cantidad de huevos descartados no puede ser negativa ($discarded)"
            }
            val totalEggs = graded.values.sum() + discarded
            require(totalEggs > 0L) {
                "La recolección debe registrar al menos 1 huevo apto o descartado"
            }
        }

        fun validateOperationalDate(
            fechaOperativa: LocalDate,
            businessStartDate: LocalDate,
        ) {
            require(!fechaOperativa.isBefore(businessStartDate)) {
                "La fecha operativa (${OperationalDate.format(fechaOperativa)}) " +
                    "no puede ser anterior a ${OperationalDate.format(businessStartDate)}"
            }
        }
    }
}
