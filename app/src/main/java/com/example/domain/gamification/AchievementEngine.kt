package com.example.domain.gamification

import com.example.domain.checkin.BehaviorTracker
import com.example.domain.repository.Clock

data class Achievement(
    val id: String,
    val title: String,
    val description: String,
    val emoji: String,
    val unlockedAt: Long?,
    val progress: Float,                     // 0..1 for locked ones
)

interface AchievementRepository {
    suspend fun unlockAll(userId: String, achievements: List<Achievement>)
    suspend fun getAchievements(userId: String): List<Achievement>
}

class InMemoryAchievementRepository : AchievementRepository {
    private val unlockedMap = mutableMapOf<Pair<String, String>, Achievement>()

    override suspend fun unlockAll(userId: String, achievements: List<Achievement>) {
        achievements.forEach {
            unlockedMap[userId to it.id] = it
        }
    }

    override suspend fun getAchievements(userId: String): List<Achievement> {
        return unlockedMap.filterKeys { it.first == userId }.values.toList()
    }
}

class AchievementEngine(
    private val achievementRepository: AchievementRepository,
    private val behaviorTracker: BehaviorTracker,
    private val clock: Clock,
) {
    /** Evaluated after every expense insert and at check-in time. */
    suspend fun evaluateAndCelebrate(userId: String): List<Achievement> {
        val streak = behaviorTracker.streakDays(userId)
        val state = listOfNotNull(
            milestone("first_week", "First Week Strong", "7 days without overspending", "🥇", streak, 7),
            milestone("streak_30", "Monthly Master", "30-day discipline streak", "🏅", streak, 30),
            savingsRateAchievement(userId),
            checkInHabit(userId),
        )

        val previouslyUnlocked = achievementRepository.getAchievements(userId).associateBy { it.id }

        val newlyUnlocked = state.filter {
            val prev = previouslyUnlocked[it.id]
            (prev == null || prev.unlockedAt == null) && it.progress >= 1f
        }.map { it.copy(unlockedAt = clock.nowMillis(), progress = 1f) }

        if (newlyUnlocked.isNotEmpty()) {
            achievementRepository.unlockAll(userId, newlyUnlocked)
        }
        return newlyUnlocked
    }

    private fun milestone(
        id: String,
        title: String,
        desc: String,
        emoji: String,
        value: Int,
        target: Int
    ) = Achievement(
        id = id,
        title = title,
        description = desc,
        emoji = emoji,
        unlockedAt = null,
        progress = (value.toFloat() / target).coerceAtMost(1f)
    )

    private suspend fun savingsRateAchievement(u: String): Achievement? {
        val rate = behaviorTracker.monthlySavingsRate(u) ?: return null
        return milestone(
            "saver_20", "Twenty Percent Club", "Saved 20%+ this month", "💰",
            (rate * 100).toInt(), 20
        )
    }

    private suspend fun checkInHabit(u: String): Achievement? {
        val completions = behaviorTracker.consecutiveCheckIns(u)
        return milestone(
            "checkin_14", "Reflection Ritual", "14 consecutive daily check-ins", "🧘",
            completions, 14
        )
    }
}
