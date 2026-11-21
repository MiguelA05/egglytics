package com.egglytics.data.repository

import com.egglytics.domain.model.EggClassification
import com.egglytics.domain.model.Payment
import com.egglytics.domain.model.Price
import com.egglytics.domain.model.Sale
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

data class ObservedSale(
    val sale: Sale,
    val isPendingWrite: Boolean,
)

data class SaleWithPaymentRequest(
    val saleId: String,
    val paymentId: String,
    val fechaOperativa: LocalDate,
    val businessStartDate: LocalDate,
    val classification: EggClassification,
    val buckets: Long,
    val activePrice: Price,
    val methodId: String,
    val note: String?,
)

interface SaleRepository {
    fun observeSales(): Flow<List<ObservedSale>>

    fun observeSalePayments(): Flow<Map<String, List<Payment>>>

    fun createPendingSaleWithFullPayment(
        request: SaleWithPaymentRequest,
        onWriteError: (String) -> Unit,
    )

    fun markDelivered(
        saleId: String,
        onWriteError: (String) -> Unit,
    )
}
