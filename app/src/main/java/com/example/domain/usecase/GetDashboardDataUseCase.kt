package com.example.domain.usecase

import com.example.core.common.Resource
import com.example.data.model.Expense
import com.example.data.model.UserProfile
import com.example.domain.repository.IFinanceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.util.Calendar

data class DashboardData(
    val profile: UserProfile?,
    val spentToday: Double,
    val spentThisMonth: Double,
    val dailyLimit: Double,
    val safeToSpendRemaining: Double,
    val healthScore: Int,
    val healthSuggestions: List<String>,
    val streakDays: Int,
    val netWorth: Double,
    val recentExpenses: List<Expense>
)

/**
 * UseCase that aggregates and computes all presentation metrics for the Dashboard.
 * Decouples health score algorithms, spending meters, and net worth formulas from Compose.
 */
class GetDashboardDataUseCase(
    private val repository: IFinanceRepository
) {
    operator fun invoke(userEmail: String): Flow<Resource<DashboardData>> {
        val startOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val startOfMonth = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        return combine(
            repository.getUserProfile(userEmail),
            repository.getTodaySpending(userEmail, startOfDay),
            repository.getMonthSpending(userEmail, startOfMonth),
            repository.getExpenses(userEmail),
            repository.getInvestments(userEmail)
        ) { profileRes, todayRes, monthRes, expensesRes, investmentsRes ->
            val profile = profileRes.getOrNull()
            val spentToday = todayRes.getOrNull() ?: 0.0
            val spentThisMonth = monthRes.getOrNull() ?: 0.0
            val expenses = expensesRes.getOrNull() ?: emptyList()
            val investments = investmentsRes.getOrNull() ?: emptyList()

            val monthlyBudget = profile?.monthlyBudgetLimit ?: 30000.0
            val dailyLimit = monthlyBudget / 30.0
            val safeRemaining = maxOf(0.0, dailyLimit - spentToday)

            // Dynamic Financial Health Score computation (0 - 100)
            val budgetRatio = if (monthlyBudget > 0) spentThisMonth / monthlyBudget else 0.5
            var score = 85
            val suggestions = mutableListOf<String>()

            if (budgetRatio > 0.9) {
                score -= 30
                suggestions.add("Critical: You have used >90% of your monthly budget.")
            } else if (budgetRatio > 0.7) {
                score -= 15
                suggestions.add("Caution: Monthly spending is approaching 75% limit.")
            } else {
                score += 5
                suggestions.add("Excellent pacing! Monthly expenses are well controlled.")
            }

            if (spentToday > dailyLimit) {
                score -= 10
                suggestions.add("Today's spending exceeded your daily safe limit.")
            }

            val totalInvestments = investments.sumOf { it.currentValue }
            val baseNetWorth = profile?.netWorth ?: 0.0
            val netWorth = maxOf(0.0, baseNetWorth + totalInvestments - spentThisMonth)

            // Dynamic Streak: count consecutive days within daily limit, starts at Day 1
            val oneDayMillis = 24 * 60 * 60 * 1000L
            val todayEpochDay = System.currentTimeMillis() / oneDayMillis
            val spendPerDay = expenses.groupBy { it.date / oneDayMillis }
                .mapValues { entry -> entry.value.sumOf { it.amount } }

            var realStreak = 1
            if (expenses.isNotEmpty()) {
                for (i in 1..30) {
                    val checkDay = todayEpochDay - i
                    val daySpend = spendPerDay[checkDay] ?: 0.0
                    if (daySpend <= dailyLimit) {
                        realStreak++
                    } else {
                        break
                    }
                }
            }
            val calculatedStreakDays = realStreak.coerceIn(1, 30)

            val data = DashboardData(
                profile = profile,
                spentToday = spentToday,
                spentThisMonth = spentThisMonth,
                dailyLimit = dailyLimit,
                safeToSpendRemaining = safeRemaining,
                healthScore = score.coerceIn(10, 100),
                healthSuggestions = suggestions,
                streakDays = calculatedStreakDays,
                netWorth = netWorth,
                recentExpenses = expenses.take(10)
            )

            Resource.Success(data)
        }
    }
}
