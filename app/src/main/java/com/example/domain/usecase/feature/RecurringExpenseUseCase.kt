package com.example.domain.usecase.feature

import com.example.core.common.Resource
import com.example.data.model.Expense
import com.example.domain.model.Money
import java.util.UUID

enum class RecurringFrequency {
    DAILY,
    WEEKLY,
    MONTHLY
}

data class RecurringExpenseRule(
    val id: String = UUID.randomUUID().toString(),
    val userEmail: String,
    val title: String,
    val category: String,
    val money: Money,
    val frequency: RecurringFrequency,
    val nextDueEpoch: Long,
    val isActive: Boolean = true
)

data class RecurringProcessResult(
    val createdExpenses: List<Expense>,
    val updatedRules: List<RecurringExpenseRule>
)

/**
 * UseCase for recurring expenses (subscriptions, rent, utility bills).
 * Detects due dates, logs new expenses, and advances next due intervals.
 */
class RecurringExpenseUseCase {

    fun processDueRules(
        rules: List<RecurringExpenseRule>,
        currentEpoch: Long
    ): Resource<RecurringProcessResult> {
        val createdExpenses = mutableListOf<Expense>()
        val updatedRules = mutableListOf<RecurringExpenseRule>()

        for (rule in rules) {
            if (!rule.isActive) {
                updatedRules.add(rule)
                continue
            }

            if (currentEpoch >= rule.nextDueEpoch) {
                // Generate automated expense
                val generatedExpense = Expense(
                    id = 0,
                    userEmail = rule.userEmail,
                    amount = rule.money.rupees(),
                    amountPaise = rule.money.paise,
                    category = rule.category,
                    date = rule.nextDueEpoch,
                    description = "Recurring: ${rule.title}",
                    paymentMethod = "Auto-Debit",
                    tags = "recurring,auto",
                    updatedAt = currentEpoch,
                    syncStatus = "PENDING_PUSH"
                )
                createdExpenses.add(generatedExpense)

                // Advance due date
                val nextEpoch = computeNextDueDate(rule.nextDueEpoch, rule.frequency)
                updatedRules.add(rule.copy(nextDueEpoch = nextEpoch))
            } else {
                updatedRules.add(rule)
            }
        }

        return Resource.Success(
            RecurringProcessResult(
                createdExpenses = createdExpenses,
                updatedRules = updatedRules
            )
        )
    }

    fun computeNextDueDate(fromEpoch: Long, frequency: RecurringFrequency): Long {
        val oneDayMillis = 86_400_000L
        return when (frequency) {
            RecurringFrequency.DAILY -> fromEpoch + oneDayMillis
            RecurringFrequency.WEEKLY -> fromEpoch + (7L * oneDayMillis)
            RecurringFrequency.MONTHLY -> fromEpoch + (30L * oneDayMillis)
        }
    }
}
