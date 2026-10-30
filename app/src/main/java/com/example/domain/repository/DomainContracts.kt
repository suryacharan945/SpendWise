package com.example.domain.repository

import com.example.core.common.Resource
import com.example.domain.model.Badge
import com.example.domain.model.Category
import com.example.domain.model.FraudAlert
import com.example.domain.model.PureExpense
import java.util.Calendar
import kotlinx.coroutines.flow.Flow

/**
 * Testable Time Provider interface.
 */
interface Clock {
    fun nowMillis(): Long
    fun startOfToday(): Long
    fun currentMonthRange(): LongRange
    fun isEvening(): Boolean
    fun daysAgo(days: Int): Long
    fun currentHour(): Int = Calendar.getInstance().apply { timeInMillis = nowMillis() }.get(Calendar.HOUR_OF_DAY)
    fun daysLeftInMonth(): Int {
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis() }
        val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val curDay = cal.get(Calendar.DAY_OF_MONTH)
        return (maxDay - curDay + 1).coerceAtLeast(1)
    }
    fun dayOfWeek(): Int = Calendar.getInstance().apply { timeInMillis = nowMillis() }.get(Calendar.DAY_OF_WEEK)
    fun epochDay(): Long = nowMillis() / (24L * 60 * 60 * 1000)
}

class SystemClock : Clock {
    override fun nowMillis(): Long = System.currentTimeMillis()

    override fun startOfToday(): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    override fun currentMonthRange(): LongRange {
        val cal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis
        cal.add(Calendar.MONTH, 1)
        val end = cal.timeInMillis - 1
        return start..end
    }

    override fun isEvening(): Boolean {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return hour >= 18 // 6 PM or later
    }

    override fun daysAgo(days: Int): Long {
        return nowMillis() - (days * 24L * 60 * 60 * 1000)
    }
}

/**
 * Fraud Detector interface.
 */
interface FraudDetector {
    suspend fun inspect(userId: String, expense: PureExpense): FraudAlert?
}

/**
 * Badge and Gamification Evaluator interface.
 */
interface BadgeEvaluator {
    suspend fun onExpenseAdded(userId: String, expense: PureExpense): List<Badge>
}

/**
 * Pure Domain Expense Repository contract.
 */
interface PureExpenseRepository {
    fun observeExpenses(userId: String): Flow<List<PureExpense>>
    suspend fun addExpense(expense: PureExpense): Resource<Unit>
    suspend fun softDelete(userId: String, expenseId: String): Resource<Unit>
    suspend fun updateExpense(expense: PureExpense): Resource<Unit>
    suspend fun totalForDay(userId: String, dayStart: Long): Double
    suspend fun totalsBetween(userId: String, start: Long, end: Long): Map<Category, Double>
    suspend fun pendingPush(): List<PureExpense>
    suspend fun recentExpenses(userId: String, hours: Int = 24): List<PureExpense>
}
