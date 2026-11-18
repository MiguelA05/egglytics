package com.egglytics.domain.usecase

import com.egglytics.domain.model.Price

sealed interface ActivePriceResult {
    data class Found(val price: Price) : ActivePriceResult
    data object NotFound : ActivePriceResult
}
