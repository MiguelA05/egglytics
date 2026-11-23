package com.egglytics.data.repository

import com.egglytics.domain.model.Expense
import com.egglytics.domain.model.SaleReturnSummary
import com.egglytics.domain.model.Withdrawal
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

data class ObservedExpense(
    val expense: Expense,
    val isPendingWrite: Boolean,
)

data class ObservedWithdrawal(
    val withdrawal: Withdrawal,
    val isPendingWrite: Boolean,
)

data class ExpenseRequest(
    val id: String,
    val fechaOperativa: LocalDate,
    val businessStartDate: LocalDate,
    val categoryId: String,
    val amount: Long,
    val methodId: String,
    val description: String?,
    val note: String?,
)

data class WithdrawalRequest(
    val id: String,
    val fechaOperativa: LocalDate,
    val businessStartDate: LocalDate,
    val amount: Long,
    val methodId: String,
    val note: String?,
)

interface ExpenseAndWithdrawalRepository {
    fun observeExpenses(): Flow<List<ObservedExpense>>
    fun observeWithdrawals(): Flow<List<ObservedWithdrawal>>
    fun observeSaleReturns(): Flow<List<SaleReturnSummary>>

    fun recordExpense(request: ExpenseRequest, onWriteError: (String) -> Unit)
    fun recordWithdrawal(request: WithdrawalRequest, onWriteError: (String) -> Unit)
}
