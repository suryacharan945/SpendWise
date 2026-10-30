package com.example.domain.ranking

import com.example.domain.model.alerts.AlertSeverity
import com.example.domain.model.coach.Insight
import com.example.domain.model.coach.UserBehaviorProfile
import com.example.domain.repository.Clock
import kotlin.math.exp
import kotlin.math.ln

/**
 * Multi-factor score: urgency * impact * relevance * freshness.
 * Deterministic, pure, testable — top N survive to the UI.
 */
class InsightRanker(private val clock: Clock) {

    data class ScoreBreakdown(
        val total: Double,
        val urgency: Double,        // how soon acting matters
        val impact: Double,         // rupees at stake
        val relevance: Double,      // learned per-user weight
        val actionability: Double,  // insights with a concrete action rank higher
    )

    fun rank(insights: List<Insight>, profile: UserBehaviorProfile): List<Insight> =
        insights.sortedByDescending { score(it, profile).total }

    fun topN(insights: List<Insight>, profile: UserBehaviorProfile, n: Int = TOP_N): List<Insight> =
        rank(insights, profile).take(n)

    fun score(insight: Insight, profile: UserBehaviorProfile): ScoreBreakdown {
        // URGENCY: severity baseline + type-specific time decay pressure.
        val severityUrgency = when (insight.severity) {
            AlertSeverity.CRITICAL -> 1.0
            AlertSeverity.WARNING -> 0.65
            AlertSeverity.INFO -> 0.35
        }
        val ageDays = (clock.nowMillis() - insight.generatedAt).toDouble() / DAY_MS
        val recency = exp(-ageDays / HALF_LIFE_DAYS * LN2_INV)     // exponential decay, 3-day half-life
        val urgency = severityUrgency * 0.7 + recency * 0.3

        // IMPACT: normalized by confidence — a shaky ₹10k claim loses to a solid ₹800 one.
        val impact = extractImpact(insight.message) * insight.confidence.coerceIn(0.3, 1.0)

        // RELEVANCE: learned click behavior per type.
        val relevance = profile.relevanceWeight(insight.type).let { w ->
            // Tone mismatch penalty: WARNING insights to users who respond to celebration underperform
            if (insight.severity == AlertSeverity.CRITICAL &&
                profile.preferredTone == UserBehaviorProfile.CoachTone.SUPPORTIVE &&
                profile.samplesByType.values.sum() > 10
            ) {
                w * 0.9
            } else {
                w
            }
        }

        val actionability = if (insight.actionable != null) 1.15 else 0.85

        val total = urgency * W_URGENCY + impact * W_IMPACT +
                relevance * W_RELEVANCE * actionability

        return ScoreBreakdown(
            total = total,
            urgency = urgency,
            impact = impact,
            relevance = relevance,
            actionability = actionability,
        )
    }

    /**
     * Pulls the largest currency figure from the message text as proxy for financial impact,
     * then squashes to 0..1 against a saturation point (₹5000 ≈ max practical impact).
     */
    private fun extractImpact(message: String): Double {
        return try {
            val amounts = AMOUNT_REGEX.findAll(message)
                .map { it.groupValues[1].replace(",", "").toDoubleOrNull() ?: 0.0 }
                .filter { !it.isNaN() && !it.isInfinite() && it > 0.0 }
                .toList()
            val max = amounts.maxOrNull() ?: return 0.2
            (max / SATURATION_AMOUNT).takeIf { !it.isNaN() }?.coerceIn(0.05, 1.0) ?: 0.2
        } catch (e: Exception) {
            0.2
        }
    }

    companion object {
        const val TOP_N = 3
        const val W_URGENCY = 0.35
        const val W_IMPACT = 0.30
        const val W_RELEVANCE = 0.35
        const val DAY_MS = 86_400_000L
        const val HALF_LIFE_DAYS = 3.0
        const val SATURATION_AMOUNT = 5000.0
        private val LN2_INV = 1.0 / ln(2.0)
        private val AMOUNT_REGEX = Regex("""₹\s?([\d,]+(?:\.\d+)?)""")
    }
}
