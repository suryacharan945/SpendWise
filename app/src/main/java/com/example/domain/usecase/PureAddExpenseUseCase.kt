package com.example.domain.usecase

import com.example.core.common.AppError
import com.example.core.common.Resource
import com.example.domain.model.Badge
import com.example.domain.model.Category
import com.example.domain.model.FraudAlert
import com.example.domain.model.PaymentMethod
import com.example.domain.model.PureExpense
import com.example.domain.repository.BadgeEvaluator
import com.example.domain.repository.Clock
import com.example.domain.repository.FraudDetector
import com.example.domain.repository.PureExpenseRepository
import java.util.UUID
import kotlin.math.round
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Pure Kotlin AddExpenseUseCase.
 * Enforces pure domain validations, normalized tags, fraud detection,
 * badge rewards, and immutable persistence with zero Android framework dependencies.
 */
class PureAddExpenseUseCase(
    private val expenseRepository: PureExpenseRepository,
    private val fraudDetector: FraudDetector,
    private val badgeEvaluator: BadgeEvaluator,
    private val clock: Clock,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    data class Input(
        val userId: String,
        val amount: Double,
        val category: Category,
        val description: String = "",
        val tags: List<String> = emptyList(),
        val paymentMethod: PaymentMethod = PaymentMethod.UPI,
        val subCategory: String? = null,
        val date: Long? = null,
        val id: String? = null,
    ) {
        companion object {
            const val MAX_AMOUNT = 1_000_000.0
            const val MAX_DESC_LENGTH = 200
        }
    }

    data class AddExpenseResult(
        val expense: PureExpense,
        val fraudAlert: FraudAlert?,
        val newBadges: List<Badge>,
    )

    suspend operator fun invoke(input: Input): Resource<AddExpenseResult> =
        withContext(ioDispatcher) {
            validate(input)?.let { validationError ->
                return@withContext Resource.Error(validationError)
            }

            val now = clock.nowMillis()
            val expense = PureExpense(
                id = input.id ?: UUID.randomUUID().toString(),
                userId = input.userId,
                amount = input.amount.roundTo2(),
                category = input.category,
                subCategory = input.subCategory,
                description = input.description.trim(),
                tags = input.tags.map { it.lowercase() }.distinct(),
                paymentMethod = input.paymentMethod,
                createdAt = input.date ?: now,
                updatedAt = now,
                deleted = false
            )

            when (val save = expenseRepository.addExpense(expense)) {
                is Resource.Error -> Resource.Error(save.error)
                is Resource.Success -> Resource.Success(
                    AddExpenseResult(
                        expense = expense,
                        fraudAlert = fraudDetector.inspect(input.userId, expense),
                        newBadges = badgeEvaluator.onExpenseAdded(input.userId, expense),
                    )
                )
                is Resource.Loading -> Resource.Loading()
            }
        }

    private fun validate(input: Input): AppError.Validation? {
        val errors = buildMap {
            if (input.amount <= 0.0) put("amount", "Amount must be greater than zero")
            if (input.amount > Input.MAX_AMOUNT) put("amount", "Amount exceeds limit")
            if (input.description.length > Input.MAX_DESC_LENGTH) put("description", "Description too long")
        }
        return if (errors.isEmpty()) null else AppError.Validation(errors)
    }

    private fun Double.roundTo2(): Double = round(this * 100.0) / 100.0
}
