package com.egglytics.data.repository

import com.egglytics.domain.model.FeedDepletion
import com.egglytics.domain.model.FeedIngredient
import com.egglytics.domain.model.FeedLoss
import com.egglytics.domain.model.FeedLossType
import com.egglytics.domain.model.FeedPurchase
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

data class ObservedFeedPurchase(
    val purchase: FeedPurchase,
    val isPendingWrite: Boolean,
)

data class ObservedFeedDepletion(
    val depletion: FeedDepletion,
    val isPendingWrite: Boolean,
)

data class ObservedFeedLoss(
    val loss: FeedLoss,
    val isPendingWrite: Boolean,
)

data class FeedPurchaseRequest(
    val id: String,
    val fechaOperativa: LocalDate,
    val businessStartDate: LocalDate,
    val ingredient: FeedIngredient,
    val bags: Long,
    val totalCost: Long,
    val methodId: String,
    val supplier: String?,
    val note: String?,
)

data class FeedDepletionRequest(
    val id: String,
    val fechaOperativa: LocalDate,
    val businessStartDate: LocalDate,
    val ingredient: FeedIngredient,
    val bags: Long,
    val note: String?,
)

data class FeedLossRequest(
    val id: String,
    val fechaOperativa: LocalDate,
    val businessStartDate: LocalDate,
    val ingredient: FeedIngredient,
    val kg: Long,
    val type: FeedLossType,
    val reason: String,
    val note: String?,
)

interface FeedRepository {
    fun observePurchases(): Flow<List<ObservedFeedPurchase>>
    fun observeDepletions(): Flow<List<ObservedFeedDepletion>>
    fun observeLosses(): Flow<List<ObservedFeedLoss>>

    fun recordPurchase(
        request: FeedPurchaseRequest,
        onWriteError: (String) -> Unit,
    )

    fun recordDepletion(
        request: FeedDepletionRequest,
        onWriteError: (String) -> Unit,
    )

    fun recordLoss(
        request: FeedLossRequest,
        onWriteError: (String) -> Unit,
    )
}
