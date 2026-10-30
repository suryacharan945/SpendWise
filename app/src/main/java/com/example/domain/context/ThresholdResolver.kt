package com.example.domain.context

import com.example.domain.model.alerts.UserNotificationPreferences
import com.example.domain.repository.Clock
import java.util.Calendar

/**
 * Single place where generic thresholds become personal thresholds based on
 * sensitivity, salary cycle timing, weekend allowances, and quiet hours.
 */
class ThresholdResolver(
    private val preferencesProvider: suspend (String) -> UserNotificationPreferences = { UserNotificationPreferences() },
    private val clock: Clock,
) {

    data class ResolvedThresholds(
        val overshootTriggerMargin: Double,      // projected overrun needed before alerting
        val anomalyZThreshold: Double,           // z-score needed to alert
        val streakRiskThreshold: Double,
        val weekendLimitMultiplier: Double,
        val inQuietHours: Boolean,
    )

    suspend fun resolve(userId: String): ResolvedThresholds {
        val prefs = preferencesProvider(userId)
        val s = prefs.sensitivity.multiplier

        val cal = Calendar.getInstance().apply { timeInMillis = clock.nowMillis() }
        val currentHour = cal.get(Calendar.HOUR_OF_DAY)
        val dayOfMonth = cal.get(Calendar.DAY_OF_MONTH)
        val maxDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

        // Salary-cycle awareness:
        // within PAYDAY_GRACE_DAYS after payday -> relax threshold (1.3x)
        // within MONTH_END_TIGHT before month end -> tighten threshold (0.75x)
        val daysSinceSalary = if (dayOfMonth >= prefs.salaryCycleDay) {
            dayOfMonth - prefs.salaryCycleDay
        } else {
            (dayOfMonth + maxDays) - prefs.salaryCycleDay
        }

        val cycleFactor = when {
            daysSinceSalary <= PAYDAY_GRACE_DAYS -> 1.3
            dayOfMonth >= (maxDays - MONTH_END_TIGHT) -> 0.75
            else -> 1.0
        }

        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        val isWeekend = (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY)

        return ResolvedThresholds(
            overshootTriggerMargin = BASE_OVERSHOOT_MARGIN * s * cycleFactor,
            anomalyZThreshold = BASE_ANOMALY_Z * s * cycleFactor,
            streakRiskThreshold = (BASE_STREAK_RISK / (s * cycleFactor)).coerceAtMost(0.95),
            weekendLimitMultiplier = if (isWeekend) prefs.weekendAllowanceMultiplier else 1.0,
            inQuietHours = prefs.quietHours.contains(currentHour),
        )
    }

    companion object {
        const val BASE_OVERSHOOT_MARGIN = 1500.0
        const val BASE_ANOMALY_Z = 2.5
        const val BASE_STREAK_RISK = 0.55
        const val PAYDAY_GRACE_DAYS = 3
        const val MONTH_END_TIGHT = 4
    }
}
