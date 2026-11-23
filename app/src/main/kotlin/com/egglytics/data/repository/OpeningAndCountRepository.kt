package com.egglytics.data.repository

import com.egglytics.domain.model.CountItem
import com.egglytics.domain.model.CountKind
import com.egglytics.domain.model.DailyClosing
import com.egglytics.domain.model.OpeningBalance
import com.egglytics.domain.model.OpeningKind
import com.egglytics.domain.model.PhysicalCount
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

data class OpeningEntryInput(
    val kind: OpeningKind,
    val key: String,
    val quantity: Long,
)

data class CountSubmitRequest(
    val fechaOperativa: LocalDate,
    val businessStartDate: LocalDate,
    val feedItems: Map<String, CountItem>,
    val cashItems: Map<String, CountItem>,
    val isClosing: Boolean,
    val closingSummary: Map<String, Long>,
    val note: String?,
)

interface OpeningAndCountRepository {
    fun observeOpenings(): Flow<List<OpeningBalance>>
    fun observeCounts(): Flow<List<PhysicalCount>>
    fun observeClosings(): Flow<List<DailyClosing>>

    fun recordOpenings(
        fechaOperativa: LocalDate,
        businessStartDate: LocalDate,
        entries: List<OpeningEntryInput>,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    )

    fun recordCountOrClosing(
        request: CountSubmitRequest,
        onSuccess: (List<String>) -> Unit,
        onError: (String) -> Unit,
    )
}
