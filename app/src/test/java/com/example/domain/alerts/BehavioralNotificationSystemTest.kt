package com.example.domain.alerts

import com.example.core.monitoring.InMemoryNotifier
import com.example.domain.anomaly.AnomalyDetector
import com.example.domain.context.ThresholdResolver
import com.example.domain.dispatch.AlertDispatcher
import com.example.domain.learning.InMemoryEngagementTracker
import com.example.domain.model.alerts.AlertSeverity
import com.example.domain.model.alerts.AlertType
import com.example.domain.model.alerts.EngagementRecord
import com.example.domain.model.alerts.SmartAlert
import com.example.domain.model.alerts.TimeWindow
import com.example.domain.model.alerts.UserNotificationPreferences
import com.example.domain.prediction.SpendingPredictor
import com.example.domain.repository.Clock
import com.example.domain.streak.StreakEngine
import java.util.Calendar
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class BehavioralNotificationSystemTest {

    private lateinit var clock: TestClock

    @Before
    fun setup() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.JUNE) // June 2026
            set(Calendar.DAY_OF_MONTH, 15)
            set(Calendar.HOUR_OF_DAY, 20) // 8 PM
            set(Calendar.MINUTE, 0)
        }
        clock = TestClock(cal.timeInMillis)
    }

    // --- 1. SpendingPredictor Tests ---

    @Test
    fun `predictor handles steady zero-variance spender with high confidence`() {
        val predictor = SpendingPredictor(clock)
        // 14 days of exactly 500/day
        val dailyTotals = (1L..14L).associateWith { 500.0 }
        val result = predictor.projectMonthEnd(
            dailyTotals = dailyTotals,
            monthRange = 1L..30L,
            todayEpochDay = 14L,
            weekendMultiplier = 1.0
        )

        assertEquals(0.0, result.variance, 0.01)
        assertTrue(result.confidence > 0.8)
        assertEquals(15000.0, result.projectedAmount, 0.01) // 30 days * 500
    }

    @Test
    fun `predictor models weekend spending rate differently from weekdays`() {
        val predictor = SpendingPredictor(clock)
        // Find epochDays for known weekday vs weekend
        val friday = 20619L // Known day
        val dailyTotals = (friday - 13..friday).associateWith { 100.0 }
        val result = predictor.projectMonthEnd(
            dailyTotals = dailyTotals,
            monthRange = (friday - 13)..(friday + 16),
            todayEpochDay = friday,
            weekendMultiplier = 1.5
        )

        assertTrue(result.projectedAmount > 0.0)
        assertTrue(result.sampleSize >= 10)
    }

    // --- 2. AnomalyDetector Tests ---

    @Test
    fun `anomaly detector identifies significant surge when spend is 3 standard deviations above mean`() {
        val detector = AnomalyDetector()
        val historical = listOf(100.0, 110.0, 95.0, 105.0, 100.0, 90.0)
        // Mean = 100, StdDev ≈ 6.5. 200 is >15 standard deviations above mean
        val anomaly = detector.detect(currentPeriodTotal = 200.0, historicalTotals = historical)

        assertTrue(anomaly is AnomalyDetector.Anomaly.Significant)
        val significant = anomaly as AnomalyDetector.Anomaly.Significant
        assertTrue(significant.zScore >= 3.0)
    }

    @Test
    fun `anomaly detector zero-baseline edge case fires on absolute delta`() {
        val detector = AnomalyDetector()
        // Historical is near-zero
        val historical = listOf(0.0, 0.0, 0.0, 0.0, 0.0)
        val anomaly = detector.detect(currentPeriodTotal = 500.0, historicalTotals = historical)

        assertTrue(anomaly is AnomalyDetector.Anomaly.Significant)
    }

    @Test
    fun `small sample baseline suppresses false positives`() {
        val detector = AnomalyDetector()
        // Only 2 baseline samples (<4 minimum)
        val anomaly = detector.detect(currentPeriodTotal = 5000.0, historicalTotals = listOf(100.0, 120.0))

        assertEquals(AnomalyDetector.Anomaly.None, anomaly)
    }

    // --- 3. StreakEngine Tests ---

    @Test
    fun `streak engine rewards established streaks with lower break risk`() {
        val engine = StreakEngine(clock)
        val fragile = engine.evaluate(
            streakDays = 2,
            todaySpentSoFar = 400.0,
            dailyLimit = 500.0,
            typicalSpendOnThisWeekday = 350.0,
            historicalOverspendRate = 0.3,
            hourOfDay = 20
        )
        val ironclad = engine.evaluate(
            streakDays = 35,
            todaySpentSoFar = 400.0,
            dailyLimit = 500.0,
            typicalSpendOnThisWeekday = 350.0,
            historicalOverspendRate = 0.3,
            hourOfDay = 20
        )

        assertEquals(StreakEngine.StreakState.Strength.FRAGILE, fragile.strength)
        assertEquals(StreakEngine.StreakState.Strength.IRONCLAD, ironclad.strength)
        assertTrue(ironclad.breakRisk < fragile.breakRisk)
    }

    // --- 4. ThresholdResolver Tests ---

    @Test
    fun `threshold resolver respects quiet hours and adjusts for salary cycle`() = runTest {
        val resolver = ThresholdResolver(
            preferencesProvider = {
                UserNotificationPreferences(
                    sensitivity = UserNotificationPreferences.Sensitivity.MEDIUM,
                    quietHours = TimeWindow(22, 8),
                    salaryCycleDay = 1
                )
            },
            clock = clock
        )

        val thresholds = resolver.resolve("user_1")
        assertNotNull(thresholds)
        assertFalse(thresholds.inQuietHours) // 20:00 is outside 22:00..08:00
        assertTrue(thresholds.overshootTriggerMargin > 0)
    }

    // --- 5. EngagementTracker & AlertDispatcher Tests ---

    @Test
    fun `dispatcher suppresses alert type when user has 70 percent or higher dismissal rate`() = runTest {
        val notifier = InMemoryNotifier()
        val tracker = InMemoryEngagementTracker(clock)
        val dispatcher = AlertDispatcher(
            notifier = notifier,
            engagementTracker = tracker,
            clock = clock
        )

        // Seed 5 DISMISSED records for UNUSUAL_SPENDING
        for (i in 1..5) {
            tracker.record(
                EngagementRecord(
                    id = "rec_$i",
                    userId = "user_1",
                    alertType = AlertType.UNUSUAL_SPENDING,
                    severity = AlertSeverity.WARNING,
                    fingerprint = "fp_$i",
                    outcome = EngagementRecord.Outcome.DISMISSED,
                    timestamp = clock.nowMillis() - (i * 1000L)
                )
            )
        }

        val alert = SmartAlert(
            type = AlertType.UNUSUAL_SPENDING,
            severity = AlertSeverity.WARNING,
            title = "Unusual Dining Spend",
            body = "Higher than normal.",
            confidence = 0.9,
            fingerprint = "fp_new_1"
        )

        dispatcher.dispatch("user_1", listOf(alert))

        // Suppressed by learning loop!
        assertTrue(notifier.sentNotifications.isEmpty())
    }

    @Test
    fun `dispatcher routes CRITICAL immediately and batches INFO into digest`() = runTest {
        val notifier = InMemoryNotifier()
        val tracker = InMemoryEngagementTracker(clock)
        val dispatcher = AlertDispatcher(
            notifier = notifier,
            engagementTracker = tracker,
            clock = clock
        )

        val criticalAlert = SmartAlert(
            type = AlertType.BUDGET_OVERSHOOT_FORECAST,
            severity = AlertSeverity.CRITICAL,
            title = "Critical Budget Alert",
            body = "Major overspend imminent!",
            confidence = 0.9,
            fingerprint = "fp_crit"
        )
        val infoAlert = SmartAlert(
            type = AlertType.SAVINGS_OPPORTUNITY,
            severity = AlertSeverity.INFO,
            title = "Savings Opportunity",
            body = "You saved 10% on utilities.",
            confidence = 0.8,
            fingerprint = "fp_info"
        )

        dispatcher.dispatch("user_1", listOf(criticalAlert, infoAlert))

        // Critical was pushed immediately
        assertEquals(1, notifier.sentNotifications.size)
        assertEquals("Critical Budget Alert", notifier.sentNotifications.first().title)

        // Info was queued into digest
        assertEquals(1, dispatcher.infoQueue.size)

        // Flush digest delivers summary
        val flushed = dispatcher.flushDigest("user_1")
        assertEquals(1, flushed.size)
        assertEquals(2, notifier.sentNotifications.size) // Critical + Digest
        assertTrue(notifier.sentNotifications.last().title.contains("Daily Summary"))
    }

    private class TestClock(private var now: Long) : Clock {
        override fun nowMillis() = now
        override fun startOfToday() = now - (now % 86_400_000L)
        override fun currentMonthRange() = (now - 14 * 86_400_000L)..(now + 15 * 86_400_000L)
        override fun isEvening() = true
        override fun daysAgo(days: Int) = now - (days * 86_400_000L)
    }
}
