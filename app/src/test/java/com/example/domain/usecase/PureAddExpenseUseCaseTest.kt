package com.example.domain.usecase

import com.example.core.common.AppError
import com.example.core.common.Resource
import com.example.domain.model.Badge
import com.example.domain.model.Category
import com.example.domain.model.FraudAlert
import com.example.domain.model.PureExpense
import com.example.domain.repository.BadgeEvaluator
import com.example.domain.repository.Clock
import com.example.domain.repository.FraudDetector
import com.example.domain.repository.PureExpenseRepository
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PureAddExpenseUseCaseTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeExpenseRepository
    private lateinit var clock: FakeClock
    private lateinit var useCase: PureAddExpenseUseCase

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = FakeExpenseRepository()
        clock = FakeClock()
        useCase = PureAddExpenseUseCase(
            expenseRepository = repository,
            fraudDetector = NoopFraudDetector(),
            badgeEvaluator = NoopBadgeEvaluator(),
            clock = clock,
            ioDispatcher = testDispatcher,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun validInput(amount: Double = 250.0, category: Category = Category.FOOD) =
        PureAddExpenseUseCase.Input(userId = "u1", amount = amount, category = category)

    @Test
    fun `valid expense is saved with correct timestamps`() = runTest(testDispatcher) {
        val result = useCase(validInput())

        assertTrue(result is Resource.Success)
        val saved = repository.stored.single()
        assertEquals(clock.nowMillis(), saved.createdAt)
        assertEquals(clock.nowMillis(), saved.updatedAt)
        assertFalse(saved.deleted)
    }

    @Test
    fun `zero amount returns validation error and does not persist`() = runTest(testDispatcher) {
        val result = useCase(validInput(amount = 0.0))

        assertTrue(result is Resource.Error)
        val error = (result as Resource.Error).error
        assertTrue(error is AppError.Validation)
        assertEquals("amount", (error as AppError.Validation).fieldErrors.keys.single())
        assertTrue(repository.stored.isEmpty())
    }

    @Test
    fun `negative amount rejected`() = runTest(testDispatcher) {
        val result = useCase(validInput(amount = -10.0))
        assertTrue(result is Resource.Error)
        val error = (result as Resource.Error).error
        assertTrue(error is AppError.Validation)
    }

    @Test
    fun `repository failure propagates as error without saving side effects`() = runTest(testDispatcher) {
        repository.addResult = Resource.Error(AppError.Database.WriteFailed(IOException()))
        val result = useCase(validInput())
        assertTrue(result is Resource.Error)
    }

    @Test
    fun `tags are normalized to lowercase and deduplicated`() = runTest(testDispatcher) {
        useCase(validInput().copy(tags = listOf("Food", "food", "URGENT")))
        assertEquals(listOf("food", "urgent"), repository.stored.single().tags)
    }

    @Test
    fun `fraud alert from detector surfaces in result`() = runTest(testDispatcher) {
        val alert = FraudAlert(FraudAlert.Type.DUPLICATE_CHARGE, "dup")
        useCase = PureAddExpenseUseCase(
            expenseRepository = repository,
            fraudDetector = object : FraudDetector {
                override suspend fun inspect(userId: String, expense: PureExpense) = alert
            },
            badgeEvaluator = NoopBadgeEvaluator(),
            clock = clock,
            ioDispatcher = testDispatcher
        )
        val result = useCase(validInput())
        assertTrue(result is Resource.Success)
        assertEquals(alert, (result as Resource.Success).data.fraudAlert)
    }

    // Fakes
    private class FakeExpenseRepository : PureExpenseRepository {
        val stored = mutableListOf<PureExpense>()
        var addResult: Resource<Unit> = Resource.Success(Unit)

        override fun observeExpenses(userId: String): Flow<List<PureExpense>> = flowOf(stored.toList())

        override suspend fun addExpense(expense: PureExpense): Resource<Unit> {
            stored += expense
            return addResult
        }

        override suspend fun softDelete(userId: String, expenseId: String): Resource<Unit> = Resource.Success(Unit)
        override suspend fun updateExpense(expense: PureExpense): Resource<Unit> = Resource.Success(Unit)
        override suspend fun totalForDay(userId: String, dayStart: Long): Double = 0.0
        override suspend fun totalsBetween(userId: String, start: Long, end: Long): Map<Category, Double> = emptyMap()
        override suspend fun pendingPush(): List<PureExpense> = emptyList()
        override suspend fun recentExpenses(userId: String, hours: Int): List<PureExpense> = stored.toList()
    }

    private class FakeClock(var fixedNow: Long = 1_700_000_000_000L) : Clock {
        override fun nowMillis() = fixedNow
        override fun startOfToday() = fixedNow
        override fun currentMonthRange() = fixedNow..fixedNow
        override fun isEvening() = false
        override fun daysAgo(days: Int) = fixedNow - (days * 24L * 60 * 60 * 1000L)
    }

    private class NoopFraudDetector : FraudDetector {
        override suspend fun inspect(userId: String, expense: PureExpense): FraudAlert? = null
    }

    private class NoopBadgeEvaluator : BadgeEvaluator {
        override suspend fun onExpenseAdded(userId: String, expense: PureExpense): List<Badge> = emptyList()
    }
}
