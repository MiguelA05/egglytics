package com.egglytics.domain.usecase

import com.egglytics.domain.model.EggClassification
import com.egglytics.domain.model.Price
import java.time.LocalDate

class GetActivePriceUseCase {
    operator fun invoke(
        prices: List<Price>,
        classification: EggClassification,
        operationalDate: LocalDate,
    ): ActivePriceResult {
        val active = prices
            .asSequence()
            .filter { it.classification == classification && !it.validFrom.isAfter(operationalDate) }
            .maxWithOrNull(
                compareBy<Price> { it.validFrom }
                    .thenBy { it.createdAt }
                    .thenBy { it.id },
            )
        return if (active != null) ActivePriceResult.Found(active) else ActivePriceResult.NotFound
    }
}
