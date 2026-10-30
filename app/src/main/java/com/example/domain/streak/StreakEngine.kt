package com.example.domain.streak

import com.example.domain.repository.Clock

/**
 * Behavioral Streak Engine calculating break risk probability
 * based on pacing against typical weekday behavior and banked streak strength.
 */
class StreakEngine(private val clock: Clock) {

    data class StreakState(
        val days: Int,
        val strength: Strength,
        val breakRisk: Double,          // 0..1 probability streak breaks TODAY
        val reason: String?,
    ) {
        enum class Strength { FRAGILE, BUILDING, ESTABLISHED, IRONCLAD }
    }

    /**
     * Evaluates the risk that an active streak will break today.
     */
    fun evaluate(
        streakDays: Int,
        todaySpentSoFar: Double,
        dailyLimit: Double,
        typicalSpendOnThisWeekday: Double,
        historicalOverspendRate: Double,
        hourOfDay: Int,
    ): StreakState {
        val strength = when {
            streakDays >= 30 -> StreakState.Strength.IRONCLAD
            streakDays >= 7 -> StreakState.Strength.ESTABLISHED
            streakDays >= 3 -> StreakState.Strength.BUILDING
            else -> StreakState.Strength.FRAGILE
        }

        val dayProgress = (hourOfDay.coerceAtLeast(1)).toDouble() / 24.0

        val expectedByNow = typicalSpendOnThisWeekday * dayProgress
        val paceFactor = if (expectedByNow > 0)
            ((todaySpentSoFar / expectedByNow)).coerceIn(0.5, 3.0) / 3.0
        else dayProgress

        val volatilityFactor = 0.7 + historicalOverspendRate * 0.6
        val strengthBuffer = strengthDays(streakDays) * 0.004   // up to 0.12 reduction

        val rawRisk = paceFactor * volatilityFactor * dayProgressCurve(hourOfDay) - strengthBuffer
        val breakRisk = rawRisk.coerceIn(0.0, 1.0)

        return StreakState(
            days = streakDays,
            strength = strength,
            breakRisk = breakRisk,
            reason = when {
                breakRisk > 0.7 -> "Pace well above your usual $streakDays-day pattern"
                breakRisk > 0.45 -> "Spending faster than your typical day"
                else -> null
            },
        )
    }

    private fun dayProgressCurve(hour: Int): Double = when (hour) {
        in 0..11 -> 0.3
        in 12..17 -> 0.7
        in 18..21 -> 1.0      // peak alert window
        else -> 0.85          // late night: still risky
    }

    private fun strengthDays(d: Int) = d.toDouble().coerceAtMost(30.0)
}
