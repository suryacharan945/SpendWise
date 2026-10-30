package com.example.domain.checkin

import com.example.domain.action.BudgetRepository
import com.example.domain.model.coach.UserBehaviorProfile
import com.example.domain.repository.Clock
import com.example.domain.repository.PureExpenseRepository
import com.example.domain.tone.ToneEngine
import java.util.Calendar
import kotlin.math.abs

data class CheckIn(
    val id: String,
    val userId: String,
    val dateEpochDay: Long,
    val slot: Slot,
    val content: CheckInContent,
    val wasShown: Boolean = false,
    val userResponseRating: Int? = null,     // optional 1 for thumbs up, -1 for thumbs down
) {
    enum class Slot(val hourWindow: IntRange, val title: String) {
        MORNING(6..11, "☀️ Today's Plan"),
        EVENING(18..22, "🌙 Evening Reflection"),
    }
}

data class CheckInContent(
    val headline: String,
    val bodyLines: List<String>,
    val suggestedDailyLimit: Double?,        // morning only
    val reflectionQuestion: String?,         // evening only
)

interface BehaviorTracker {
    suspend fun typicalSpendForWeekday(userId: String, dayOfWeek: Int): Double
    suspend fun streakDays(userId: String): Int
    suspend fun monthlySavingsRate(userId: String): Double?
    suspend fun consecutiveCheckIns(userId: String): Int
    suspend fun recentOverspendCount(userId: String, days: Int = 7): Int
}

class InMemoryBehaviorTracker : BehaviorTracker {
    var typicalSpend: Double = 600.0
    var streak: Int = 7
    var savingsRate: Double? = 0.25
    var checkIns: Int = 5
    var overspendCount: Int = 0

    override suspend fun typicalSpendForWeekday(userId: String, dayOfWeek: Int): Double = typicalSpend
    override suspend fun streakDays(userId: String): Int = streak
    override suspend fun monthlySavingsRate(userId: String): Double? = savingsRate
    override suspend fun consecutiveCheckIns(userId: String): Int = checkIns
    override suspend fun recentOverspendCount(userId: String, days: Int): Int = overspendCount
}

interface BehaviorProfileRepository {
    suspend fun get(userId: String): UserBehaviorProfile
    suspend fun upsert(profile: UserBehaviorProfile)
    suspend fun observedOpenHours(userId: String): List<Int>
    suspend fun checkInRate(userId: String): Double
}

class InMemoryBehaviorProfileRepository : BehaviorProfileRepository {
    private val profiles = mutableMapOf<String, UserBehaviorProfile>()
    var openHours: List<Int> = listOf(9, 13, 20)
    var checkInRateValue: Double = 0.75

    override suspend fun get(userId: String): UserBehaviorProfile =
        profiles[userId] ?: UserBehaviorProfile.defaultFor(userId)

    override suspend fun upsert(profile: UserBehaviorProfile) {
        profiles[profile.userId] = profile
    }

    override suspend fun observedOpenHours(userId: String): List<Int> = openHours
    override suspend fun checkInRate(userId: String): Double = checkInRateValue
}

class DailyCheckInEngine(
    private val budgetRepository: BudgetRepository,
    private val expenseRepository: PureExpenseRepository,
    private val behaviorTracker: BehaviorTracker,
    private val toneEngine: ToneEngine,
    private val behaviorProfileRepo: BehaviorProfileRepository,
    private val clock: Clock,
) {

    suspend fun generate(userId: String, forcedSlot: CheckIn.Slot? = null): CheckIn? {
        val hour = clock.currentHour()
        val slot = forcedSlot ?: CheckIn.Slot.entries.firstOrNull { hour in it.hourWindow } ?: return null
        val profile = behaviorProfileRepo.get(userId)
        val tone = profile.preferredTone

        val checkIn = when (slot) {
            CheckIn.Slot.MORNING -> morningCheckIn(userId, tone)
            CheckIn.Slot.EVENING -> eveningCheckIn(userId, tone, profile)
        }
        return checkIn.copy(id = "${userId}_${slot.name}_${clock.epochDay()}")
    }

    /** Morning: reverse-budget today's allowance from remaining month headroom. */
    suspend fun morningCheckIn(u: String, tone: UserBehaviorProfile.CoachTone): CheckIn {
        val budget = budgetRepository.monthlyBudgetOnce(u) ?: 30000.0
        val daysLeft = clock.daysLeftInMonth().coerceAtLeast(1)
        val monthSpent = expenseRepository.totalsBetween(u, clock.currentMonthRange().first, clock.nowMillis()).values.sum()
        val remaining = (budget - monthSpent).coerceAtLeast(0.0)
        val dailyAllowance = remaining / daysLeft
        val typicalToday = behaviorTracker.typicalSpendForWeekday(u, clock.dayOfWeek())

        val lines = buildList {
            add("Safe to spend today: ₹%,.0f".format(dailyAllowance))
            add("Your usual %s spend: ₹%,.0f".format(weekdayName(clock.dayOfWeek()), typicalToday))
            if (dailyAllowance < typicalToday) {
                add("⚠️ Tighter than usual — month-end discipline pays off.")
            }
        }
        return CheckIn(
            id = "",
            userId = u,
            dateEpochDay = clock.epochDay(),
            slot = CheckIn.Slot.MORNING,
            content = CheckInContent(
                headline = toneEngine.applyTone("Here's your spending plan for today.", tone, toneContext(u)),
                bodyLines = lines,
                suggestedDailyLimit = dailyAllowance,
                reflectionQuestion = null
            )
        )
    }

    /** Evening: what happened today + one forward-looking suggestion. */
    suspend fun eveningCheckIn(u: String, tone: UserBehaviorProfile.CoachTone, p: UserBehaviorProfile): CheckIn {
        val spentToday = expenseRepository.totalForDay(u, clock.startOfToday())
        val limit = budgetRepository.dailyLimit(u)
        val within = spentToday <= limit
        val savedVsTypical = behaviorTracker.typicalSpendForWeekday(u, clock.dayOfWeek()) - spentToday

        val headline = toneEngine.applyTone(
            if (within) "You stayed within today's limit — nice."
            else "Today went over by ₹%,.0f — tomorrow is a fresh start.".format(spentToday - limit),
            tone, toneContext(u)
        )

        val bodyLines = buildList {
            add("Spent today: ₹%,.0f of ₹%,.0f".format(spentToday, limit))
            if (savedVsTypical > 100) {
                add("That's ₹%,.0f less than your typical day 📉".format(savedVsTypical))
            }
        }

        return CheckIn(
            id = "",
            userId = u,
            dateEpochDay = clock.epochDay(),
            slot = CheckIn.Slot.EVENING,
            content = CheckInContent(
                headline = headline,
                bodyLines = bodyLines,
                suggestedDailyLimit = null,
                reflectionQuestion = pickReflection(p),
            )
        )
    }

    private fun pickReflection(p: UserBehaviorProfile): String =
        REFLECTIONS[abs(p.lastCoachingTouch.hashCode()) % REFLECTIONS.size]

    private suspend fun toneContext(u: String): ToneEngine.EmotionalContext {
        val streak = behaviorTracker.streakDays(u)
        val rate = behaviorTracker.monthlySavingsRate(u) ?: 0.0
        val negativeEvents = behaviorTracker.recentOverspendCount(u, days = 7)
        return ToneEngine.EmotionalContext(streak, rate, negativeEvents)
    }

    private fun weekdayName(dayOfWeek: Int): String = when (dayOfWeek) {
        Calendar.MONDAY -> "Monday"
        Calendar.TUESDAY -> "Tuesday"
        Calendar.WEDNESDAY -> "Wednesday"
        Calendar.THURSDAY -> "Thursday"
        Calendar.FRIDAY -> "Friday"
        Calendar.SATURDAY -> "Saturday"
        Calendar.SUNDAY -> "Sunday"
        else -> "Weekday"
    }

    private companion object {
        val REFLECTIONS = listOf(
            "What's one purchase today you'd reconsider?",
            "Which category felt worth it this week?",
            "What are you saving toward right now?"
        )
    }
}
