package com.example.domain.chat

import com.example.domain.model.Category
import java.util.ArrayDeque
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

sealed class CoachIntent {
    data class CategorySpend(val category: Category) : CoachIntent()
    data class AffordabilityCheck(val amount: Double) : CoachIntent()
    data object TotalSpend : CoachIntent()
    data class General(val topic: String) : CoachIntent()
}

data class CoachResponse(
    val text: String,
    val amounts: List<Double> = emptyList()
)

/**
 * Follow-ups ("Why?", "Show me details", "How to fix?") resolve against the LAST
 * substantive exchange instead of failing intent detection.
 */
class ConversationMemory {

    data class Exchange(
        val query: String,
        val intent: CoachIntent?,
        val response: CoachResponse,
        val involvedCategories: Set<Category>,
        val involvedAmounts: List<Double>,
        val timestamp: Long,
    )

    private val history = ArrayDeque<Exchange>()

    fun remember(exchange: Exchange) {
        synchronized(history) {
            history.addLast(exchange)
            while (history.size > MAX_TURNS) history.removeFirst()
        }
    }

    fun lastSubstantive(): Exchange? = synchronized(history) {
        history.lastOrNull { it.intent != null }
    }

    /**
     * If new input is anaphoric/follow-up, rewrite it into a full standalone question
     * using memory context.
     */
    fun resolveFollowUp(rawQuery: String): String? {
        val q = rawQuery.trim().lowercase()
        val last = lastSubstantive() ?: return null

        return when {
            q in WHY_PHRASES -> last.intent?.whyQuestion(last.involvedCategories.firstOrNull())
            q in DETAILS_PHRASES -> "Show details for ${last.topicDescription()}"
            q in FIX_PHRASES -> "How do I reduce my ${last.involvedCategories.firstOrNull()?.label?.lowercase() ?: "spending"}?"
            q in CONTINUATION && rawQuery.length < FOLLOWUP_MAX_LEN ->
                "${last.topicDescription()} $rawQuery"
            else -> null
        }
    }

    private fun Exchange.topicDescription(): String = buildString {
        append(involvedCategories.firstOrNull()?.label ?: "my spending")
        if (involvedAmounts.isNotEmpty()) append(" (${intent?.let { it::class.simpleName } ?: "Insight"})")
    }

    private fun CoachIntent.whyQuestion(category: Category?): String = when (this) {
        is CoachIntent.CategorySpend -> {
            val catStr = if ((category ?: this.category) == Category.FOOD) "food" else (category?.label?.lowercase() ?: this.category.label.lowercase())
            "Why did my $catStr spending change?"
        }
        is CoachIntent.AffordabilityCheck -> "Why can or can't I afford ₹%,.0f?".format(amount)
        is CoachIntent.TotalSpend -> "What drove my spending this period?"
        is CoachIntent.General -> "What's behind these numbers?"
    }

    companion object {
        private val WHY_PHRASES = setOf("why", "why?", "why is that", "what happened")
        private val DETAILS_PHRASES = setOf("show me details", "details", "show details", "more info", "elaborate")
        private val FIX_PHRASES = setOf("how to fix", "how do i fix it", "what should i do", "fix it")
        private val CONTINUATION = setOf("and last month", "and yesterday", "and last week", "compared to last month")
        private const val MAX_TURNS = 12
        private const val FOLLOWUP_MAX_LEN = 40
    }
}
