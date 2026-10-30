package com.example.domain.usecase.feature

import com.example.core.common.Resource
import com.example.data.model.*
import com.example.domain.model.Money

/**
 * Clean Architecture Use Case for managing extended UserProfileDetails in Phase 6.
 * Encapsulates safe daily allowance calculation, budget style rules, and validation.
 */
class UserProfileDetailsUseCase {

    fun getDefaultProfileDetails(profile: UserProfile): UserProfileDetails {
        val income = if (profile.monthlyIncome > 0) Money.fromRupees(profile.monthlyIncome) else Money.fromRupees(50000.0)
        val savingsTarget = if (profile.monthlySavingsGoal > 0 && income.rupees() > 0) {
            ((profile.monthlySavingsGoal / income.rupees()) * 100).toInt().coerceIn(5, 50)
        } else {
            20
        }

        return UserProfileDetails(
            displayName = profile.username.ifEmpty { "Smart Saver" },
            avatar = Avatar.Initial(profile.username.take(1).ifEmpty { "U" }, bgGradientIndex = 0),
            monthlyIncome = income,
            salaryDayOfMonth = 1,
            fixedMonthlyCommitments = Money.fromRupees(income.rupees() * 0.3), // 30% default commitments (rent/bills)
            savingsTargetPercent = savingsTarget,
            riskAppetite = RiskAppetite.BALANCED,
            primaryGoalIntent = "Build wealth and peace of mind",
            payCurrencyNote = "INR (₹) Standard",
            quietHours = 22..7,
            budgetStyle = BudgetStyle.ZERO_BASED
        )
    }

    fun calculateDailyAllowance(details: UserProfileDetails, remainingDaysInMonth: Int): Money {
        return details.computeDailyAllowance(remainingDaysInMonth)
    }

    fun validateProfile(details: UserProfileDetails): Resource<Boolean> {
        if (details.displayName.isBlank()) {
            return Resource.Error("Display name cannot be empty.")
        }
        if (details.salaryDayOfMonth !in 1..31) {
            return Resource.Error("Salary day must be between 1 and 31.")
        }
        if (details.savingsTargetPercent !in 5..50) {
            return Resource.Error("Savings target must be between 5% and 50%.")
        }
        if (details.fixedMonthlyCommitments > details.monthlyIncome) {
            return Resource.Error("Fixed monthly commitments cannot exceed monthly income.")
        }
        return Resource.Success(true)
    }
}
