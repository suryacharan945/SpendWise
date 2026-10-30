package com.example.core.monitoring

import com.example.core.common.Resource
import com.example.domain.model.Category
import com.example.domain.model.PureExpense
import com.example.domain.repository.Clock
import com.example.domain.repository.PureExpenseRepository
import java.util.Calendar
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class NotificationEngineTest {

    private lateinit var repository: FakeExpenseRepository
    private lateinit var notifier: InMemoryNotifier
    private lateinit var clock: FakeClock
    private lateinit var engine: NotificationEngine

    private var mockMonthlyBudget: Double = 30000.0
    private var mockDailyLimit: Double = 1000.0
    private var mockStreak: Int = 5
    private val mockCategoryBaselines = mutableMapOf<Category, Double>()

    @Before
    fun setup() {
        repository = FakeExpenseRepository()
        notifier = InMemoryNotifier()
        // Default clock: Day 10 of a month (30 days total)
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.JUNE) // June has 30 days
            set(Calendar.DAY_OF_MONTH, 10)
            set(Calendar.HOUR_OF_DAY, 20) // 8 PM (Evening)
            set(Calendar.MINUTE, 0)
        }
        clock = FakeClock(cal.timeInMillis, isEveningTime = true)

        engine = NotificationEngine(
            expenseRepository = repository,
            monthlyBudgetProvider = { mockMonthlyBudget },
            dailyLimitProvider = { mockDailyLimit },
            streakProvider = { mockStreak },
            categoryBaselineProvider = { _, cat -> mockCategoryBaselines[cat] ?: 1000.0 },
            notifier = notifier,
            clock = clock
        )
    }

    @Test
    fun `predicts overshoot when projected monthly spend exceeds budget`() = runTest {
        // Spent 15,000 in first 10 days of a 30-day month -> Run rate = 1500/day -> Projected = 45,000 > 30,000
        repository.totalsBetweenResult = mapOf(Category.FOOD to 15000.0)

        engine.predictOvershoot("user_1")

        assertEquals(1, notifier.sentNotifications.size)
        val notif = notifier.sentNotifications.first()
        assertEquals(NotificationEngine.NOTIF_OVERSHOOT, notif.id)
        assertTrue(notif.title.contains("Budget Forecast Alert"))
        assertTrue(notif.body.contains("15,000"))
    }

    @Test
    fun `does not trigger overshoot alert when spending is well within budget`() = runTest {
        // Spent 5,000 in first 10 days -> Projected = 15,000 <= 30,000
        repository.totalsBetweenResult = mapOf(Category.FOOD to 5000.0)

        engine.predictOvershoot("user_1")

        assertTrue(notifier.sentNotifications.isEmpty())
    }

    @Test
    fun `detects streak risk when evening approaches with high daily burn rate`() = runTest {
        // Daily limit 1000, spent 850 (>80%), evening time, streak 5 days
        repository.totalForDayResult = 850.0
        clock.isEveningTime = true

        engine.detectStreakRisk("user_1")

        assertEquals(1, notifier.sentNotifications.size)
        val notif = notifier.sentNotifications.first()
        assertEquals(NotificationEngine.NOTIF_STREAK, notif.id)
        assertTrue(notif.title.contains("Streak at Risk"))
        assertTrue(notif.body.contains("150 left"))
    }

    @Test
    fun `detects unusual category spend exceeding 140 percent of baseline`() = runTest {
        // Food baseline 2,000, actual last 7 days = 3,500 (>140% and >500)
        mockCategoryBaselines[Category.FOOD] = 2000.0
        repository.totalsBetweenResult = mapOf(Category.FOOD to 3500.0)

        engine.detectUnusualSpending("user_1")

        assertEquals(1, notifier.sentNotifications.size)
        val notif = notifier.sentNotifications.first()
        assertTrue(notif.title.contains("Unusual Food & Dining Spending"))
        assertTrue(notif.body.contains("75% more"))
    }

    @Test
    fun `respects notifier deduplication window`() = runTest {
        repository.totalsBetweenResult = mapOf(Category.FOOD to 15000.0)

        // Fire once
        engine.predictOvershoot("user_1")
        assertEquals(1, notifier.sentNotifications.size)

        // Fire second time within same window
        engine.predictOvershoot("user_1")
        assertEquals(1, notifier.sentNotifications.size) // No second notification
    }

    // Fakes
    private class FakeExpenseRepository : PureExpenseRepository {
        var totalForDayResult: Double = 0.0
        var totalsBetweenResult: Map<Category, Double> = emptyMap()

        override fun observeExpenses(userId: String): Flow<List<PureExpense>> = flowOf(emptyList())
        override suspend fun addExpense(expense: PureExpense): Resource<Unit> = Resource.Success(Unit)
        override suspend fun softDelete(userId: String, expenseId: String): Resource<Unit> = Resource.Success(Unit)
        override suspend fun updateExpense(expense: PureExpense): Resource<Unit> = Resource.Success(Unit)
        override suspend fun totalForDay(userId: String, dayStart: Long): Double = totalForDayResult
        override suspend fun totalsBetween(userId: String, start: Long, end: Long): Map<Category, Double> = totalsBetweenResult
        override suspend fun pendingPush(): List<PureExpense> = emptyList()
        override suspend fun recentExpenses(userId: String, hours: Int): List<PureExpense> = emptyList()
    }

    private class FakeClock(var fixedNow: Long, var isEveningTime: Boolean = false) : Clock {
        override fun nowMillis() = fixedNow
        override fun startOfToday() = fixedNow - (fixedNow % 86_400_000L)
        override fun currentMonthRange(): LongRange = (fixedNow - 10 * 86_400_000L)..(fixedNow + 20 * 86_400_000L)
        override fun isEvening() = isEveningTime
        override fun daysAgo(days: Int) = fixedNow - (days * 86_400_000L)
    }
}
