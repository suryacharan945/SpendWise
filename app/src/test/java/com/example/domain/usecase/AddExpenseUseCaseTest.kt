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

class AddExpenseUseCaseTest {

    private lateinit var fakeRepository: FakeFinanceRepository
    private lateinit var useCase: AddExpenseUseCase

    @Before
    fun setUp() {
        fakeRepository = FakeFinanceRepository()
        useCase = AddExpenseUseCase(fakeRepository)
    }

    @Test
    fun `invoke with zero or negative amount returns Error`() = runTest {
        val result = useCase(
            userEmail = "test@domain.com",
            amount = 0.0,
            category = "Food",
            description = "Lunch"
        )

        assertTrue(result is Resource.Error)
        assertEquals("Expense amount must be greater than zero.", (result as Resource.Error).message)
    }

    @Test
    fun `invoke with empty email returns Error`() = runTest {
        val result = useCase(
            userEmail = "   ",
            amount = 100.0,
            category = "Food",
            description = "Lunch"
        )

        assertTrue(result is Resource.Error)
        assertEquals("User must be authenticated to log an expense.", (result as Resource.Error).message)
    }

    @Test
    fun `invoke with blank description returns Error`() = runTest {
        val result = useCase(
            userEmail = "test@domain.com",
            amount = 100.0,
            category = "Food",
            description = "  "
        )

        assertTrue(result is Resource.Error)
        assertEquals("Description cannot be empty.", (result as Resource.Error).message)
    }

    @Test
    fun `invoke with valid parameters persists expense and returns Success`() = runTest {
        val result = useCase(
            userEmail = "test@domain.com",
            amount = 250.0,
            category = "Food",
            description = "Sandwich and Juice"
        )

        assertTrue(result is Resource.Success)
        val data = (result as Resource.Success).data
        assertEquals(250.0, data.expense.amount, 0.001)
        assertEquals("Sandwich and Juice", data.expense.description)
        assertEquals(1, fakeRepository.expenses.size)
        assertFalse(data.fraudEvaluation.isSuspicious)
    }

    @Test
    fun `invoke detects duplicate transaction within two minutes`() = runTest {
        val now = System.currentTimeMillis()
        // Pre-insert an identical transaction 30 seconds ago
        fakeRepository.expenses.add(
            Expense(
                id = 1,
                amount = 499.0,
                category = "Electronics",
                description = "Earphones",
                date = now - 30_000L,
                userEmail = "test@domain.com"
            )
        )

        val result = useCase(
            userEmail = "test@domain.com",
            amount = 499.0,
            category = "Electronics",
            description = "Earphones",
            date = now
        )

        assertTrue(result is Resource.Success)
        val data = (result as Resource.Success).data
        assertTrue(data.fraudEvaluation.isSuspicious)
        assertTrue(data.fraudEvaluation.reasons.any { it.contains("duplicate", ignoreCase = true) })
        assertTrue(data.fraudEvaluation.riskScore >= 25)
    }

    @Test
    fun `invoke detects velocity burst of transactions in short window`() = runTest {
        val now = System.currentTimeMillis()
        // Pre-insert 3 transactions in the past 2 minutes
        for (i in 1..3) {
            fakeRepository.expenses.add(
                Expense(
                    id = i,
                    amount = 100.0 * i,
                    category = "Misc",
                    description = "Purchase $i",
                    date = now - (i * 20_000L),
                    userEmail = "test@domain.com"
                )
            )
        }

        // 4th rapid transaction
        val result = useCase(
            userEmail = "test@domain.com",
            amount = 50.0,
            category = "Misc",
            description = "Rapid 4th purchase",
            date = now
        )

        assertTrue(result is Resource.Success)
        val data = (result as Resource.Success).data
        assertTrue(data.fraudEvaluation.isSuspicious)
        assertTrue(data.fraudEvaluation.reasons.any { it.contains("velocity", ignoreCase = true) })
    }

    // In-memory fake repository implementation for fast, reliable unit tests
    private class FakeFinanceRepository : IFinanceRepository {
        val expenses = mutableListOf<Expense>()
        val goals = mutableListOf<SavingGoal>()

        override fun getUserProfile(email: String): Flow<Resource<UserProfile?>> =
            flowOf(Resource.Success(UserProfile(email = email, username = "Test", avatarUrl = "", monthlyBudgetLimit = 30000.0)))

        override suspend fun getUserProfileSync(email: String): Resource<UserProfile?> =
            Resource.Success(UserProfile(email = email, username = "Test", avatarUrl = "", monthlyBudgetLimit = 30000.0))

        override suspend fun saveUserProfile(profile: UserProfile): Resource<Unit> =
            Resource.Success(Unit)

        override fun getExpenses(email: String): Flow<Resource<List<Expense>>> =
            flowOf(Resource.Success(expenses.toList()))

        override suspend fun addExpense(expense: Expense): Resource<Expense> {
            val assigned = expense.copy(id = expenses.size + 1)
            expenses.add(assigned)
            return Resource.Success(assigned)
        }

        override suspend fun updateExpense(expense: Expense): Resource<Unit> {
            val index = expenses.indexOfFirst { it.id == expense.id }
            if (index != -1) expenses[index] = expense
            return Resource.Success(Unit)
        }

        override suspend fun deleteExpense(id: Int): Resource<Unit> {
            expenses.removeAll { it.id == id }
            return Resource.Success(Unit)
        }

        override fun getTodaySpending(email: String, startOfDay: Long): Flow<Resource<Double>> =
            flowOf(Resource.Success(expenses.filter { it.date >= startOfDay }.sumOf { it.amount }))

        override fun getMonthSpending(email: String, startOfMonth: Long): Flow<Resource<Double>> =
            flowOf(Resource.Success(expenses.filter { it.date >= startOfMonth }.sumOf { it.amount }))

        override fun getSavingGoals(email: String): Flow<Resource<List<SavingGoal>>> =
            flowOf(Resource.Success(goals.toList()))

        override suspend fun addGoal(goal: SavingGoal): Resource<SavingGoal> {
            goals.add(goal)
            return Resource.Success(goal)
        }

        override suspend fun updateGoal(goal: SavingGoal): Resource<Unit> = Resource.Success(Unit)
        override suspend fun deleteGoal(id: Int): Resource<Unit> = Resource.Success(Unit)

        override fun getInvestments(email: String): Flow<Resource<List<Investment>>> =
            flowOf(Resource.Success(emptyList()))

        override suspend fun addInvestment(investment: Investment): Resource<Investment> =
            Resource.Success(investment)

        override suspend fun updateInvestment(investment: Investment): Resource<Unit> = Resource.Success(Unit)
        override suspend fun deleteInvestment(id: Int): Resource<Unit> = Resource.Success(Unit)

        override fun getJournalEntries(email: String): Flow<Resource<List<FinancialJournalEntry>>> =
            flowOf(Resource.Success(emptyList()))

        override suspend fun addJournalEntry(entry: FinancialJournalEntry): Resource<FinancialJournalEntry> =
            Resource.Success(entry)

        override suspend fun deleteJournalEntry(id: Int): Resource<Unit> = Resource.Success(Unit)

        override suspend fun getUnsyncedExpenses(email: String): Resource<List<Expense>> =
            Resource.Success(expenses.filter { it.syncStatus != "SYNCED" })

        override suspend fun getUnsyncedGoals(email: String): Resource<List<SavingGoal>> =
            Resource.Success(goals.filter { it.syncStatus != "SYNCED" })
    }
}
