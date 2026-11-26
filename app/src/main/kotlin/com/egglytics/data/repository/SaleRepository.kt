package com.egglytics.data.repository

import com.egglytics.domain.model.Payment
import com.egglytics.domain.model.Sale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

data class ObservedSale(
    val sale: Sale,
    val isPendingWrite: Boolean,
)

data class ObservedSalePayment(
    val payment: Payment,
    val isPendingWrite: Boolean,
)

data class SaleWithPaymentRequest(
    val sale: Sale,
    val paymentId: String,
    val methodId: String,
)

interface SaleRepository {
    fun observeSales(): Flow<List<ObservedSale>>

    fun observeSalePayments(): Flow<Map<String, List<Payment>>>

    fun observeAllSalePayments(): Flow<Map<String, List<ObservedSalePayment>>> =
        observeSalePayments().map { grouped ->
            grouped.mapValues { (_, list) ->
                list.map { ObservedSalePayment(payment = it, isPendingWrite = false) }
            }
        }

    fun observeSaleReturnIdsBySaleId(): Flow<Map<String, List<String>>> = flowOf(emptyMap())

    fun createPendingSaleWithFullPayment(
        request: SaleWithPaymentRequest,
        onWriteError: (String) -> Unit,
    )

    fun markDelivered(
        saleId: String,
        onWriteError: (String) -> Unit,
    )
}
