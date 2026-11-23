package com.egglytics.data.repository

import com.egglytics.domain.model.PaymentMethod
import kotlinx.coroutines.flow.Flow

interface PaymentMethodRepository {
    fun observeActivePaymentMethods(): Flow<List<PaymentMethod>>

    fun observeAllPaymentMethods(): Flow<List<PaymentMethod>> = observeActivePaymentMethods()
}
