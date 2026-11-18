package com.egglytics.domain.model

data class PaymentMethod(
    val id: String,
    val name: String,
    val active: Boolean,
)
