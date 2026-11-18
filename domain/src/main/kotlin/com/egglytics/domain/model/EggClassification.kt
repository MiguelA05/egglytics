package com.egglytics.domain.model

enum class EggClassification(val code: String) {
    B("B"),
    A("A"),
    AA("AA"),
    AAA("AAA"),
    JUMBO("JUMBO");

    companion object {
        fun fromCode(code: String): EggClassification =
            entries.firstOrNull { it.code == code }
                ?: throw IllegalArgumentException("Clasificación de huevo desconocida: '$code'")
    }
}
