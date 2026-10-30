package com.example.domain.model.alerts

/**
 * Supported behavioral alert triggers in the personal finance system.
 */
enum class AlertType {
    BUDGET_OVERSHOOT_FORECAST,
    STREAK_AT_RISK,
    UNUSUAL_SPENDING,
    WEEKEND_ALLOWANCE,
    SALARY_CYCLE_NUDGE,
    SAVINGS_OPPORTUNITY,
}
