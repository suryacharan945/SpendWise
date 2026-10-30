package com.example.domain.usecase

import com.example.core.common.Resource
import com.example.core.security.FraudDetectionEngine
import com.example.core.security.FraudEvaluationResult
import com.example.data.model.Expense
import com.example.domain.repository.IFinanceRepository
import kotlinx.coroutines.flow.first

data class AddExpenseResult(
    val expense: Expense,
    val fraudEvaluation: FraudEvaluationResult
)

/**
 * UseCase encapsulating business rules, security/fraud checks, and persistence for adding expenses.
 */
class AddExpenseUseCase(
    private val repository: IFinanceRepository
) {
    suspend operator fun invoke(
        userEmail: String,
        amount: Double,
        category: String,
        description: String,
        tags: String = "",
        paymentMethod: String = "Cash",
        date: Long = System.currentTimeMillis()
    ): Resource<AddExpenseResult> {
        // 1. Domain Validations
        if (userEmail.isBlank()) {
            return Resource.Error("User must be authenticated to log an expense.")
        }
        if (amount <= 0.0) {
            return Resource.Error("Expense amount must be greater than zero.")
        }
        if (description.isBlank()) {
            return Resource.Error("Description cannot be empty.")
        }
        if (category.isBlank()) {
            return Resource.Error("Category must be specified.")
        }

        // 2. Fetch recent context for fraud/anomaly analysis
        val recentExpenses = try {
            repository.getExpenses(userEmail).first().getOrNull() ?: emptyList()
        } catch (e: Throwable) {
            emptyList()
        }

        val monthlyBudget = try {
            repository.getUserProfileSync(userEmail).getOrNull()?.monthlyBudgetLimit ?: 30000.0
        } catch (e: Throwable) {
            30000.0
        }

        // 3. Fraud / Velocity Evaluation
        val fraudResult = FraudDetectionEngine.evaluateTransaction(
            newAmount = amount,
            newCategory = category,
            newDescription = description,
            recentExpenses = recentExpenses,
            monthlyBudget = monthlyBudget,
            timestamp = date
        )

        val newExpense = Expense(
            amount = amount,
            category = category,
            description = description.trim(),
            date = date,
            paymentMethod = paymentMethod,
            tags = tags.trim(),
            userEmail = userEmail,
            updatedAt = System.currentTimeMillis(),
            isDeleted = false,
            syncStatus = "PENDING_PUSH"
        )

        // 4. Persistence
        return when (val saveResult = repository.addExpense(newExpense)) {
            is Resource.Success -> Resource.Success(
                AddExpenseResult(
                    expense = saveResult.data,
                    fraudEvaluation = fraudResult
                )
            )
            is Resource.Error -> Resource.Error(saveResult.message, saveResult.cause)
            is Resource.Loading -> Resource.Loading()
        }
    }
}
