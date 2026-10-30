package com.example.domain.timing

import com.example.domain.model.Category
import com.example.domain.model.coach.Insight
import com.example.domain.model.coach.UserBehaviorProfile
import com.example.domain.repository.Clock

enum class PresentationMoment {
    APP_OPEN,           // top-3 ranked cards immediately
    POST_EXPENSE,       // single micro-insight right after logging
    EVENING_REFLECTION, // bundled with evening check-in
    MORNING_PLAN,       // bundled with morning check-in
}

class InsightTimingEngine(
    private val clock: Clock,
) {

    /**
     * Chooses WHICH insights show at each moment:
     *  - App open: top 3 overall (the ranker's job)
     *  - Post-expense: only insights about the JUST-USED category — hyper-relevant,
     *    never interrupt with unrelated budget talk mid-action
     *  - Check-ins: reflective/savings types only (planning mindset != alarm mindset)
     */
    fun select(
        moment: PresentationMoment,
        ranked: List<Insight>,
        profile: UserBehaviorProfile,
        justLoggedCategory: Category? = null
    ): List<Insight> = when (moment) {
        PresentationMoment.APP_OPEN ->
            if (isGoodHour(profile)) ranked.take(3) else ranked.take(1)

        PresentationMoment.POST_EXPENSE ->
            ranked.filter { it.type in POST_EXPENSE_TYPES && mentions(it, justLoggedCategory) }.take(1)

        PresentationMoment.EVENING_REFLECTION ->
            ranked.filter { it.type in REFLECTIVE_TYPES }.take(2)

        PresentationMoment.MORNING_PLAN ->
            ranked.filter {
                it.type == Insight.InsightType.PREDICTION ||
                it.type == Insight.InsightType.BUDGET_USAGE
            }.take(2)
    }

    private fun isGoodHour(profile: UserBehaviorProfile): Boolean =
        clock.currentHour() in profile.optimalEngagementHours

    private fun mentions(i: Insight, c: Category?): Boolean =
        c == null || i.message.contains(c.label, ignoreCase = true) || i.message.contains(c.name, ignoreCase = true)

    companion object {
        val POST_EXPENSE_TYPES = setOf(Insight.InsightType.CATEGORY_TREND, Insight.InsightType.BUDGET_USAGE)
        val REFLECTIVE_TYPES = setOf(Insight.InsightType.SAVINGS_OPPORTUNITY, Insight.InsightType.STREAK_BEHAVIOR)
    }
}
