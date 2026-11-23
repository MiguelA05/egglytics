package com.egglytics.data.repository

import com.egglytics.domain.model.Harvest
import com.egglytics.domain.model.Payment
import com.egglytics.domain.model.Reversal
import com.egglytics.domain.model.Sale
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

data class ObservedReversal(
    val reversal: Reversal,
    val isPendingWrite: Boolean,
)

interface ReversalRepository {
    fun observeReversals(): Flow<List<ObservedReversal>>

    fun reverseHarvest(
        harvest: Harvest,
        businessStartDate: LocalDate,
        reason: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    )

    fun reverseSaleWithPayment(
        sale: Sale,
        payment: Payment,
        businessStartDate: LocalDate,
        reason: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    )
}
