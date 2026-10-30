package com.example.domain.personalization

import com.example.domain.checkin.BehaviorProfileRepository
import com.example.domain.model.coach.Insight
import com.example.domain.model.coach.InsightFeedback
import com.example.domain.model.coach.UserBehaviorProfile
import com.example.domain.repository.Clock
import java.util.UUID

interface InsightFeedbackDao {
    suspend fun insert(feedback: InsightFeedback)
    suspend fun insertVote(userId: String, insightId: String, helpful: Boolean, timestamp: Long)
    suspend fun recent(userId: String, sinceMs: Long): List<InsightFeedback>
}

class InMemoryInsightFeedbackDao : InsightFeedbackDao {
    private val records = mutableListOf<InsightFeedback>()

    override suspend fun insert(feedback: InsightFeedback) {
        records.add(feedback)
    }

    override suspend fun insertVote(userId: String, insightId: String, helpful: Boolean, timestamp: Long) {
        records.add(
            InsightFeedback(
                id = UUID.randomUUID().toString(),
                userId = userId,
                insightId = insightId,
                insightType = Insight.InsightType.GENERAL,
                outcome = if (helpful) InsightFeedback.Outcome.HELPFUL_VOTE_YES else InsightFeedback.Outcome.HELPFUL_VOTE_NO,
                timestamp = timestamp
            )
        )
    }

    override suspend fun recent(userId: String, sinceMs: Long): List<InsightFeedback> =
        records.filter { it.userId == userId && it.timestamp >= sinceMs }
}

/**
 * Consumes ALL behavioral signals (clicks, dismisses, helpful votes, check-in ratings)
 * and maintains UserBehaviorProfile — which drives ranking, tone, and timing.
 */
class PersonalizationEngine(
    private val feedbackDao: InsightFeedbackDao,
    private val profileRepository: BehaviorProfileRepository,
    private val clock: Clock,
) {

    suspend fun record(userId: String, insight: Insight, outcome: InsightFeedback.Outcome) {
        feedbackDao.insert(
            InsightFeedback(
                id = UUID.randomUUID().toString(),
                userId = userId,
                insightId = insight.id,
                insightType = insight.type,
                outcome = outcome,
                timestamp = clock.nowMillis()
            )
        )
        rebuildProfile(userId)
    }

    suspend fun voteHelpful(userId: String, insightId: String, helpful: Boolean) {
        feedbackDao.insertVote(userId, insightId, helpful, clock.nowMillis())
        rebuildProfile(userId)
    }

    /** Recompute profile from rolling 60-day window. Cheap enough to run per event. */
    suspend fun rebuildProfile(userId: String) {
        val events = feedbackDao.recent(userId, sinceMs = clock.nowMillis() - SIXTY_DAYS_MS)
        val byType = events.groupBy { it.insightType }

        val clicksByType = byType.mapValues { (_, es) ->
            es.count { it.outcome in ENGAGING_OUTCOMES } / es.size.toDouble()
        }

        val voteScores = events.filter {
            it.outcome == InsightFeedback.Outcome.HELPFUL_VOTE_YES ||
            it.outcome == InsightFeedback.Outcome.HELPFUL_VOTE_NO
        }
        val feedbackScore = voteScores.takeIf { it.isNotEmpty() }
            ?.let { list -> list.count { it.outcome == InsightFeedback.Outcome.HELPFUL_VOTE_YES } / list.size.toDouble() }
            ?: 0.5

        val draft = UserBehaviorProfile(
            userId = userId,
            insightClickRateByType = clicksByType,
            samplesByType = byType.mapValues { it.value.size },
            feedbackScoreRolling = feedbackScore,
            optimalEngagementHours = profileRepository.observedOpenHours(userId),
            checkInCompletionRate = profileRepository.checkInRate(userId),
            lastCoachingTouch = clock.nowMillis(),
        )

        val updatedProfile = draft.copy(preferredTone = UserBehaviorProfile.CoachTone.from(draft))
        profileRepository.upsert(updatedProfile)
    }

    companion object {
        val ENGAGING_OUTCOMES = setOf(InsightFeedback.Outcome.CLICKED, InsightFeedback.Outcome.ACTION_EXECUTED)
        const val SIXTY_DAYS_MS = 60L * 24 * 60 * 60 * 1000
    }
}
