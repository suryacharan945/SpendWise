package com.example.domain.usecase.feature

import com.example.core.common.Resource
import com.example.domain.model.Money

enum class RoundUpTarget(val incrementRupees: Long) {
    NEAREST_10(10L),
    NEAREST_50(50L),
    NEAREST_100(100L)
}

data class RoundUpResult(
    val originalMoney: Money,
    val roundedMoney: Money,
    val spareChangeMoney: Money
)

/**
 * UseCase for the FinTech Round-Up Rule:
 * Rounds transactions to the nearest ₹10, ₹50, or ₹100 and directs spare change
 * to designated savings goals.
 */
class RoundUpRuleUseCase {

    operator fun invoke(
        expense: Money,
        target: RoundUpTarget = RoundUpTarget.NEAREST_10
    ): Resource<RoundUpResult> {
        if (expense.paise <= 0L) {
            return Resource.Error("Expense must be positive for round-up.")
        }

        val stepPaise = target.incrementRupees * 100L
        val remainder = expense.paise % stepPaise

        val spareChangePaise = if (remainder == 0L) 0L else (stepPaise - remainder)
        val roundedPaise = expense.paise + spareChangePaise

        return Resource.Success(
            RoundUpResult(
                originalMoney = expense,
                roundedMoney = Money.fromPaise(roundedPaise),
                spareChangeMoney = Money.fromPaise(spareChangePaise)
            )
        )
    }
}
