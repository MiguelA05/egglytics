package com.egglytics.data.repository

import com.egglytics.domain.model.EggClassification
import com.egglytics.domain.model.Harvest
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

data class ObservedHarvest(
    val harvest: Harvest,
    val isPendingWrite: Boolean,
)

interface HarvestRepository {
    fun observeHarvests(): Flow<List<ObservedHarvest>>

    fun recordHarvest(
        harvestId: String,
        fechaOperativa: LocalDate,
        graded: Map<EggClassification, Long>,
        discarded: Long,
        note: String?,
        onWriteError: (String) -> Unit,
    )
}
