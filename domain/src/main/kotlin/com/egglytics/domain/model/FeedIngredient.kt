package com.egglytics.domain.model

enum class FeedIngredient(val code: String) {
    CONCENTRATE("concentrate"),
    CORN_BRAN("corn_bran");

    companion object {
        fun fromCode(code: String): FeedIngredient =
            entries.firstOrNull { it.code == code }
                ?: throw IllegalArgumentException("Ingrediente de alimento desconocido: '$code'")
    }
}
