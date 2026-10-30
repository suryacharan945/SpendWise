package com.example.domain.coach

import com.example.core.common.Resource
import com.example.domain.action.CoachActionExecutorImpl
import com.example.domain.action.CoachActionRequest
import com.example.domain.action.InMemoryBudgetRepository
import com.example.domain.action.InMemorySavingsGoalRepository
import com.example.domain.chat.CoachIntent
import com.example.domain.chat.CoachResponse
import com.example.domain.chat.ConversationMemory
import com.example.domain.checkin.CheckIn
import com.example.domain.checkin.DailyCheckInEngine
import com.example.domain.checkin.InMemoryBehaviorProfileRepository
import com.example.domain.checkin.InMemoryBehaviorTracker
import com.example.domain.gamification.AchievementEngine
import com.example.domain.gamification.InMemoryAchievementRepository
import com.example.domain.model.Category
import com.example.domain.model.PureExpense
import com.example.domain.model.alerts.AlertSeverity
import com.example.domain.model.coach.Insight
import com.example.domain.model.coach.InsightFeedback
import com.example.domain.model.coach.UserBehaviorProfile
import com.example.domain.personalization.InMemoryInsightFeedbackDao
import com.example.domain.personalization.PersonalizationEngine
import com.example.domain.ranking.InsightRanker
import com.example.domain.repository.Clock
import com.example.domain.repository.PureExpenseRepository
import com.example.domain.timing.InsightTimingEngine
import com.example.domain.timing.PresentationMoment
import com.example.domain.tone.ToneEngine
import java.util.Calendar
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class BehaviorDrivenAssistantTest {

    private lateinit var clock: TestClock
    private lateinit var ranker: InsightRanker
    private lateinit var toneEngine: ToneEngine
    private lateinit var memory: ConversationMemory
    private lateinit var budgetRepo: InMemoryBudgetRepository
    private lateinit var goalRepo: InMemorySavingsGoalRepository
    private lateinit var actionExecutor: CoachActionExecutorImpl
    private lateinit var expenseRepo: TestExpenseRepository
    private lateinit var behaviorTracker: InMemoryBehaviorTracker
    private lateinit var profileRepo: InMemoryBehaviorProfileRepository
    private lateinit var feedbackDao: InMemoryInsightFeedbackDao
    private lateinit var personalization: PersonalizationEngine
    private lateinit var checkInEngine: DailyCheckInEngine
    private lateinit var achievementEngine: AchievementEngine
    private lateinit var timingEngine: InsightTimingEngine
    private lateinit var coachEngine: AICoachEngine

    @Before
    fun setup() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.JUNE)
            set(Calendar.DAY_OF_MONTH, 15)
            set(Calendar.HOUR_OF_DAY, 20)
            set(Calendar.MINUTE, 0)
        }
        clock = TestClock(cal.timeInMillis)
        ranker = InsightRanker(clock)
        toneEngine = ToneEngine()
        memory = ConversationMemory()
        budgetRepo = InMemoryBudgetRepository()
        goalRepo = InMemorySavingsGoalRepository()
        actionExecutor = CoachActionExecutorImpl(budgetRepo, goalRepo)
        expenseRepo = TestExpenseRepository()
        behaviorTracker = InMemoryBehaviorTracker()
        profileRepo = InMemoryBehaviorProfileRepository()
        feedbackDao = InMemoryInsightFeedbackDao()
        personalization = PersonalizationEngine(feedbackDao, profileRepo, clock)
        checkInEngine = DailyCheckInEngine(budgetRepo, expenseRepo, behaviorTracker, toneEngine, profileRepo, clock)
        achievementEngine = AchievementEngine(InMemoryAchievementRepository(), behaviorTracker, clock)
        timingEngine = InsightTimingEngine(clock)
        coachEngine = AICoachEngine(
            ranker = ranker,
            timingEngine = timingEngine,
            toneEngine = toneEngine,
            personalization = personalization,
            checkInEngine = checkInEngine,
            achievementEngine = achievementEngine,
            actionExecutor = actionExecutor,
            conversationMemory = memory,
            profileRepository = profileRepo,
            clock = clock
        )
    }

    // --- 1. Ranker Tests ---

    @Test
    fun `ranker prefers urgent high-impact actionable insights`() {
        val profile = UserBehaviorProfile.defaultFor("user_1")

        val urgentHighImpactActionable = Insight(
            id = "i1",
            type = Insight.InsightType.PREDICTION,
            severity = AlertSeverity.CRITICAL,
            message = "Forecast shows ₹4,800 overrun by EOM",
            generatedAt = clock.nowMillis(),
            confidence = 0.9,
            actionable = Insight.ActionSuggestion("Cut Limit", Insight.CoachAction.SET_CATEGORY_LIMIT)
        )

        val lowImpactInfo = Insight(
            id = "i2",
            type = Insight.InsightType.GENERAL,
            severity = AlertSeverity.INFO,
            message = "Spent ₹150 on snacks yesterday",
            generatedAt = clock.nowMillis() - (5 * 86_400_000L), // 5 days old
            confidence = 0.5,
            actionable = null
        )

        val score1 = ranker.score(urgentHighImpactActionable, profile)
        val score2 = ranker.score(lowImpactInfo, profile)

        assertTrue(score1.total > score2.total)
        assertTrue(score1.urgency > score2.urgency)
        assertTrue(score1.impact > score2.impact)
    }

    @Test
    fun `demoted insight type sinks despite high impact`() {
        val profile = UserBehaviorProfile(
            userId = "user_1",
            insightClickRateByType = mapOf(
                Insight.InsightType.CATEGORY_TREND to 0.05,
                Insight.InsightType.PREDICTION to 0.8
            ),
            samplesByType = mapOf(
                Insight.InsightType.CATEGORY_TREND to 8,
                Insight.InsightType.PREDICTION to 8
            )
        )

        val highImpactTrend = Insight(
            id = "i_trend",
            type = Insight.InsightType.CATEGORY_TREND,
            severity = AlertSeverity.WARNING,
            message = "Dining trend indicates ₹4,500 monthly spend",
            generatedAt = clock.nowMillis(),
            confidence = 0.85
        )

        val lowImpactPrediction = Insight(
            id = "i_pred",
            type = Insight.InsightType.PREDICTION,
            severity = AlertSeverity.WARNING,
            message = "Predicted saving opportunity ₹600",
            generatedAt = clock.nowMillis(),
            confidence = 0.85
        )

        assertTrue(ranker.score(lowImpactPrediction, profile).total > ranker.score(highImpactTrend, profile).total)
    }

    // --- 2. Conversation Memory Tests ---

    @Test
    fun `conversation memory resolves bare follow-up`() = runTest {
        val foodExchange = ConversationMemory.Exchange(
            query = "How much did I spend on food?",
            intent = CoachIntent.CategorySpend(Category.FOOD),
            response = CoachResponse("You spent ₹3,400 on food.", listOf(3400.0)),
            involvedCategories = setOf(Category.FOOD),
            involvedAmounts = listOf(3400.0),
            timestamp = clock.nowMillis()
        )

        memory.remember(foodExchange)
        assertEquals("Why did my food spending change?", memory.resolveFollowUp("why"))
        assertEquals("Show details for Food & Dining (CategorySpend)", memory.resolveFollowUp("details"))
        assertEquals("How do I reduce my food & dining?", memory.resolveFollowUp("how to fix"))
    }

    // --- 3. Tone Engine Tests ---

    @Test
    fun `tone engine never alters numeric facts`() {
        val original = "You spent ₹1,200 on dining"
        val ctx = ToneEngine.EmotionalContext(streakDays = 5, savingsRate = 0.2, recentNegativeEvents = 0)

        for (tone in UserBehaviorProfile.CoachTone.entries) {
            val toned = toneEngine.applyTone(original, tone, ctx)
            assertTrue("Failed for tone $tone: $toned", toned.contains("₹1,200"))
        }
    }

    // --- 4. Daily Check-In Tests ---

    @Test
    fun `evening check-in praises within-limit days in celebratory tone`() = runTest {
        budgetRepo.setDailyLimit("user_1", 1200.0)
        expenseRepo.dailyTotal = 850.0 // within limit

        val profile = UserBehaviorProfile("user_1", preferredTone = UserBehaviorProfile.CoachTone.CELEBRATORY)
        profileRepo.upsert(profile)

        val checkIn = checkInEngine.eveningCheckIn("user_1", UserBehaviorProfile.CoachTone.CELEBRATORY, profile)

        assertTrue(checkIn.content.headline.contains("You stayed within today's limit"))
        assertTrue(checkIn.content.headline.contains("🎉") || checkIn.content.headline.contains("🏆"))
    }

    @Test
    fun `morning check-in reverse budgets daily allowance`() = runTest {
        budgetRepo.setMonthlyBudget("user_1", 30000.0)
        expenseRepo.monthTotal = 15000.0 // 15,000 remaining
        clock.customDaysLeft = 15

        val checkIn = checkInEngine.morningCheckIn("user_1", UserBehaviorProfile.CoachTone.DIRECT)

        assertEquals(1000.0, checkIn.content.suggestedDailyLimit!!, 0.01)
        assertTrue(checkIn.content.bodyLines.any { it.contains("₹1,000") })
    }

    // --- 5. Personalization Loop Tests ---

    @Test
    fun `personalization demotes types after 6 dismissals`() = runTest {
        val insight = Insight(
            id = "ins_1",
            type = Insight.InsightType.CATEGORY_TREND,
            severity = AlertSeverity.INFO,
            message = "Trend update",
            generatedAt = clock.nowMillis()
        )

        repeat(6) {
            personalization.record("user_1", insight, InsightFeedback.Outcome.DISMISSED)
        }

        val updatedProfile = profileRepo.get("user_1")
        assertEquals(0.0, updatedProfile.insightClickRateByType[Insight.InsightType.CATEGORY_TREND] ?: 1.0, 0.01)
        assertEquals(0.5, updatedProfile.relevanceWeight(Insight.InsightType.CATEGORY_TREND), 0.01)
    }

    // --- 6. Timing Engine Tests ---

    @Test
    fun `post-expense moment shows only relevant-category insights`() {
        val profile = UserBehaviorProfile.defaultFor("user_1")

        val foodInsight = Insight(
            id = "f1",
            type = Insight.InsightType.CATEGORY_TREND,
            severity = AlertSeverity.WARNING,
            message = "Your Food & Dining spend surged",
            generatedAt = clock.nowMillis()
        )

        val shoppingInsight = Insight(
            id = "s1",
            type = Insight.InsightType.CATEGORY_TREND,
            severity = AlertSeverity.INFO,
            message = "Your Shopping trend is calm",
            generatedAt = clock.nowMillis()
        )

        val selected = timingEngine.select(
            moment = PresentationMoment.POST_EXPENSE,
            ranked = listOf(foodInsight, shoppingInsight),
            profile = profile,
            justLoggedCategory = Category.FOOD
        )

        assertEquals(1, selected.size)
        assertEquals("f1", selected.first().id)
    }

    // --- 7. Executable Actions Tests ---

    @Test
    fun `coach action executor cuts category limit correctly`() = runTest {
        budgetRepo.setCategoryLimit("user_1", Category.FOOD, 5000.0)

        val request = CoachActionRequest.ReduceCategoryLimit(Category.FOOD, 20)
        val result = actionExecutor.execute("user_1", request)

        assertTrue(result is Resource.Success)
        val newLimit = budgetRepo.categoryLimitOnce("user_1", Category.FOOD)
        assertEquals(4000.0, newLimit, 0.01) // 5000 - 20%
    }

    // --- 8. Gamification Achievement Tests ---

    @Test
    fun `achievement engine unlocks First Week Strong when streak reaches 7`() = runTest {
        behaviorTracker.streak = 7
        val unlocked = achievementEngine.evaluateAndCelebrate("user_1")

        assertTrue(unlocked.any { it.id == "first_week" && it.progress >= 1f })
    }

    // --- Test Doubles ---

    private class TestClock(private var now: Long) : Clock {
        var customDaysLeft: Int? = null
        override fun nowMillis(): Long = now
        override fun startOfToday(): Long = now - (now % (24 * 60 * 60 * 1000L))
        override fun currentMonthRange(): LongRange = startOfToday()..(startOfToday() + 30 * 86_400_000L)
        override fun isEvening(): Boolean = true
        override fun daysAgo(days: Int): Long = now - (days * 86_400_000L)
        override fun currentHour(): Int = 20
        override fun daysLeftInMonth(): Int = customDaysLeft ?: 15
        override fun dayOfWeek(): Int = Calendar.FRIDAY
        override fun epochDay(): Long = now / 86_400_000L
    }

    private class TestExpenseRepository : PureExpenseRepository {
        var dailyTotal: Double = 500.0
        var monthTotal: Double = 10000.0

        override fun observeExpenses(userId: String): Flow<List<PureExpense>> = emptyFlow()
        override suspend fun addExpense(expense: PureExpense): Resource<Unit> = Resource.Success(Unit)
        override suspend fun softDelete(userId: String, expenseId: String): Resource<Unit> = Resource.Success(Unit)
        override suspend fun updateExpense(expense: PureExpense): Resource<Unit> = Resource.Success(Unit)
        override suspend fun totalForDay(userId: String, dayStart: Long): Double = dailyTotal
        override suspend fun totalsBetween(userId: String, start: Long, end: Long): Map<Category, Double> =
            mapOf(Category.FOOD to monthTotal)
        override suspend fun pendingPush(): List<PureExpense> = emptyList()
        override suspend fun recentExpenses(userId: String, hours: Int): List<PureExpense> = emptyList()
    }
}
