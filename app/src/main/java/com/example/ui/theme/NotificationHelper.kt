package com.example.ui.theme

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object NotificationHelper {
    private const val CHANNEL_ID = "budget_alerts_channel"
    private const val CHANNEL_NAME = "Budget Alerts and Warnings"
    private const val CHANNEL_DESC = "Alerts on budget threshold violations and daily spending health status."

    fun registerNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESC
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    @SuppressLint("MissingPermission", "NotificationPermission")
    fun triggerNotification(context: Context, title: String, content: String) {
        registerNotificationChannel(context)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        try {
            val manager = NotificationManagerCompat.from(context)
            // Simple check or try-catch for Manifest.permission.POST_NOTIFICATIONS
            manager.notify((1..1000).random(), builder.build())
        } catch (e: SecurityException) {
            // Gracefully catch security permissions exception on Android 13+
        }
    }

    fun triggerBudgetThresholdAlert(context: Context, category: String, percent: Int) {
        triggerNotification(
            context,
            "⚠️ Budget Alert: $category ${percent}% Used",
            "You have consumed ${percent}% of your allocated $category budget for this month."
        )
    }

    fun triggerOverspendingAlert(context: Context, spentToday: Double, dailyLimit: Double) {
        triggerNotification(
            context,
            "🚨 Daily Overspending Warning!",
            "You spent ₹${String.format("%.0f", spentToday)} today, exceeding your daily safe limit of ₹${String.format("%.0f", dailyLimit)}."
        )
    }

    fun triggerWeekendAlert(context: Context) {
        triggerNotification(
            context,
            "🎉 Weekend Spending Alert",
            "Weekend transactions are tracking higher than weekdays. Stay mindful of your dining out budget!"
        )
    }

    fun triggerGoalMilestoneAlert(context: Context, goalName: String, percent: Int) {
        triggerNotification(
            context,
            "🎯 Goal Milestone Reached!",
            "Awesome work! You've achieved ${percent}% of your '$goalName' savings goal."
        )
    }

    fun triggerStreakAlert(context: Context, streakDays: Int) {
        triggerNotification(
            context,
            "🔥 Streak Alert: $streakDays Days Strong!",
            "You have maintained your disciplined budget streak for $streakDays consecutive days. Keep it alive!"
        )
    }
}
