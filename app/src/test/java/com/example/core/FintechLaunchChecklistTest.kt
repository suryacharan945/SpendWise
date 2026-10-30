package com.example.core

import com.example.core.sync.SyncAction
import com.example.core.sync.SyncConflictResolver
import com.example.data.dao.CategoryTotalRow
import com.example.data.model.Expense
import com.example.data.model.SavingGoal
import com.example.domain.alerts.InMemoryDedupDataStore
import com.example.domain.alerts.NotificationDeduper
import com.example.domain.model.Money
import com.example.domain.repository.Clock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class FintechLaunchChecklistTest {

    private lateinit var dedupStore: InMemoryDedupDataStore
    private lateinit var deduper: NotificationDeduper
    private lateinit var testClock: MutableTestClock

    @Before
    fun setup() {
        dedupStore = InMemoryDedupDataStore()
        deduper = NotificationDeduper(dedupStore)
        testClock = MutableTestClock(1773000000000L) // fixed baseline
    }

    // --- 1. Money Value Class Precision Tests ---

    @Test
    fun `money eliminates floating-point drift over repeated additions`() {
        // In standard Double: 0.1 added 10 times often yields 0.9999999999999999
        var doubleSum = 0.0
        var moneySum = Money.ZERO
        val tenPaise = Money.fromRupees(0.10)

        repeat(100) {
            doubleSum += 0.10
            moneySum += tenPaise
        }

        assertEquals(1000L, moneySum.paise) // Exactly 1,000 paise
        assertEquals(10.00, moneySum.rupees(), 0.0) // Exactly 10.00 rupees
        assertEquals("₹10.00", moneySum.format())
    }

    @Test
    fun `money arithmetic operations compute correctly in integer paise`() {
        val m1 = Money.fromRupees(150.50)
        val m2 = Money.fromRupees(49.50)

        val sum = m1 + m2
        val diff = m1 - m2
        val multiplied = m1 * 1.5 // 150.50 * 1.5 = 225.75

        assertEquals(20000L, sum.paise)
        assertEquals(10100L, diff.paise)
        assertEquals(22575L, multiplied.paise)
        assertEquals("₹225.75", multiplied.format())
    }

    @Test
    fun `money comparison matches integer ordering`() {
        val smaller = Money.fromRupees(99.99)
        val larger = Money.fromRupees(100.00)

        assertTrue(smaller < larger)
        assertFalse(smaller > larger)
        assertEquals(Money.fromPaise(10000L), larger)
    }

    // --- 2. Notification Deduplication Tests ---

    @Test
    fun `deduper suppresses duplicate notifications on same calendar day`() = runTest {
        val key = "streak_risk_user_1"

        val firstAttempt = deduper.shouldSend(key, testClock)
        val secondAttempt = deduper.shouldSend(key, testClock)

        assertTrue("First attempt should be allowed", firstAttempt)
        assertFalse("Second attempt on same day must be suppressed", secondAttempt)
    }

    @Test
    fun `deduper permits sending on next calendar day`() = runTest {
        val key = "budget_pace_food"

        assertTrue(deduper.shouldSend(key, testClock))
        assertFalse(deduper.shouldSend(key, testClock))

        // Advance clock by 25 hours (next calendar day)
        testClock.advanceHours(25)

        assertTrue("Should allow sending again on new calendar day", deduper.shouldSend(key, testClock))
    }

    @Test
    fun `deduper keeps independent keys isolated`() = runTest {
        val key1 = "alert_food"
        val key2 = "alert_travel"

        assertTrue(deduper.shouldSend(key1, testClock))
        assertTrue("Different key should not be blocked", deduper.shouldSend(key2, testClock))
    }

    // --- 3. Deterministic Sync Tie-Breaking Tests ---

    @Test
    fun `sync conflict resolver breaks clock-skew ties using syncVersion`() {
        val timestamp = 1773000000000L

        val local = Expense(
            id = 1,
            amount = 500.0,
            category = "Food",
            description = "Lunch",
            userEmail = "test@example.com",
            updatedAt = timestamp, // identical timestamp
            syncVersion = 1L
        )

        val remote = Expense(
            id = 1,
            amount = 550.0,
            category = "Food",
            description = "Lunch + Coffee",
            userEmail = "test@example.com",
            updatedAt = timestamp, // identical timestamp
            syncVersion = 2L // higher monotonic version
        )

        val result = SyncConflictResolver.resolveExpenseConflict(local, remote)

        assertEquals(SyncAction.OVERWRITE_WITH_REMOTE, result.action)
        assertEquals(550.0, result.resolvedEntity.amount, 0.0)
    }

    @Test
    fun `sync conflict resolver preserves soft-delete tombstone with equal timestamp`() {
        val timestamp = 1773000000000L

        val localGoal = SavingGoal(
            id = 1,
            name = "Trip",
            goalType = "Vacation",
            targetAmount = 10000.0,
            currentAmount = 5000.0,
            userEmail = "test@example.com",
            updatedAt = timestamp,
            isDeleted = false,
            syncVersion = 1L
        )

        val remoteDeletedGoal = SavingGoal(
            id = 1,
            name = "Trip",
            goalType = "Vacation",
            targetAmount = 10000.0,
            currentAmount = 5000.0,
            userEmail = "test@example.com",
            updatedAt = timestamp,
            isDeleted = true, // Tombstone
            syncVersion = 2L // Higher version
        )

        val result = SyncConflictResolver.resolveGoalConflict(localGoal, remoteDeletedGoal)

        assertEquals(SyncAction.OVERWRITE_WITH_REMOTE, result.action)
        assertTrue("Tombstone must be honored to prevent resurrecting deleted entity", result.resolvedEntity.isDeleted)
    }

    // --- 4. SQL Aggregation Row Model Test ---

    @Test
    fun `category total row accurately maps paise to Money`() {
        val row = CategoryTotalRow(category = "Food", totalPaise = 425000L)
        val money = Money.fromPaise(row.totalPaise)

        assertEquals("Food", row.category)
        assertEquals(4250.0, money.rupees(), 0.0)
        assertEquals("₹4,250.00", money.format())
    }

    // --- Test Clock Helper ---

    private class MutableTestClock(private var now: Long) : Clock {
        override fun nowMillis(): Long = now
        override fun startOfToday(): Long = now - (now % (24 * 60 * 60 * 1000L))
        override fun currentMonthRange(): LongRange = startOfToday()..(startOfToday() + 30 * 86_400_000L)
        override fun isEvening(): Boolean = false
        override fun daysAgo(days: Int): Long = now - (days * 86_400_000L)
        override fun epochDay(): Long = now / (24L * 60 * 60 * 1000L)

        fun advanceHours(hours: Int) {
            now += (hours * 60 * 60 * 1000L)
        }
    }
}
