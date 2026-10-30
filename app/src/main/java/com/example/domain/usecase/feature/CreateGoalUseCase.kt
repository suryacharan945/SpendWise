package com.example.domain.usecase.feature

import com.example.core.common.Resource
import com.example.data.model.SavingGoal
import com.example.domain.model.Money
import com.example.domain.repository.IFinanceRepository
import java.util.*

data class GoalCreationResult(
    val goal: SavingGoal,
    val dailyRequired: Money,
    val monthlyRequired: Money,
    val isAggressive: Boolean, // true if > 30% of daily allowance
    val warningMessage: String? = null
)

class CreateGoalUseCase(private val repository: IFinanceRepository) {

    suspend operator fun invoke(
        userEmail: String,
        name: String,
        targetAmount: Money,
        targetDateMillis: Long,
        monthlyContribution: Money = Money.ZERO,
        currentAllowanceDaily: Money = Money.fromRupees(1000.0)
    ): Resource<GoalCreationResult> {
        if (name.isBlank()) {
            return Resource.Error("Goal name cannot be empty.")
        }
        if (targetAmount.paise <= 0L) {
            return Resource.Error("Target amount must be greater than zero.")
        }

        val now = System.currentTimeMillis()
        if (targetDateMillis <= now) {
            return Resource.Error("Target date must be in the future.")
        }

        val daysRemaining = maxOf(1L, (targetDateMillis - now) / (24 * 60 * 60 * 1000L))
        val dailyRequiredPaise = targetAmount.paise / daysRemaining
        val dailyRequiredMoney = Money.fromPaise(dailyRequiredPaise)

        val monthsRemaining = maxOf(1.0, daysRemaining / 30.0)
        val monthlyRequiredMoney = Money.fromRupees(targetAmount.rupees() / monthsRemaining)

        val allowanceThresholdPaise = (currentAllowanceDaily.paise * 30L) / 100L // 30% threshold
        val isAggressive = dailyRequiredPaise > allowanceThresholdPaise
        val warning = if (isAggressive) {
            "Warning: Requires ${dailyRequiredMoney.format()}/day, which exceeds 30% of your daily allowance (${currentAllowanceDaily.format()})."
        } else null

        val newGoal = SavingGoal(
            name = name.trim(),
            goalType = name.trim(),
            targetAmount = targetAmount.rupees(),
            currentAmount = 0.0,
            monthlyContribution = if (monthlyContribution.paise > 0) monthlyContribution.rupees() else monthlyRequiredMoney.rupees(),
            userEmail = userEmail
        )

        val insertResult = repository.addGoal(newGoal)
        return when (insertResult) {
            is Resource.Success -> Resource.Success(
                GoalCreationResult(
                    goal = insertResult.data,
                    dailyRequired = dailyRequiredMoney,
                    monthlyRequired = monthlyRequiredMoney,
                    isAggressive = isAggressive,
                    warningMessage = warning
                )
            )
            is Resource.Error -> Resource.Error(insertResult.message, cause = insertResult.cause)
            is Resource.Loading -> Resource.Loading()
        }
    }
}
