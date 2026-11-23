package com.egglytics.data.repository

import com.egglytics.domain.model.ExpenseCategory
import kotlinx.coroutines.flow.Flow

interface ExpenseCategoryRepository {
    fun observeActiveCategories(): Flow<List<ExpenseCategory>>
}
