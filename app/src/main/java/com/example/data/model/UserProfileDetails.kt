package com.example.data.model

import com.example.domain.model.Money

sealed class Avatar {
    data class Emoji(val emoji: String, val bgGradientIndex: Int = 0) : Avatar()
    data class Initial(val initial: String, val bgGradientIndex: Int = 0) : Avatar()
    data class PhotoUri(val uri: String) : Avatar()

    fun displayRepresentation(): String = when (this) {
        is Emoji -> emoji
        is Initial -> initial.take(1).uppercase()
        is PhotoUri -> "👤"
    }
}

enum class RiskAppetite {
    CONSERVATIVE,
    BALANCED,
    BOLD
}

enum class BudgetStyle {
    ZERO_BASED,
    REVERSE,
    ENVELOPE
}

/**
 * Extended Rich Profile Details model for Phase 6.
 * Drives safe-to-spend computations, personalized coaching tone, and budget distribution.
 */
data class UserProfileDetails(
    val displayName: String,
    val avatar: Avatar,
    val monthlyIncome: Money,
    val salaryDayOfMonth: Int,             // 1..31 — powers check-in timing & projection
    val fixedMonthlyCommitments: Money,    // rent/bills → smarter safe-to-spend
    val savingsTargetPercent: Int,         // 5–50, default 20 — drives goals & check-ins
    val riskAppetite: RiskAppetite = RiskAppetite.BALANCED,
    val primaryGoalIntent: String? = null,
    val payCurrencyNote: String = "INR (₹) Standard",
    val quietHours: IntRange = 22..7,
    val budgetStyle: BudgetStyle = BudgetStyle.ZERO_BASED
) {
    /**
     * Compute safe daily spending allowance considering commitments and target savings:
     * Discretionary budget = monthlyIncome - fixedMonthlyCommitments - (monthlyIncome * savingsTargetPercent / 100)
     */
    fun computeDailyAllowance(remainingDaysInMonth: Int): Money {
        val savingsPaise = (monthlyIncome.paise * savingsTargetPercent.toLong()) / 100L
        val commitmentsPaise = fixedMonthlyCommitments.paise
        val discretionaryPaise = maxOf(0L, monthlyIncome.paise - commitmentsPaise - savingsPaise)
        val days = maxOf(1, remainingDaysInMonth)
        return Money.fromPaise(discretionaryPaise / days)
    }

    /**
     * Compute total planned savings for the month.
     */
    fun computeMonthlySavingsTarget(): Money {
        val savingsPaise = (monthlyIncome.paise * savingsTargetPercent.toLong()) / 100L
        return Money.fromPaise(savingsPaise)
    }
}
