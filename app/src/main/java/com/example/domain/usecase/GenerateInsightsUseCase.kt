package com.example.domain.usecase

import com.example.core.common.Resource
import com.example.data.api.GeminiClient
import com.example.data.model.Expense
import com.example.data.model.SavingGoal

data class InsightsOutput(
    val quickInsights: List<String>,
    val comprehensiveReport: String,
    val isAiGenerated: Boolean
)

/**
 * Hybrid Insights Engine UseCase.
 * Combines online Gemini generative models with local deterministic financial rules as fallback.
 */
class GenerateInsightsUseCase {

    suspend operator fun invoke(
        expenses: List<Expense>,
        goals: List<SavingGoal>,
        monthlyBudget: Double
    ): Resource<InsightsOutput> {
        return try {
            val liveInsights = GeminiClient.getDashboardInsights(expenses, monthlyBudget)
            val liveReport = GeminiClient.getCoachingReport(expenses, goals, monthlyBudget)

            if (liveInsights.isNotEmpty() && liveReport.isNotBlank()) {
                Resource.Success(
                    InsightsOutput(
                        quickInsights = liveInsights,
                        comprehensiveReport = liveReport,
                        isAiGenerated = true
                    )
                )
            } else {
                Resource.Success(generateFallbackInsights(expenses, goals, monthlyBudget))
            }
        } catch (e: Exception) {
            Resource.Success(generateFallbackInsights(expenses, goals, monthlyBudget))
        }
    }

    private fun generateFallbackInsights(
        expenses: List<Expense>,
        goals: List<SavingGoal>,
        monthlyBudget: Double
    ): InsightsOutput {
        val insights = mutableListOf<String>()

        val totalSpent = expenses.sumOf { it.amount }
        val foodSpent = expenses.filter { it.category.equals("Food", ignoreCase = true) }.sumOf { it.amount }
        val shoppingSpent = expenses.filter { it.category.equals("Shopping", ignoreCase = true) }.sumOf { it.amount }

        if (totalSpent > 0) {
            val foodPct = (foodSpent / totalSpent) * 100
            if (foodPct > 35) {
                insights.add("🍔 Food accounts for ${foodPct.toInt()}% of your spending. Meal prepping could save ~₹2,500 this month.")
            }
            val shopPct = (shoppingSpent / totalSpent) * 100
            if (shopPct > 25) {
                insights.add("🛍️ Shopping is elevated at ${shopPct.toInt()}% of total debits. Consider waiting 48h before non-essential purchases.")
            }
        }

        insights.add("🎯 Maintain your daily pace to keep your savings habit streak growing.")
        insights.add("💡 Routing an extra ₹1,000 to your highest-priority goal will accelerate completion by 3 weeks.")

        val report = buildString {
            appendLine("**FinTech Financial Wellness Report**")
            appendLine()
            appendLine("• **Budget Utilization**: You have deployed ₹${totalSpent.toInt()} against your monthly limit of ₹${monthlyBudget.toInt()}.")
            if (totalSpent > monthlyBudget) {
                appendLine("• **Budget Alert**: Currently in overrun. Review discretionary categories to rebalance.")
            } else {
                appendLine("• **Savings Trajectory**: You are maintaining a healthy runway with ₹${(monthlyBudget - totalSpent).toInt()} remaining.")
            }
            appendLine()
            appendLine("• **Goal Velocity**: You have ${goals.size} active savings objectives. Ensure automated recurring deposits remain enabled.")
        }

        return InsightsOutput(
            quickInsights = insights.take(3),
            comprehensiveReport = report,
            isAiGenerated = false
        )
    }
}
