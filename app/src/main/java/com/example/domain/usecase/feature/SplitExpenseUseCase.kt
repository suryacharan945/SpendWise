package com.example.domain.usecase.feature

import com.example.core.common.Resource
import com.example.domain.model.Money

data class ParticipantShare(
    val name: String,
    val share: Money,
    val isPaid: Boolean = false
)

data class SplitResult(
    val totalMoney: Money,
    val numParticipants: Int,
    val participantShares: List<ParticipantShare>
)

/**
 * UseCase for dividing expenses across multiple participants.
 * Executes in integer paise to eliminate fractional currency drift and guarantee
 * the sum of all participant shares equals the original total.
 */
class SplitExpenseUseCase {

    operator fun invoke(
        total: Money,
        participantNames: List<String>
    ): Resource<SplitResult> {
        if (participantNames.isEmpty()) {
            return Resource.Error("At least one participant is required.")
        }
        if (total.paise <= 0L) {
            return Resource.Error("Total amount must be greater than zero.")
        }

        val count = participantNames.size.toLong()
        val basePaise = total.paise / count
        val remainderPaise = total.paise % count

        val shares = participantNames.mapIndexed { index, name ->
            // Allocate remainder paise across the first few participants to maintain penny-perfect sum
            val extra = if (index < remainderPaise) 1L else 0L
            ParticipantShare(
                name = name,
                share = Money.fromPaise(basePaise + extra),
                isPaid = index == 0 // First participant is usually payer
            )
        }

        return Resource.Success(
            SplitResult(
                totalMoney = total,
                numParticipants = participantNames.size,
                participantShares = shares
            )
        )
    }
}
