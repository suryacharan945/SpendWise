package com.example.domain.repository

import com.example.core.common.Resource
import com.example.data.model.*
import kotlinx.coroutines.flow.Flow

/**
 * Domain Repository Interface adhering to Clean Architecture principles.
 * Decouples UseCases from underlying SQLite/Room and Network implementations,
 * standardizing responses inside Resource<T>.
 */
interface IFinanceRepository {
    fun getUserProfile(email: String): Flow<Resource<UserProfile?>>
    suspend fun getUserProfileSync(email: String): Resource<UserProfile?>
    suspend fun saveUserProfile(profile: UserProfile): Resource<Unit>

    fun getExpenses(email: String): Flow<Resource<List<Expense>>>
    suspend fun addExpense(expense: Expense): Resource<Expense>
    suspend fun updateExpense(expense: Expense): Resource<Unit>
    suspend fun deleteExpense(id: Int): Resource<Unit>

    fun getTodaySpending(email: String, startOfDay: Long): Flow<Resource<Double>>
    fun getMonthSpending(email: String, startOfMonth: Long): Flow<Resource<Double>>

    fun getSavingGoals(email: String): Flow<Resource<List<SavingGoal>>>
    suspend fun addGoal(goal: SavingGoal): Resource<SavingGoal>
    suspend fun updateGoal(goal: SavingGoal): Resource<Unit>
    suspend fun deleteGoal(id: Int): Resource<Unit>

    fun getInvestments(email: String): Flow<Resource<List<Investment>>>
    suspend fun addInvestment(investment: Investment): Resource<Investment>
    suspend fun updateInvestment(investment: Investment): Resource<Unit>
    suspend fun deleteInvestment(id: Int): Resource<Unit>

    fun getJournalEntries(email: String): Flow<Resource<List<FinancialJournalEntry>>>
    suspend fun addJournalEntry(entry: FinancialJournalEntry): Resource<FinancialJournalEntry>
    suspend fun deleteJournalEntry(id: Int): Resource<Unit>

    suspend fun getUnsyncedExpenses(email: String): Resource<List<Expense>>
    suspend fun getUnsyncedGoals(email: String): Resource<List<SavingGoal>>
}
