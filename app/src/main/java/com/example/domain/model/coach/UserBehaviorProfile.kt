package com.example.domain.model.coach

import com.example.domain.model.Category
import com.example.domain.model.alerts.AlertSeverity

/**
 * Insight model representing a coaching insight or behavioral suggestion.
 */
data class Insight(
    val id: String,
    val type: InsightType,
    val severity: AlertSeverity,
    val message: String,
    val generatedAt: Long,
    val confidence: Double = 1.0,
    val actionable: ActionSuggestion? = null,
) {
    enum class InsightType {
        PREDICTION,
        BUDGET_USAGE,
        CATEGORY_TREND,
        SAVINGS_OPPORTUNITY,
        STREAK_BEHAVIOR,
        ANOMALY,
        GENERAL
    }

    enum class CoachAction {
        ADJUST_BUDGET_FOOD,
        SET_CATEGORY_LIMIT,
        CREATE_SAVINGS_GOAL,
        REVIEW_EXPENSES,
        VIEW_REPORT,
        NONE
    }

    data class ActionSuggestion(
        val title: String,
        val action: CoachAction,
    )
}

/**
 * The single source of truth for "who is this user and how do they like to be coached".
 * Rebuilt continuously from insight clicks, alert engagement, and app usage.
 */
data class UserBehaviorProfile(
    val userId: String,
    val insightClickRateByType: Map<Insight.InsightType, Double> = emptyMap(),  // 0..1
    val samplesByType: Map<Insight.InsightType, Int> = emptyMap(),
    val feedbackScoreRolling: Double = 0.5,        // avg of "was this helpful?" votes, 0..1
    val preferredTone: CoachTone = CoachTone.SUPPORTIVE, // learned, not configured
    val optimalEngagementHours: List<Int> = listOf(9, 13, 20),   // hours user actually opens the app
    val checkInCompletionRate: Double = 0.5,       // % of daily check-ins answered
    val lastCoachingTouch: Long = 0L,
) {
    /** Types with >=6 samples & <15% click rate get demoted; >=50% get boosted. */
    fun relevanceWeight(type: Insight.InsightType): Double {
        val rate = insightClickRateByType[type] ?: return NEUTRAL_WEIGHT
        val samples = samplesByType[type] ?: 0
        if (samples < MIN_SAMPLES) return NEUTRAL_WEIGHT
        return when {
            rate >= BOOST_THRESHOLD -> 1.4
            rate <= DEMOTE_THRESHOLD -> 0.5
            else -> NEUTRAL_WEIGHT + rate
        }
    }

    fun streakDaysSnapshot(): Int = 7

    enum class CoachTone(val styleHint: String) {
        SUPPORTIVE("encouraging, no blame, small wins framing"),
        DIRECT("brief, numbers-first, minimal fluff"),
        CELEBRATORY("enthusiastic, progress-highlighting"),
        WARNING("firm urgency without shame");

        companion object {
            /** Learned tone: high completion + positive feedback -> celebratory/supportive;
             *  repeated ignored warnings -> escalate to direct/warning. */
            fun from(profile: UserBehaviorProfile): CoachTone = when {
                profile.feedbackScoreRolling > 0.7 && profile.checkInCompletionRate > 0.5 -> CELEBRATORY
                profile.checkInCompletionRate < 0.2 -> DIRECT      // soft tones aren't landing
                profile.insightClickRateByType.values.average().takeIf { !it.isNaN() } ?: 0.0 > 0.4 -> SUPPORTIVE
                else -> SUPPORTIVE
            }
        }
    }

    companion object {
        const val MIN_SAMPLES = 6
        const val BOOST_THRESHOLD = 0.5
        const val DEMOTE_THRESHOLD = 0.15
        const val NEUTRAL_WEIGHT = 0.8

        fun defaultFor(userId: String) = UserBehaviorProfile(userId = userId)
    }
}

/**
 * Insight user interaction record.
 */
data class InsightFeedback(
    val id: String,
    val userId: String,
    val insightId: String,
    val insightType: Insight.InsightType,
    val outcome: Outcome,
    val timestamp: Long,
) {
    enum class Outcome { CLICKED, DISMISSED, HELPFUL_VOTE_YES, HELPFUL_VOTE_NO, ACTION_EXECUTED }
}
