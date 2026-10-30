package com.example.core.monitoring

import android.content.Context
import com.example.ui.theme.NotificationHelper
import java.util.Calendar

/**
 * Intelligent Smart Alerts Engine detecting spending spikes, streak expiration risks,
 * and statistical budget overshoot projections.
 */
object SmartAlertsEngine {

    fun evaluateDailyPacing(context: Context, spentToday: Double, dailyLimit: Double) {
        if (dailyLimit > 0 && spentToday > dailyLimit) {
            NotificationHelper.triggerOverspendingAlert(context, spentToday, dailyLimit)
        }
    }

    fun evaluateCategoryThreshold(context: Context, category: String, categoryTotal: Double, categoryBudget: Double) {
        if (categoryBudget > 0 && categoryTotal >= (categoryBudget * 0.9)) {
            val pct = ((categoryTotal / categoryBudget) * 100).toInt()
            NotificationHelper.triggerBudgetThresholdAlert(context, category, pct)
        }
    }

    /**
     * Checks if user is at risk of losing their streak today.
     * E.g. If current time is after 8:00 PM and no transactions have been logged.
     */
    fun evaluateStreakRisk(context: Context, streakDays: Int, hasLoggedToday: Boolean) {
        if (streakDays > 0 && !hasLoggedToday) {
            val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            if (currentHour >= 20) { // 8 PM or later
                NotificationHelper.triggerNotification(
                    context,
                    "⚠️ Streak at Risk: $streakDays Days Active",
                    "You haven't logged any expenses today. Log a quick entry before midnight to keep your streak alive!"
                )
            }
        }
    }

    /**
     * Projects whether current daily spending pace will overshoot monthly budget.
     */
    fun predictBudgetOvershoot(context: Context, monthlyBudget: Double, spentThisMonth: Double): Boolean {
        if (monthlyBudget <= 0) return false
        val cal = Calendar.getInstance()
        val currentDay = cal.get(Calendar.DAY_OF_MONTH)
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

        if (currentDay in 5 until daysInMonth) {
            val dailyRunRate = spentThisMonth / currentDay
            val projectedMonthSpend = dailyRunRate * daysInMonth
            if (projectedMonthSpend > (monthlyBudget * 1.15)) {
                val excess = (projectedMonthSpend - monthlyBudget).toInt()
                NotificationHelper.triggerNotification(
                    context,
                    "📈 Budget Overshoot Forecast",
                    "At your current pace of ₹${dailyRunRate.toInt()}/day, you are projected to exceed your budget by ₹$excess. Consider adjusting non-essential expenses."
                )
                return true
            }
        }
        return false
    }
}
