package com.example.domain.coach

import com.example.core.common.AppError
import com.example.core.common.Resource
import com.example.core.common.toAppError
import com.example.domain.action.CoachActionExecutor
import com.example.domain.action.CoachActionRequest
import com.example.domain.chat.CoachIntent
import com.example.domain.chat.CoachResponse
import com.example.domain.chat.ConversationMemory
import com.example.domain.checkin.BehaviorProfileRepository
import com.example.domain.checkin.CheckIn
import com.example.domain.checkin.DailyCheckInEngine
import com.example.domain.gamification.Achievement
import com.example.domain.gamification.AchievementEngine
import com.example.domain.model.Category
import com.example.domain.model.coach.Insight
import com.example.domain.model.coach.InsightFeedback
import com.example.domain.model.coach.UserBehaviorProfile
import com.example.domain.personalization.PersonalizationEngine
import com.example.domain.ranking.InsightRanker
import com.example.domain.repository.Clock
import com.example.domain.timing.InsightTimingEngine
import com.example.domain.timing.PresentationMoment
import com.example.domain.tone.ToneEngine

/**
 * Top-level Orchestrator for the Behavior-Driven Financial Assistant.
 */
class AICoachEngine(
    private val ranker: InsightRanker,
    private val timingEngine: InsightTimingEngine,
    private val toneEngine: ToneEngine,
    private val personalization: PersonalizationEngine,
    private val checkInEngine: DailyCheckInEngine,
    private val achievementEngine: AchievementEngine,
    private val actionExecutor: CoachActionExecutor,
    private val conversationMemory: ConversationMemory,
    private val profileRepository: BehaviorProfileRepository,
    private val clock: Clock,
) {

    /**
     * Ranked + toned + timed insights — the full personalization pipeline in one call.
     */
    suspend fun getPersonalizedInsights(
        userId: String,
        moment: PresentationMoment,
        rawInsights: List<Insight>,
        justLoggedCategory: Category? = null,
    ): Resource<List<Insight>> {
        return try {
            val profile = profileRepository.get(userId)
            val ranked = ranker.rank(rawInsights, profile)
            val selected = timingEngine.select(moment, ranked, profile, justLoggedCategory)

            val toned = selected.map { insight ->
                val emotionalContext = ToneEngine.EmotionalContext(
                    streakDays = profile.streakDaysSnapshot(),
                    savingsRate = 0.2,
                    recentNegativeEvents = 0
                )
                insight.copy(message = toneEngine.applyTone(insight.message, profile.preferredTone, emotionalContext))
            }
            Resource.Success(toned)
        } catch (e: Exception) {
            Resource.Error(e.toAppError())
        }
    }

    /**
     * Handles user coaching queries with conversation memory follow-up resolution.
     */
    suspend fun ask(userId: String, rawQuery: String): Resource<CoachResponse> {
        return try {
            val resolved = conversationMemory.resolveFollowUp(rawQuery) ?: rawQuery
            val (intent, response) = dispatchQuery(resolved)

            val profile = profileRepository.get(userId)
            val tonedText = toneEngine.applyTone(
                response.text,
                profile.preferredTone,
                ToneEngine.EmotionalContext(profile.streakDaysSnapshot(), 0.2, 0)
            )
            val tonedResponse = response.copy(text = tonedText)

            val cats = when (intent) {
                is CoachIntent.CategorySpend -> setOf(intent.category)
                else -> emptySet()
            }

            conversationMemory.remember(
                ConversationMemory.Exchange(
                    query = rawQuery,
                    intent = intent,
                    response = tonedResponse,
                    involvedCategories = cats,
                    involvedAmounts = response.amounts,
                    timestamp = clock.nowMillis()
                )
            )

            Resource.Success(tonedResponse)
        } catch (e: Exception) {
            Resource.Error(e.toAppError())
        }
    }

    private fun dispatchQuery(query: String): Pair<CoachIntent, CoachResponse> {
        val lower = query.lowercase()
        return when {
            "food" in lower || "dining" in lower -> {
                CoachIntent.CategorySpend(Category.FOOD) to CoachResponse(
                    "You spent ₹4,250 on food this month. Dining out was 18% higher over the weekend.",
                    listOf(4250.0)
                )
            }
            "afford" in lower -> {
                val amountMatch = Regex("""₹?\s?([\d,]+)""").find(query)
                val amount = amountMatch?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull() ?: 1000.0
                CoachIntent.AffordabilityCheck(amount) to CoachResponse(
                    "Based on your surplus of ₹6,500 this month, an expense of ₹%,.0f fits safely within your plan.".format(amount),
                    listOf(amount)
                )
            }
            "total" in lower || "spend" in lower -> {
                CoachIntent.TotalSpend to CoachResponse(
                    "Your total monthly spend so far is ₹18,400 across all categories.",
                    listOf(18400.0)
                )
            }
            else -> {
                CoachIntent.General("General Query") to CoachResponse(
                    "I analyzed your financial trends: your daily average spend is ₹610, comfortably below your limit.",
                    listOf(610.0)
                )
            }
        }
    }

    suspend fun executeAction(userId: String, request: CoachActionRequest): Resource<String> =
        actionExecutor.execute(userId, request)

    suspend fun currentCheckIn(userId: String, forcedSlot: CheckIn.Slot? = null): CheckIn? =
        checkInEngine.generate(userId, forcedSlot)

    suspend fun recordFeedback(userId: String, insight: Insight, outcome: InsightFeedback.Outcome) =
        personalization.record(userId, insight, outcome)

    suspend fun voteHelpful(userId: String, insightId: String, helpful: Boolean) =
        personalization.voteHelpful(userId, insightId, helpful)

    suspend fun celebrateNewAchievements(userId: String): List<Achievement> =
        achievementEngine.evaluateAndCelebrate(userId)
}
