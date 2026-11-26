package com.egglytics.data.repository

import com.egglytics.domain.model.Price
import com.egglytics.domain.model.EggClassification
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

interface PriceRepository {
    suspend fun addPrice(classification: EggClassification, amount: Long, validFrom: LocalDate, uid: String)
    fun observePrices(): Flow<List<Price>>
    suspend fun getPrices(): List<Price>
}
