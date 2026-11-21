package com.egglytics.data.model

import java.time.LocalDate

data class Business(
    val id: String,
    val name: String,
    val currency: String,
    val timezone: String,
    val startDate: LocalDate,
    val active: Boolean,
)
