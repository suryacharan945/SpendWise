package com.example.core.security

import com.example.data.model.Expense
import java.util.Calendar

enum class FraudRiskLevel {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

enum class FraudAction {
    ALLOW,
    WARN_USER,
    REQUIRE_CONFIRMATION
}

data class FraudEvaluationResult(
    val isSuspicious: Boolean,
    val riskScore: Int, // 0 to 100
    val riskLevel: FraudRiskLevel,
    val reasons: List<String>,
    val actionRecommended: FraudAction
)

/**
 * Intelligent Real-Time Fraud and Anomaly Detection Engine for FinTech transactions.
 * Inspects velocity, outlier amounts, off-hour transactions, and duplicate patterns.
 */
object FraudDetectionEngine {

    fun evaluateTransaction(
        newAmount: Double,
        newCategory: String,
        newDescription: String,
        recentExpenses: List<Expense>,
        monthlyBudget: Double = 30000.0,
        timestamp: Long = System.currentTimeMillis()
    ): FraudEvaluationResult {
        val reasons = mutableListOf<String>()
        var riskScore = 0

        // 1. High Velocity Check: > 3 transactions in the last 5 minutes
        val fiveMinutesAgo = timestamp - (5 * 60 * 1000L)
        val rapidFireCount = recentExpenses.count { it.date >= fiveMinutesAgo }
        if (rapidFireCount >= 3) {
            riskScore += 35
            reasons.add("High transaction velocity: $rapidFireCount transactions recorded in under 5 minutes.")
        }

        // 2. Duplicate Transaction Check: Identical amount and category in the last 2 minutes
        val twoMinutesAgo = timestamp - (2 * 60 * 1000L)
        val hasDuplicate = recentExpenses.any {
            it.date >= twoMinutesAgo &&
            it.amount == newAmount &&
            it.category.equals(newCategory, ignoreCase = true)
        }
        if (hasDuplicate) {
            riskScore += 30
            reasons.add("Potential duplicate debit: identical amount ₹$newAmount in '$newCategory' within 2 minutes.")
        }

        // 3. Outlier Amount Check: Expense exceeds 50% of total monthly budget or > 4x category average
        val categoryExpenses = recentExpenses.filter { it.category.equals(newCategory, ignoreCase = true) }
        val categoryAvg = if (categoryExpenses.isNotEmpty()) categoryExpenses.map { it.amount }.average() else 0.0
        if (newAmount > (monthlyBudget * 0.5)) {
            riskScore += 45
            reasons.add("Unusual outlier: single transaction consumes >50% of the entire monthly budget.")
        } else if (categoryAvg > 0 && newAmount > (categoryAvg * 3.5)) {
            riskScore += 25
            reasons.add("Category spike: transaction is 3.5x higher than your average '$newCategory' spend (₹${categoryAvg.toInt()}).")
        }

        // 4. Off-Hours Midnight Burst Check: Large debit between 1:00 AM and 5:00 AM
        val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        if (hour in 1..4 && newAmount > 2000.0) {
            riskScore += 20
            reasons.add("Unusual off-hours transaction: debit of ₹${newAmount.toInt()} initiated between 1 AM and 5 AM.")
        }

        // Determine Level and Action
        val clampedScore = riskScore.coerceIn(0, 100)
        val (level, action) = when {
            clampedScore >= 70 -> Pair(FraudRiskLevel.CRITICAL, FraudAction.REQUIRE_CONFIRMATION)
            clampedScore >= 45 -> Pair(FraudRiskLevel.HIGH, FraudAction.WARN_USER)
            clampedScore >= 25 -> Pair(FraudRiskLevel.MEDIUM, FraudAction.WARN_USER)
            else -> Pair(FraudRiskLevel.LOW, FraudAction.ALLOW)
        }

        return FraudEvaluationResult(
            isSuspicious = clampedScore >= 25,
            riskScore = clampedScore,
            riskLevel = level,
            reasons = reasons,
            actionRecommended = action
        )
    }
}
