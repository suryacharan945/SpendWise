package com.example.domain.usecase

import com.example.core.common.Resource
import com.example.data.model.SavingGoal
import com.example.domain.repository.IFinanceRepository

data class GoalDepositResult(
    val updatedGoal: SavingGoal,
    val earnedXp: Int,
    val milestoneReached: Int? // 50, 100, etc.
)

/**
 * UseCase encapsulating savings goal deposits, milestone notifications, and gamification XP.
 */
class DepositToGoalUseCase(
    private val repository: IFinanceRepository
) {
    suspend operator fun invoke(goal: SavingGoal, amount: Double): Resource<GoalDepositResult> {
        if (amount <= 0.0) {
            return Resource.Error("Deposit amount must be greater than zero.")
        }

        val newCurrent = goal.currentAmount + amount
        val prevPct = if (goal.targetAmount > 0) ((goal.currentAmount / goal.targetAmount) * 100).toInt() else 0
        val newPct = if (goal.targetAmount > 0) ((newCurrent / goal.targetAmount) * 100).toInt() else 0

        val milestone = when {
            newPct >= 100 && prevPct < 100 -> 100
            newPct >= 50 && prevPct < 50 -> 50
            else -> null
        }

        val updatedGoal = goal.copy(
            currentAmount = newCurrent,
            updatedAt = System.currentTimeMillis(),
            syncStatus = "PENDING_PUSH"
        )

        return when (val updateRes = repository.updateGoal(updatedGoal)) {
            is Resource.Success -> {
                Resource.Success(
                    GoalDepositResult(
                        updatedGoal = updatedGoal,
                        earnedXp = 25,
                        milestoneReached = milestone
                    )
                )
            }
            is Resource.Error -> Resource.Error(updateRes.message, updateRes.cause)
            is Resource.Loading -> Resource.Loading()
        }
    }
}
