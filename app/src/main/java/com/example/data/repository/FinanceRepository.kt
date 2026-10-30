package com.example.data.repository

import com.example.core.common.Resource
import com.example.data.dao.*
import com.example.data.model.*
import com.example.domain.repository.IFinanceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

class FinanceRepository(
    private val userProfileDao: UserProfileDao,
    private val expenseDao: ExpenseDao,
    private val savingGoalDao: SavingGoalDao,
    private val investmentDao: InvestmentDao,
    private val financialJournalEntryDao: FinancialJournalEntryDao
) : IFinanceRepository {

    // Profiling
    override fun getUserProfile(email: String): Flow<Resource<UserProfile?>> =
        userProfileDao.getUserProfile(email)
            .map { Resource.Success(it) as Resource<UserProfile?> }
            .catch { emit(Resource.Error("Failed to fetch user profile: ${it.localizedMessage}", cause = it)) }

    override suspend fun getUserProfileSync(email: String): Resource<UserProfile?> {
        return try {
            Resource.Success(userProfileDao.getUserProfileSync(email))
        } catch (e: Exception) {
            Resource.Error("Error retrieving user profile: ${e.localizedMessage}", cause = e)
        }
    }

    override suspend fun saveUserProfile(profile: UserProfile): Resource<Unit> {
        return try {
            userProfileDao.insertOrUpdateProfile(profile.copy(updatedAt = System.currentTimeMillis()))
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error("Failed to save user profile: ${e.localizedMessage}", cause = e)
        }
    }

    // Expenses
    override fun getExpenses(email: String): Flow<Resource<List<Expense>>> =
        expenseDao.getExpenses(email)
            .map { Resource.Success(it) as Resource<List<Expense>> }
            .catch { emit(Resource.Error("Failed to stream expenses: ${it.localizedMessage}", cause = it)) }

    override suspend fun addExpense(expense: Expense): Resource<Expense> {
        return try {
            expenseDao.insertExpense(expense)
            Resource.Success(expense)
        } catch (e: Exception) {
            Resource.Error("Failed to insert expense: ${e.localizedMessage}", cause = e)
        }
    }

    override suspend fun updateExpense(expense: Expense): Resource<Unit> {
        return try {
            expenseDao.updateExpense(expense.copy(updatedAt = System.currentTimeMillis()))
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error("Failed to update expense: ${e.localizedMessage}", cause = e)
        }
    }

    override suspend fun deleteExpense(id: Int): Resource<Unit> {
        return try {
            // Soft delete to support cloud synchronization and audit history
            expenseDao.softDeleteExpenseById(id)
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error("Failed to delete expense: ${e.localizedMessage}", cause = e)
        }
    }

    override fun getTodaySpending(email: String, startOfDay: Long): Flow<Resource<Double>> =
        expenseDao.getTodaySpending(email, startOfDay)
            .map { Resource.Success(it ?: 0.0) as Resource<Double> }
            .catch { emit(Resource.Success(0.0)) }

    override fun getMonthSpending(email: String, startOfMonth: Long): Flow<Resource<Double>> =
        expenseDao.getMonthSpending(email, startOfMonth)
            .map { Resource.Success(it ?: 0.0) as Resource<Double> }
            .catch { emit(Resource.Success(0.0)) }

    // Goals
    override fun getSavingGoals(email: String): Flow<Resource<List<SavingGoal>>> =
        savingGoalDao.getSavingGoals(email)
            .map { Resource.Success(it) as Resource<List<SavingGoal>> }
            .catch { emit(Resource.Error("Failed to stream saving goals: ${it.localizedMessage}", cause = it)) }

    override suspend fun addGoal(goal: SavingGoal): Resource<SavingGoal> {
        return try {
            savingGoalDao.insertGoal(goal)
            Resource.Success(goal)
        } catch (e: Exception) {
            Resource.Error("Failed to add saving goal: ${e.localizedMessage}", cause = e)
        }
    }

    override suspend fun updateGoal(goal: SavingGoal): Resource<Unit> {
        return try {
            savingGoalDao.updateGoal(goal.copy(updatedAt = System.currentTimeMillis()))
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error("Failed to update saving goal: ${e.localizedMessage}", cause = e)
        }
    }

    override suspend fun deleteGoal(id: Int): Resource<Unit> {
        return try {
            savingGoalDao.softDeleteGoalById(id)
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error("Failed to delete saving goal: ${e.localizedMessage}", cause = e)
        }
    }

    // Investments
    override fun getInvestments(email: String): Flow<Resource<List<Investment>>> =
        investmentDao.getInvestments(email)
            .map { Resource.Success(it) as Resource<List<Investment>> }
            .catch { emit(Resource.Error("Failed to stream investments: ${it.localizedMessage}", cause = it)) }

    override suspend fun addInvestment(investment: Investment): Resource<Investment> {
        return try {
            investmentDao.insertInvestment(investment)
            Resource.Success(investment)
        } catch (e: Exception) {
            Resource.Error("Failed to add investment: ${e.localizedMessage}", cause = e)
        }
    }

    override suspend fun updateInvestment(investment: Investment): Resource<Unit> {
        return try {
            investmentDao.updateInvestment(investment.copy(updatedAt = System.currentTimeMillis()))
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error("Failed to update investment: ${e.localizedMessage}", cause = e)
        }
    }

    override suspend fun deleteInvestment(id: Int): Resource<Unit> {
        return try {
            investmentDao.softDeleteInvestmentById(id)
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error("Failed to delete investment: ${e.localizedMessage}", cause = e)
        }
    }

    // Journal
    override fun getJournalEntries(email: String): Flow<Resource<List<FinancialJournalEntry>>> =
        financialJournalEntryDao.getJournalEntries(email)
            .map { Resource.Success(it) as Resource<List<FinancialJournalEntry>> }
            .catch { emit(Resource.Error("Failed to stream journal entries: ${it.localizedMessage}", cause = it)) }

    override suspend fun addJournalEntry(entry: FinancialJournalEntry): Resource<FinancialJournalEntry> {
        return try {
            financialJournalEntryDao.insertJournalEntry(entry)
            Resource.Success(entry)
        } catch (e: Exception) {
            Resource.Error("Failed to record journal entry: ${e.localizedMessage}", cause = e)
        }
    }

    override suspend fun deleteJournalEntry(id: Int): Resource<Unit> {
        return try {
            financialJournalEntryDao.softDeleteJournalEntryById(id)
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error("Failed to delete journal entry: ${e.localizedMessage}", cause = e)
        }
    }

    // Sync helpers
    override suspend fun getUnsyncedExpenses(email: String): Resource<List<Expense>> {
        return try {
            Resource.Success(expenseDao.getUnsyncedExpenses(email))
        } catch (e: Exception) {
            Resource.Error("Failed to fetch unsynced expenses: ${e.localizedMessage}", cause = e)
        }
    }

    override suspend fun getUnsyncedGoals(email: String): Resource<List<SavingGoal>> {
        return try {
            Resource.Success(savingGoalDao.getUnsyncedGoals(email))
        } catch (e: Exception) {
            Resource.Error("Failed to fetch unsynced goals: ${e.localizedMessage}", cause = e)
        }
    }
}
