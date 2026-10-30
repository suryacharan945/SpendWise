package com.example.domain.tone

import com.example.domain.model.coach.UserBehaviorProfile

/**
 * Rewrites insight/chat content in the user's learned tone WITHOUT changing facts.
 * Rules-based so it works offline; AI provider can enhance phrasing when available.
 */
class ToneEngine {

    /** Wraps raw message with tone-appropriate opener/closer and adjusts emoji density. */
    fun applyTone(text: String, tone: UserBehaviorProfile.CoachTone, context: EmotionalContext): String =
        when (tone) {
            UserBehaviorProfile.CoachTone.CELEBRATORY ->
                "${context.celebrationEmoji} $text ${context.closingPraise}"
            UserBehaviorProfile.CoachTone.SUPPORTIVE ->
                "$text\n${supportiveCloser(context)}"
            UserBehaviorProfile.CoachTone.DIRECT -> text          // numbers speak for themselves
            UserBehaviorProfile.CoachTone.WARNING ->
                "⏰ $text — the sooner you act, the smaller the fix."
        }.trim()

    private fun supportiveCloser(ctx: EmotionalContext) = when {
        ctx.streakDays >= 3 -> "You've built ${ctx.streakDays} days of momentum — one small step keeps it going. 💪"
        ctx.savingsRate > 0.15 -> "Your savings rate is already strong — this would make it even better."
        else -> "Small consistent changes beat big dramatic ones. You've got this."
    }

    /** Context that shapes emotional framing. */
    data class EmotionalContext(
        val streakDays: Int,
        val savingsRate: Double,
        val recentNegativeEvents: Int,     // overspend alerts in last week -> soften tone
    ) {
        val celebrationEmoji get() = if (streakDays >= 7) "🏆" else "🎉"
        val closingPraise get() = if (recentNegativeEvents == 0)
            "Keep the streak alive!" else "Bounce-back mode: engaged!"
    }
}
