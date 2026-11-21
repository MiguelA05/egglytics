package com.egglytics.data.repository

import com.egglytics.domain.model.Price
import kotlinx.coroutines.flow.Flow

interface PriceRepository {
    fun observePrices(): Flow<List<Price>>
    suspend fun getPrices(): List<Price>
}
