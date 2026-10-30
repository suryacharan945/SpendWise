package com.example.domain.usecase

import com.example.core.common.Resource
import com.example.data.model.*
import com.example.domain.repository.IFinanceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class DepositToGoalUseCaseTest {

    private lateinit var fakeRepository: FakeFinanceRepository
    private lateinit var useCase: DepositToGoalUseCase

    @Before
    fun setUp() {
        fakeRepository = FakeFinanceRepository()
        useCase = DepositToGoalUseCase(fakeRepository)
    }

    @Test
    fun `deposit with non-positive amount returns Error`() = runTest {
        val goal = SavingGoal(id = 1, name = "Car", goalType = "Vehicle", targetAmount = 10000.0, currentAmount = 2000.0, userEmail = "u@t.com")
        val result = useCase(goal, 0.0)

        assertTrue(result is Resource.Error)
        assertEquals("Deposit amount must be greater than zero.", (result as Resource.Error).message)
    }

    @Test
    fun `deposit crossing 50 percent triggers 50 percent milestone`() = runTest {
        val goal = SavingGoal(id = 1, name = "Car", goalType = "Vehicle", targetAmount = 10000.0, currentAmount = 4000.0, userEmail = "u@t.com")
        // Deposit 1500 brings to 5500 (55%)
        val result = useCase(goal, 1500.0)

        assertTrue(result is Resource.Success)
        val data = (result as Resource.Success).data
        assertEquals(5500.0, data.updatedGoal.currentAmount, 0.001)
        assertEquals(50, data.milestoneReached)
        assertEquals(25, data.earnedXp)
    }

    @Test
    fun `deposit reaching 100 percent triggers 100 percent milestone`() = runTest {
        val goal = SavingGoal(id = 1, name = "Emergency Fund", goalType = "Savings", targetAmount = 10000.0, currentAmount = 8000.0, userEmail = "u@t.com")
        // Deposit 2000 brings to 10000 (100%)
        val result = useCase(goal, 2000.0)

        assertTrue(result is Resource.Success)
        val data = (result as Resource.Success).data
        assertEquals(10000.0, data.updatedGoal.currentAmount, 0.001)
        assertEquals(100, data.milestoneReached)
        assertEquals("PENDING_PUSH", data.updatedGoal.syncStatus)
    }

    private class FakeFinanceRepository : IFinanceRepository {
        var lastUpdatedGoal: SavingGoal? = null

        override fun getUserProfile(email: String): Flow<Resource<UserProfile?>> = flowOf(Resource.Success(null))
        override suspend fun getUserProfileSync(email: String): Resource<UserProfile?> = Resource.Success(null)
        override suspend fun saveUserProfile(profile: UserProfile): Resource<Unit> = Resource.Success(Unit)

        override fun getExpenses(email: String): Flow<Resource<List<Expense>>> = flowOf(Resource.Success(emptyList()))
        override suspend fun addExpense(expense: Expense): Resource<Expense> = Resource.Success(expense)
        override suspend fun updateExpense(expense: Expense): Resource<Unit> = Resource.Success(Unit)
        override suspend fun deleteExpense(id: Int): Resource<Unit> = Resource.Success(Unit)

        override fun getTodaySpending(email: String, startOfDay: Long): Flow<Resource<Double>> = flowOf(Resource.Success(0.0))
        override fun getMonthSpending(email: String, startOfMonth: Long): Flow<Resource<Double>> = flowOf(Resource.Success(0.0))

        override fun getSavingGoals(email: String): Flow<Resource<List<SavingGoal>>> = flowOf(Resource.Success(emptyList()))
        override suspend fun addGoal(goal: SavingGoal): Resource<SavingGoal> = Resource.Success(goal)
        override suspend fun updateGoal(goal: SavingGoal): Resource<Unit> {
            lastUpdatedGoal = goal
            return Resource.Success(Unit)
        }
        override suspend fun deleteGoal(id: Int): Resource<Unit> = Resource.Success(Unit)

        override fun getInvestments(email: String): Flow<Resource<List<Investment>>> = flowOf(Resource.Success(emptyList()))
        override suspend fun addInvestment(investment: Investment): Resource<Investment> = Resource.Success(investment)
        override suspend fun updateInvestment(investment: Investment): Resource<Unit> = Resource.Success(Unit)
        override suspend fun deleteInvestment(id: Int): Resource<Unit> = Resource.Success(Unit)

        override fun getJournalEntries(email: String): Flow<Resource<List<FinancialJournalEntry>>> = flowOf(Resource.Success(emptyList()))
        override suspend fun addJournalEntry(entry: FinancialJournalEntry): Resource<FinancialJournalEntry> = Resource.Success(entry)
        override suspend fun deleteJournalEntry(id: Int): Resource<Unit> = Resource.Success(Unit)

        override suspend fun getUnsyncedExpenses(email: String): Resource<List<Expense>> = Resource.Success(emptyList())
        override suspend fun getUnsyncedGoals(email: String): Resource<List<SavingGoal>> = Resource.Success(emptyList())
    }
}
