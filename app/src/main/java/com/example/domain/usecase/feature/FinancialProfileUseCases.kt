package com.example.domain.usecase.feature

import com.example.core.common.Resource
import com.example.data.model.UserProfile
import com.example.domain.model.Money
import com.example.domain.repository.IFinanceRepository

data class FinancialProfile(
    val monthlyIncome: Money,
    val mandatoryCommitments: Money,
    val savingsTargetPercent: Int,
    val salaryDay: Int,
    val dailyAllowance: Money,
    val discretionaryIncome: Money
)

data class MandatoryExpenseItem(
    val id: String,
    val name: String,
    val amount: Money
)

class GetFinancialProfileUseCase(private val repository: IFinanceRepository) {
    suspend operator fun invoke(email: String): Resource<FinancialProfile> {
        return try {
            val user = repository.getUserProfileSync(email).getOrNull()
            val income = if (user != null && user.monthlyIncome > 0) Money.fromRupees(user.monthlyIncome) else Money.fromRupees(50000.0)
            val savingsGoal = if (user != null && user.monthlySavingsGoal > 0) Money.fromRupees(user.monthlySavingsGoal) else Money.fromRupees(10000.0)
            val pct = if (income.rupees() > 0) ((savingsGoal.rupees() / income.rupees()) * 100).toInt().coerceIn(5, 50) else 20
            val commitments = Money.fromRupees(income.rupees() * 0.3)
            val savingsPaise = (income.paise * pct.toLong()) / 100L
            val discretionaryPaise = maxOf(0L, income.paise - commitments.paise - savingsPaise)
            val dailyPaise = discretionaryPaise / 30L

            Resource.Success(
                FinancialProfile(
                    monthlyIncome = income,
                    mandatoryCommitments = commitments,
                    savingsTargetPercent = pct,
                    salaryDay = 1,
                    dailyAllowance = Money.fromPaise(dailyPaise),
                    discretionaryIncome = Money.fromPaise(discretionaryPaise)
                )
            )
        } catch (e: Exception) {
            Resource.Error("Failed to fetch financial profile: ${e.localizedMessage}", cause = e)
        }
    }
}

class UpdateFinancialProfileUseCase(private val repository: IFinanceRepository) {
    suspend operator fun invoke(
        email: String,
        monthlyIncome: Money,
        mandatoryCommitments: Money,
        savingsTargetPercent: Int,
        salaryDay: Int = 1
    ): Resource<FinancialProfile> {
        if (monthlyIncome.paise <= 0L) {
            return Resource.Error("Monthly income must be greater than zero.")
        }
        if (savingsTargetPercent !in 5..50) {
            return Resource.Error("Savings target percent must be between 5% and 50%.")
        }
        if (salaryDay !in 1..31) {
            return Resource.Error("Salary day must be between 1 and 31.")
        }

        val savingsPaise = (monthlyIncome.paise * savingsTargetPercent.toLong()) / 100L
        val totalFixedOutflowPaise = mandatoryCommitments.paise + savingsPaise
        if (totalFixedOutflowPaise > monthlyIncome.paise) {
            return Resource.Error("Mandatory expenses + savings target cannot exceed monthly income.")
        }

        val discretionaryPaise = monthlyIncome.paise - totalFixedOutflowPaise
        val dailyPaise = discretionaryPaise / 30L
        val dailyAllowance = Money.fromPaise(dailyPaise)

        return try {
            val user = repository.getUserProfileSync(email).getOrNull()
            if (user != null) {
                val updated = user.copy(
                    monthlyIncome = monthlyIncome.rupees(),
                    monthlySavingsGoal = Money.fromPaise(savingsPaise).rupees(),
                    monthlyBudgetLimit = Money.fromPaise(discretionaryPaise).rupees(),
                    dailyBudgetLimit = dailyAllowance.rupees(),
                    annualIncome = monthlyIncome.rupees() * 12.0
                )
                repository.saveUserProfile(updated)
            }
            Resource.Success(
                FinancialProfile(
                    monthlyIncome = monthlyIncome,
                    mandatoryCommitments = mandatoryCommitments,
                    savingsTargetPercent = savingsTargetPercent,
                    salaryDay = salaryDay,
                    dailyAllowance = dailyAllowance,
                    discretionaryIncome = Money.fromPaise(discretionaryPaise)
                )
            )
        } catch (e: Exception) {
            Resource.Error("Failed to update financial profile: ${e.localizedMessage}", cause = e)
        }
    }
}
