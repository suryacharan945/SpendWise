package com.example.data.dao

import androidx.room.*
import com.example.data.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profiles WHERE email = :email AND isDeleted = 0 LIMIT 1")
    fun getUserProfile(email: String): Flow<UserProfile?>

    @Query("SELECT * FROM user_profiles WHERE email = :email AND isDeleted = 0 LIMIT 1")
    suspend fun getUserProfileSync(email: String): UserProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateProfile(profile: UserProfile)
}

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses WHERE userEmail = :userEmail AND isDeleted = 0 ORDER BY date DESC")
    fun getExpenses(userEmail: String): Flow<List<Expense>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: Expense)

    @Update
    suspend fun updateExpense(expense: Expense)

    @Query("UPDATE expenses SET isDeleted = 1, updatedAt = :timestamp, syncStatus = 'PENDING_PUSH' WHERE id = :id")
    suspend fun softDeleteExpenseById(id: Int, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun deleteExpenseById(id: Int)

    @Query("SELECT SUM(amount) FROM expenses WHERE userEmail = :userEmail AND date >= :startOfDay AND isDeleted = 0")
    fun getTodaySpending(userEmail: String, startOfDay: Long): Flow<Double?>

    @Query("SELECT SUM(amount) FROM expenses WHERE userEmail = :userEmail AND date >= :startOfMonth AND isDeleted = 0")
    fun getMonthSpending(userEmail: String, startOfMonth: Long): Flow<Double?>

    @Query("SELECT * FROM expenses WHERE userEmail = :userEmail AND syncStatus != 'SYNCED'")
    suspend fun getUnsyncedExpenses(userEmail: String): List<Expense>

    @Query("""
        SELECT category, SUM(amountPaise) as totalPaise FROM expenses
        WHERE userEmail = :userEmail AND isDeleted = 0 AND date BETWEEN :start AND :end
        GROUP BY category
    """)
    suspend fun totalsBetweenGrouped(userEmail: String, start: Long, end: Long): List<CategoryTotalRow>
}

data class CategoryTotalRow(
    val category: String,
    val totalPaise: Long
)

@Dao
interface SavingGoalDao {
    @Query("SELECT * FROM saving_goals WHERE userEmail = :userEmail AND isDeleted = 0")
    fun getSavingGoals(userEmail: String): Flow<List<SavingGoal>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGoal(goal: SavingGoal)

    @Update
    suspend fun updateGoal(goal: SavingGoal)

    @Query("UPDATE saving_goals SET isDeleted = 1, updatedAt = :timestamp, syncStatus = 'PENDING_PUSH' WHERE id = :id")
    suspend fun softDeleteGoalById(id: Int, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM saving_goals WHERE id = :id")
    suspend fun deleteGoalById(id: Int)

    @Query("SELECT * FROM saving_goals WHERE userEmail = :userEmail AND syncStatus != 'SYNCED'")
    suspend fun getUnsyncedGoals(userEmail: String): List<SavingGoal>
}

@Dao
interface InvestmentDao {
    @Query("SELECT * FROM investments WHERE userEmail = :userEmail AND isDeleted = 0")
    fun getInvestments(userEmail: String): Flow<List<Investment>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvestment(investment: Investment)

    @Update
    suspend fun updateInvestment(investment: Investment)

    @Query("UPDATE investments SET isDeleted = 1, updatedAt = :timestamp, syncStatus = 'PENDING_PUSH' WHERE id = :id")
    suspend fun softDeleteInvestmentById(id: Int, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM investments WHERE id = :id")
    suspend fun deleteInvestmentById(id: Int)
}

@Dao
interface FinancialJournalEntryDao {
    @Query("SELECT * FROM financial_journal_entries WHERE userEmail = :userEmail AND isDeleted = 0 ORDER BY timestamp DESC")
    fun getJournalEntries(userEmail: String): Flow<List<FinancialJournalEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJournalEntry(entry: FinancialJournalEntry)

    @Query("UPDATE financial_journal_entries SET isDeleted = 1, updatedAt = :timestamp, syncStatus = 'PENDING_PUSH' WHERE id = :id")
    suspend fun softDeleteJournalEntryById(id: Int, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM financial_journal_entries WHERE id = :id")
    suspend fun deleteJournalEntryById(id: Int)
}
