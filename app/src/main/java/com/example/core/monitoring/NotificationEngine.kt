package com.example.core.monitoring

import com.example.domain.anomaly.AnomalyDetector
import com.example.domain.context.ThresholdResolver
import com.example.domain.dispatch.AlertDispatcher
import com.example.domain.model.Category
import com.example.domain.model.alerts.AlertSeverity
import com.example.domain.model.alerts.AlertType
import com.example.domain.model.alerts.SmartAlert
import com.example.domain.prediction.SpendingPredictor
import com.example.domain.repository.Clock
import com.example.domain.repository.PureExpenseRepository
import com.example.domain.streak.StreakEngine
import java.util.Calendar

/**
 * Notifier abstraction keeping Android UI and Framework dependencies out of domain logic.
 */
interface Notifier {
    fun send(id: Int, title: String, body: String, action: String = "")
    fun wasSentToday(key: String): Boolean
    fun wasSentThisWeek(key: String): Boolean
    fun markSent(key: String, windowMillis: Long = 24 * 60 * 60 * 1000L)
}

/**
 * In-memory Notifier for unit tests and local deduplication state.
 */
class InMemoryNotifier : Notifier {
    val sentNotifications = mutableListOf<SentNotification>()
    private val sentTimestamps = mutableMapOf<String, Long>()

    data class SentNotification(val id: Int, val title: String, val body: String, val action: String)

    override fun send(id: Int, title: String, body: String, action: String) {
        sentNotifications.add(SentNotification(id, title, body, action))
    }

    override fun wasSentToday(key: String): Boolean {
        val lastSent = sentTimestamps[key] ?: return false
        return (System.currentTimeMillis() - lastSent) < (24 * 60 * 60 * 1000L)
    }

    override fun wasSentThisWeek(key: String): Boolean {
        val lastSent = sentTimestamps[key] ?: return false
        return (System.currentTimeMillis() - lastSent) < (7 * 24 * 60 * 60 * 1000L)
    }

    override fun markSent(key: String, windowMillis: Long) {
        sentTimestamps[key] = System.currentTimeMillis()
    }
}

/**
 * Behavioral Finance Smart Notification Engine.
 * Features:
 * 1. Budget Overshoot Prediction: Projects month-end spending based on current burn rate or WMA predictor.
 * 2. Streak Risk Warning: Alerts users in the evening when burn rate threatens active streak.
 * 3. Unusual Spending Detection: Evaluates category baseline deviations using heuristic or Z-score models.
 * 4. Behavioral Orchestration: Dispatches SmartAlerts through priority and engagement-aware channels.
 */
class NotificationEngine(
    private val expenseRepository: PureExpenseRepository,
    private val monthlyBudgetProvider: suspend (String) -> Double,
    private val dailyLimitProvider: suspend (String) -> Double,
    private val streakProvider: suspend (String) -> Int,
    private val categoryBaselineProvider: suspend (String, Category) -> Double,
    private val notifier: Notifier,
    private val clock: Clock,
) {

    suspend fun evaluateAll(userId: String) {
        predictOvershoot(userId)
        detectStreakRisk(userId)
        detectUnusualSpending(userId)
    }

    /**
     * 1. Budget overshoot PREDICTION — fires before it happens, not after.
     */
    suspend fun predictOvershoot(userId: String) {
        val monthlyLimit = monthlyBudgetProvider(userId)
        if (monthlyLimit <= 0.0) return

        val cal = Calendar.getInstance().apply { timeInMillis = clock.nowMillis() }
        val dayOfMonth = cal.get(Calendar.DAY_OF_MONTH).coerceAtLeast(1)
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

        val monthRange = clock.currentMonthRange()
        val totals = expenseRepository.totalsBetween(userId, monthRange.first, clock.nowMillis())
        val spent = totals.values.sum()
        val projected = (spent / dayOfMonth) * daysInMonth

        if (projected > monthlyLimit && !notifier.wasSentToday(OVERSHOOT_KEY)) {
            val overage = projected - monthlyLimit
            notifier.send(
                id = NOTIF_OVERSHOOT,
                title = "📊 Budget Forecast Alert",
                body = "At this pace you'll exceed your budget by ₹${"%,.0f".format(overage)} this month.",
                action = "OpenAnalytics"
            )
            notifier.markSent(OVERSHOOT_KEY)
        }
    }

    /**
     * 2. Streak risk — nudge BEFORE the streak breaks, in local evening time.
     */
    suspend fun detectStreakRisk(userId: String) {
        val streak = streakProvider(userId)
        if (streak < MIN_STREAK_TO_WARN) return

        val todaySpent = expenseRepository.totalForDay(userId, clock.startOfToday())
        val limit = dailyLimitProvider(userId)

        if (limit > 0.0 && todaySpent > (limit * 0.8) && clock.isEvening() && !notifier.wasSentToday(STREAK_KEY)) {
            val remaining = (limit - todaySpent).coerceAtLeast(0.0)
            notifier.send(
                id = NOTIF_STREAK,
                title = "🔥 $streak-Day Streak at Risk!",
                body = "Only ₹${"%,.0f".format(remaining)} left today to keep your no-overspending streak alive.",
                action = "OpenDashboard"
            )
            notifier.markSent(STREAK_KEY)
        }
    }

    /**
     * 3. Unusual spending — powered by category deviations against historical baseline.
     */
    suspend fun detectUnusualSpending(userId: String) {
        val last7 = expenseRepository.totalsBetween(userId, clock.daysAgo(7), clock.nowMillis())

        last7.forEach { (category, total) ->
            val expected = categoryBaselineProvider(userId, category)
            if (expected > 0.0 && total > (expected * UNUSUAL_MULTIPLIER) && total > MIN_ALERT_AMOUNT &&
                !notifier.wasSentThisWeek(unusualKey(category))
            ) {
                val percentExcess = ((total / expected) - 1.0) * 100.0
                notifier.send(
                    id = unusualKey(category).hashCode(),
                    title = "⚠️ Unusual ${category.label} Spending",
                    body = "You've spent ${"%.0f".format(percentExcess)}% more than your usual weekly average.",
                    action = "OpenCategoryInsights:${category.name}"
                )
                notifier.markSent(unusualKey(category), windowMillis = 7 * 24 * 60 * 60 * 1000L)
            }
        }
    }

    /**
     * Advanced behavioral orchestration composing detectors and dispatching SmartAlerts.
     */
    suspend fun evaluateBehavioral(
        userId: String,
        predictor: SpendingPredictor = SpendingPredictor(clock),
        anomalyDetector: AnomalyDetector = AnomalyDetector(),
        streakEngine: StreakEngine = StreakEngine(clock),
        thresholdResolver: ThresholdResolver = ThresholdResolver(clock = clock),
        dispatcher: AlertDispatcher,
        dailyTotalsMap: Map<Long, Double> = emptyMap(),
        weeklyBaselines: Map<Category, List<Double>> = emptyMap(),
    ) {
        val t = thresholdResolver.resolve(userId)
        val alerts = mutableListOf<SmartAlert>()

        // 1. Forecast with WMA
        val budget = monthlyBudgetProvider(userId)
        if (budget > 0.0 && dailyTotalsMap.isNotEmpty()) {
            val prediction = predictor.projectMonthEnd(
                dailyTotals = dailyTotalsMap,
                monthRange = 0L..30L,
                todayEpochDay = clock.nowMillis() / 86_400_000L,
                weekendMultiplier = t.weekendLimitMultiplier
            )
            val overrun = prediction.projectedAmount - budget
            if (overrun >= t.overshootTriggerMargin) {
                val severity = if (prediction.confidence > 0.75 && overrun > budget * 0.15) AlertSeverity.CRITICAL
                else if (prediction.confidence > 0.5) AlertSeverity.WARNING
                else AlertSeverity.INFO
                alerts += SmartAlert(
                    type = AlertType.BUDGET_OVERSHOOT_FORECAST,
                    severity = severity,
                    title = "📊 Budget Forecast",
                    body = "Projected month-end: ₹${"%,.0f".format(prediction.projectedAmount)}. Potential overrun: ₹${"%,.0f".format(overrun)}.",
                    confidence = prediction.confidence,
                    action = "OpenAnalytics",
                    fingerprint = "overshoot_${userId}_${prediction.projectedAmount.toInt()}"
                )
            }
        }

        // 2. Behavioral Streak
        val streak = streakProvider(userId)
        if (streak >= 3) {
            val todaySpent = expenseRepository.totalForDay(userId, clock.startOfToday())
            val dailyLimit = dailyLimitProvider(userId) * t.weekendLimitMultiplier
            val hour = Calendar.getInstance().apply { timeInMillis = clock.nowMillis() }.get(Calendar.HOUR_OF_DAY)
            val state = streakEngine.evaluate(
                streakDays = streak,
                todaySpentSoFar = todaySpent,
                dailyLimit = dailyLimit,
                typicalSpendOnThisWeekday = dailyLimit * 0.8,
                historicalOverspendRate = 0.2,
                hourOfDay = hour
            )
            if (state.breakRisk >= t.streakRiskThreshold && state.reason != null) {
                val severity = if (state.strength == StreakEngine.StreakState.Strength.IRONCLAD && state.breakRisk > 0.8) AlertSeverity.CRITICAL
                else if (state.breakRisk > 0.65) AlertSeverity.WARNING
                else AlertSeverity.INFO
                alerts += SmartAlert(
                    type = AlertType.STREAK_AT_RISK,
                    severity = severity,
                    title = "🔥 ${state.days}-Day Streak at Risk",
                    body = "${state.reason}. Keep it alive!",
                    confidence = 0.85,
                    action = "OpenDashboard",
                    fingerprint = "streak_${userId}_$streak"
                )
            }
        }

        // 3. Category Z-Score Anomaly
        if (weeklyBaselines.isNotEmpty()) {
            val thisWeek = expenseRepository.totalsBetween(userId, clock.daysAgo(7), clock.nowMillis())
            val detected = anomalyDetector.detectCategory(thisWeek, weeklyBaselines)
            detected.forEach { (category, anomaly) ->
                val z = when (anomaly) {
                    is AnomalyDetector.Anomaly.Significant -> anomaly.zScore
                    is AnomalyDetector.Anomaly.Elevated -> anomaly.zScore
                    AnomalyDetector.Anomaly.None -> 0.0
                }
                if (z >= t.anomalyZThreshold) {
                    alerts += SmartAlert(
                        type = AlertType.UNUSUAL_SPENDING,
                        severity = if (z >= 3.0) AlertSeverity.WARNING else AlertSeverity.INFO,
                        confidence = minOf(1.0, z / 4.0),
                        title = "⚠️ Unusual ${category.label} Spending",
                        body = "Significant spending surge detected in ${category.label}.",
                        action = "OpenCategoryInsights:${category.name}",
                        fingerprint = "anomaly_${userId}_${category.name}"
                    )
                }
            }
        }

        dispatcher.dispatch(userId, alerts)
    }

    companion object {
        const val OVERSHOOT_KEY = "overshoot_forecast"
        const val STREAK_KEY = "streak_risk"
        const val NOTIF_OVERSHOOT = 101
        const val NOTIF_STREAK = 102
        const val MIN_STREAK_TO_WARN = 3
        const val UNUSUAL_MULTIPLIER = 1.4
        const val MIN_ALERT_AMOUNT = 500.0

        fun unusualKey(c: Category) = "unusual_${c.name}"
    }
}
