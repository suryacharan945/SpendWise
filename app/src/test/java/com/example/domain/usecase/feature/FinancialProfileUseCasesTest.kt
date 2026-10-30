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

class FinancialProfileUseCasesTest {

    private lateinit var fakeRepository: FakeProfileRepository
    private lateinit var getUseCase: GetFinancialProfileUseCase
    private lateinit var updateUseCase: UpdateFinancialProfileUseCase

    @Before
    fun setUp() {
        fakeRepository = FakeProfileRepository()
        getUseCase = GetFinancialProfileUseCase(fakeRepository)
        updateUseCase = UpdateFinancialProfileUseCase(fakeRepository)
    }

    @Test
    fun `GetFinancialProfileUseCase retrieves calculations from repository`() = runTest {
        val result = getUseCase("user@test.com")
        assertTrue(result is Resource.Success)
        val data = (result as Resource.Success).data
        assertEquals(50000.0, data.monthlyIncome.rupees(), 0.01)
        assertTrue(data.dailyAllowance.rupees() > 0)
    }

    @Test
    fun `UpdateFinancialProfileUseCase rejects non-positive monthly income`() = runTest {
        val result = updateUseCase(
            email = "user@test.com",
            monthlyIncome = Money.ZERO,
            mandatoryCommitments = Money.fromRupees(10000.0),
            savingsTargetPercent = 20
        )
        assertTrue(result is Resource.Error)
        assertEquals("Monthly income must be greater than zero.", (result as Resource.Error).message)
    }

    @Test
    fun `UpdateFinancialProfileUseCase rejects invalid savings percentage`() = runTest {
        val result = updateUseCase(
            email = "user@test.com",
            monthlyIncome = Money.fromRupees(60000.0),
            mandatoryCommitments = Money.fromRupees(15000.0),
            savingsTargetPercent = 75 // max is 50%
        )
        assertTrue(result is Resource.Error)
        assertEquals("Savings target percent must be between 5% and 50%.", (result as Resource.Error).message)
    }

    @Test
    fun `UpdateFinancialProfileUseCase rejects commitments exceeding income`() = runTest {
        val result = updateUseCase(
            email = "user@test.com",
            monthlyIncome = Money.fromRupees(40000.0),
            mandatoryCommitments = Money.fromRupees(35000.0), // commitments + 20% savings (8000) = 43000 > 40000
            savingsTargetPercent = 20
        )
        assertTrue(result is Resource.Error)
        assertEquals("Mandatory expenses + savings target cannot exceed monthly income.", (result as Resource.Error).message)
    }

    @Test
    fun `UpdateFinancialProfileUseCase updates profile and computes daily allowance accurately`() = runTest {
        val income = Money.fromRupees(60000.0)
        val mandatory = Money.fromRupees(20000.0)
        val savingsPct = 20 // 20% of 60k = 12k
        // Discretionary = 60k - 20k - 12k = 28k
        // Daily allowance = 28k / 30 = 933.33 rupees
        val result = updateUseCase(
            email = "user@test.com",
            monthlyIncome = income,
            mandatoryCommitments = mandatory,
            savingsTargetPercent = savingsPct,
            salaryDay = 5
        )

        assertTrue(result is Resource.Success)
        val data = (result as Resource.Success).data
        assertEquals(2800000L, data.discretionaryIncome.paise)
        assertEquals(2800000L / 30L, data.dailyAllowance.paise)
        assertEquals(5, data.salaryDay)
        assertNotNull(fakeRepository.savedProfile)
        assertEquals(60000.0, fakeRepository.savedProfile!!.monthlyIncome, 0.01)
    }

    private class FakeProfileRepository : IFinanceRepository {
        var savedProfile: UserProfile? = UserProfile(
            email = "user@test.com",
            username = "TestUser",
            monthlyIncome = 50000.0,
            monthlySavingsGoal = 10000.0
        )

        override fun getUserProfile(email: String): Flow<Resource<UserProfile?>> = flowOf(Resource.Success(savedProfile))
        override suspend fun getUserProfileSync(email: String): Resource<UserProfile?> = Resource.Success(savedProfile)
        override suspend fun saveUserProfile(profile: UserProfile): Resource<Unit> {
            savedProfile = profile
            return Resource.Success(Unit)
        }
        override fun getExpenses(email: String): Flow<Resource<List<Expense>>> = flowOf(Resource.Success(emptyList()))
        override suspend fun addExpense(expense: Expense): Resource<Expense> = Resource.Success(expense)
        override suspend fun updateExpense(expense: Expense): Resource<Unit> = Resource.Success(Unit)
        override suspend fun deleteExpense(id: Int): Resource<Unit> = Resource.Success(Unit)
        override fun getTodaySpending(email: String, startOfDay: Long): Flow<Resource<Double>> = flowOf(Resource.Success(0.0))
        override fun getMonthSpending(email: String, startOfMonth: Long): Flow<Resource<Double>> = flowOf(Resource.Success(0.0))
        override fun getSavingGoals(email: String): Flow<Resource<List<SavingGoal>>> = flowOf(Resource.Success(emptyList()))
        override suspend fun addGoal(goal: SavingGoal): Resource<SavingGoal> = Resource.Success(goal)
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
