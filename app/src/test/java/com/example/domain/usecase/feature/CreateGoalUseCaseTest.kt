package com.example.domain.usecase.feature

import com.example.core.common.Resource
import com.example.data.model.*
import com.example.domain.model.Money
import com.example.domain.repository.IFinanceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class CreateGoalUseCaseTest {

    private lateinit var fakeRepository: FakeGoalRepository
    private lateinit var useCase: CreateGoalUseCase

    @Before
    fun setUp() {
        fakeRepository = FakeGoalRepository()
        useCase = CreateGoalUseCase(fakeRepository)
    }

    @Test
    fun `rejects empty or blank goal name`() = runTest {
        val result = useCase(
            userEmail = "user@test.com",
            name = "   ",
            targetAmount = Money.fromRupees(10000.0),
            targetDateMillis = System.currentTimeMillis() + 86400000L * 30
        )
        assertTrue(result is Resource.Error)
        assertEquals("Goal name cannot be empty.", (result as Resource.Error).message)
    }

    @Test
    fun `rejects non-positive target amount`() = runTest {
        val result = useCase(
            userEmail = "user@test.com",
            name = "Vacation",
            targetAmount = Money.ZERO,
            targetDateMillis = System.currentTimeMillis() + 86400000L * 30
        )
        assertTrue(result is Resource.Error)
        assertEquals("Target amount must be greater than zero.", (result as Resource.Error).message)
    }

    @Test
    fun `rejects target date in past`() = runTest {
        val result = useCase(
            userEmail = "user@test.com",
            name = "Vacation",
            targetAmount = Money.fromRupees(10000.0),
            targetDateMillis = System.currentTimeMillis() - 10000L
        )
        assertTrue(result is Resource.Error)
        assertEquals("Target date must be in the future.", (result as Resource.Error).message)
    }

    @Test
    fun `flags aggressive goals requiring over 30 percent of daily allowance`() = runTest {
        val now = System.currentTimeMillis()
        val futureDate = now + (30L * 24 * 60 * 60 * 1000L) // 30 days
        // Target: ₹60,000 in 30 days = ₹2,000/day
        // Daily allowance: ₹1,000/day -> 30% = ₹300/day. ₹2,000 > ₹300 => Aggressive!
        val result = useCase(
            userEmail = "user@test.com",
            name = "Emergency Fund",
            targetAmount = Money.fromRupees(60000.0),
            targetDateMillis = futureDate,
            currentAllowanceDaily = Money.fromRupees(1000.0)
        )

        assertTrue(result is Resource.Success)
        val data = (result as Resource.Success).data
        assertTrue(data.isAggressive)
        assertNotNull(data.warningMessage)
        assertTrue(data.warningMessage!!.contains("exceeds 30%"))
    }

    @Test
    fun `successfully creates goal within safe allowance`() = runTest {
        val now = System.currentTimeMillis()
        val futureDate = now + (180L * 24 * 60 * 60 * 1000L) // 180 days (~6 months)
        // Target: ₹18,000 in 180 days = ₹100/day
        // Daily allowance: ₹1,000/day -> 30% = ₹300/day. ₹100 <= ₹300 => Safe!
        val result = useCase(
            userEmail = "user@test.com",
            name = "New Phone",
            targetAmount = Money.fromRupees(18000.0),
            targetDateMillis = futureDate,
            currentAllowanceDaily = Money.fromRupees(1000.0)
        )

        assertTrue(result is Resource.Success)
        val data = (result as Resource.Success).data
        assertFalse(data.isAggressive)
        assertNull(data.warningMessage)
        assertEquals(1, fakeRepository.goals.size)
        assertEquals("New Phone", fakeRepository.goals[0].name)
    }

    private class FakeGoalRepository : IFinanceRepository {
        val goals = mutableListOf<SavingGoal>()

        override fun getUserProfile(email: String): Flow<Resource<UserProfile?>> = flowOf(Resource.Success(null))
        override suspend fun getUserProfileSync(email: String): Resource<UserProfile?> = Resource.Success(null)
        override suspend fun saveUserProfile(profile: UserProfile): Resource<Unit> = Resource.Success(Unit)
        override fun getExpenses(email: String): Flow<Resource<List<Expense>>> = flowOf(Resource.Success(emptyList()))
        override suspend fun addExpense(expense: Expense): Resource<Expense> = Resource.Success(expense)
        override suspend fun updateExpense(expense: Expense): Resource<Unit> = Resource.Success(Unit)
        override suspend fun deleteExpense(id: Int): Resource<Unit> = Resource.Success(Unit)
        override fun getTodaySpending(email: String, startOfDay: Long): Flow<Resource<Double>> = flowOf(Resource.Success(0.0))
        override fun getMonthSpending(email: String, startOfMonth: Long): Flow<Resource<Double>> = flowOf(Resource.Success(0.0))
        override fun getSavingGoals(email: String): Flow<Resource<List<SavingGoal>>> = flowOf(Resource.Success(goals))
        override suspend fun addGoal(goal: SavingGoal): Resource<SavingGoal> {
            goals.add(goal)
            return Resource.Success(goal)
        }
        override suspend fun updateGoal(goal: SavingGoal): Resource<Unit> = Resource.Success(Unit)
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
