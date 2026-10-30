package com.example.domain.action

import com.example.core.common.AppError
import com.example.core.common.Resource
import com.example.core.common.toAppError
import com.example.domain.model.Category
import com.example.domain.model.coach.Insight
import kotlin.coroutines.cancellation.CancellationException

sealed class CoachActionRequest {
    data class SetBudget(val category: Category, val monthlyLimit: Double) : CoachActionRequest()
    data class ReduceCategoryLimit(val category: Category, val reduceByPercent: Int) : CoachActionRequest()
    data class CreateSavingsGoal(val name: String, val targetAmount: Double, val deadlineMonths: Int) : CoachActionRequest()
    data object ReviewExpenses : CoachActionRequest()
}

data class SavingsPlan(
    val targetAmount: Double,
    val durationMonths: Int,
    val monthlyAmount: Double,
)

interface RecommendationEngine {
    fun suggestSavingsPlan(targetAmount: Double, deadlineMonths: Int, monthlySurplus: Double = 0.0): SavingsPlan =
        SavingsPlan(
            targetAmount = targetAmount,
            durationMonths = deadlineMonths.coerceAtLeast(1),
            monthlyAmount = targetAmount / deadlineMonths.coerceAtLeast(1)
        )
}

class DefaultRecommendationEngine : RecommendationEngine

interface BudgetRepository {
    suspend fun setCategoryLimit(userId: String, category: Category, monthlyLimit: Double)
    suspend fun categoryLimitOnce(userId: String, category: Category): Double
    suspend fun monthlyBudgetOnce(userId: String): Double?
    suspend fun dailyLimit(userId: String): Double
}

class InMemoryBudgetRepository : BudgetRepository {
    private val categoryLimits = mutableMapOf<Pair<String, Category>, Double>()
    private val monthlyBudgets = mutableMapOf<String, Double>()
    private val dailyLimits = mutableMapOf<String, Double>()

    override suspend fun setCategoryLimit(userId: String, category: Category, monthlyLimit: Double) {
        categoryLimits[userId to category] = monthlyLimit
    }

    override suspend fun categoryLimitOnce(userId: String, category: Category): Double {
        return categoryLimits[userId to category] ?: 5000.0
    }

    override suspend fun monthlyBudgetOnce(userId: String): Double? {
        return monthlyBudgets[userId] ?: 30000.0
    }

    override suspend fun dailyLimit(userId: String): Double {
        return dailyLimits[userId] ?: 1000.0
    }

    fun setMonthlyBudget(userId: String, budget: Double) {
        monthlyBudgets[userId] = budget
    }

    fun setDailyLimit(userId: String, limit: Double) {
        dailyLimits[userId] = limit
    }
}

interface SavingsGoalRepository {
    suspend fun create(userId: String, name: String, targetAmount: Double, deadlineMonths: Int)
}

class InMemorySavingsGoalRepository : SavingsGoalRepository {
    data class GoalEntry(val userId: String, val name: String, val targetAmount: Double, val deadlineMonths: Int)
    val goals = mutableListOf<GoalEntry>()

    override suspend fun create(userId: String, name: String, targetAmount: Double, deadlineMonths: Int) {
        goals.add(GoalEntry(userId, name, targetAmount, deadlineMonths))
    }
}

interface CoachActionExecutor {
    /** Returns human-readable confirmation of what was done. */
    suspend fun execute(userId: String, request: CoachActionRequest): Resource<String>
}

class CoachActionExecutorImpl(
    private val budgetRepository: BudgetRepository,
    private val goalRepository: SavingsGoalRepository,
    private val recommendationEngine: RecommendationEngine = DefaultRecommendationEngine(),
) : CoachActionExecutor {

    override suspend fun execute(userId: String, request: CoachActionRequest): Resource<String> = try {
        when (val r = request) {
            is CoachActionRequest.SetBudget -> {
                budgetRepository.setCategoryLimit(userId, r.category, r.monthlyLimit)
                Resource.Success("✅ Done! ${r.category.label} budget set to ₹%,.0f/month.".format(r.monthlyLimit))
            }
            is CoachActionRequest.ReduceCategoryLimit -> {
                val current = budgetRepository.categoryLimitOnce(userId, r.category)
                val reduced = current * (1 - r.reduceByPercent / 100.0)
                budgetRepository.setCategoryLimit(userId, r.category, reduced)
                Resource.Success("✅ ${r.category.label} limit cut %.0f%% → now ₹%,.0f/month. I'll watch that you stay under it."
                    .format(r.reduceByPercent.toDouble(), reduced))
            }
            is CoachActionRequest.CreateSavingsGoal -> {
                val plan = recommendationEngine.suggestSavingsPlan(r.targetAmount, r.deadlineMonths, monthlySurplus = 0.0)
                goalRepository.create(userId, r.name, r.targetAmount, plan.durationMonths)
                Resource.Success("🎯 Goal \"${r.name}\" created — ₹%,.0f over %d months (~₹%,.0f/mo)."
                    .format(r.targetAmount, plan.durationMonths, plan.monthlyAmount))
            }
            CoachActionRequest.ReviewExpenses ->
                Resource.Success("Opening your expense review…")
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Resource.Error(e.toAppError())
    }
}

fun Insight.ActionSuggestion.toRequest(defaultLimit: Double = 2000.0): CoachActionRequest? = when (action) {
    Insight.CoachAction.ADJUST_BUDGET_FOOD -> CoachActionRequest.SetBudget(Category.FOOD, defaultLimit)
    Insight.CoachAction.SET_CATEGORY_LIMIT -> CoachActionRequest.ReduceCategoryLimit(Category.FOOD, 20)
    Insight.CoachAction.CREATE_SAVINGS_GOAL -> CoachActionRequest.CreateSavingsGoal("My Goal", 12000.0, 6)
    Insight.CoachAction.REVIEW_EXPENSES -> CoachActionRequest.ReviewExpenses
    Insight.CoachAction.VIEW_REPORT, Insight.CoachAction.NONE -> null
}
