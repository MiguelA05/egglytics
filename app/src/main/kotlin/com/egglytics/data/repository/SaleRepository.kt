package com.egglytics.data.repository

import com.egglytics.domain.model.EggClassification
import com.egglytics.domain.model.Price
import com.egglytics.domain.model.Sale
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

data class ObservedSale(
    val sale: Sale,
    val isPendingWrite: Boolean,
)

interface SaleRepository {
    fun observeSales(): Flow<List<ObservedSale>>

    fun createPendingSale(
        saleId: String,
        fechaOperativa: LocalDate,
        businessStartDate: LocalDate,
        classification: EggClassification,
        buckets: Long,
        activePrice: Price,
        note: String?,
        onWriteError: (String) -> Unit,
    )

    fun markDelivered(
        saleId: String,
        onWriteError: (String) -> Unit,
    )
}
