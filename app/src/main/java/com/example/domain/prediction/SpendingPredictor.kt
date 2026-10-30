package com.example.domain.prediction

import com.example.domain.model.alerts.PredictionResult
import com.example.domain.repository.Clock
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Weighted moving average projection with weekday/weekend adjustment.
 * Replaces naive linear spending projections with decaying 14-day sample weights
 * and population variance confidence calculations.
 */
class SpendingPredictor(private val clock: Clock) {

    /**
     * Projects month-end total from daily spending history.
     */
    fun projectMonthEnd(
        dailyTotals: Map<Long, Double>,   // epochDay -> spent
        monthRange: ClosedRange<Long>,    // epochDays
        todayEpochDay: Long,
        weekendMultiplier: Double = 1.25, // personalization override
    ): PredictionResult {
        val window = (todayEpochDay - PREDICTION_WINDOW_DAYS + 1)..todayEpochDay

        val weekdays = window.filter(::isWeekday).mapNotNull { dailyTotals[it] }
        val weekends = window.filterNot(::isWeekday).mapNotNull { dailyTotals[it] }

        if (weekdays.isEmpty() && weekends.isEmpty()) {
            return PredictionResult(0.0, confidence = 0.0, variance = 0.0, sampleSize = 0)
        }

        val wmaWeekday = weightedAverage(weekdays)
        val avgWeekend = weightedAverage(weekends).takeUnless { it == 0.0 }
            ?: (wmaWeekday * weekendMultiplier)

        val windowValues = window.mapNotNull { dailyTotals[it] }
        val variance = populationVariance(windowValues)
        val stdDev = sqrt(variance)
        val mean = windowValues.average().takeIf { !it.isNaN() } ?: 0.0
        val coefficientOfVariation = if (mean > 0) stdDev / mean else 1.0
        val sampleConfidence = minOf(1.0, (weekdays.size + weekends.size).toDouble() / MIN_RELIABLE_SAMPLES)
        val stabilityConfidence = (1.0 - coefficientOfVariation).coerceIn(0.0, 1.0)

        // Remaining days in month split by weekday vs weekend
        val remainingDays = (monthRange.endInclusive - todayEpochDay).coerceAtLeast(0)
        val remainingWeekdays = ((todayEpochDay + 1)..monthRange.endInclusive).count(::isWeekday)
        val remainingWeekends = (remainingDays - remainingWeekdays).coerceAtLeast(0)

        val alreadySpent = (monthRange.start..todayEpochDay).sumOf { dailyTotals[it] ?: 0.0 }
        val projectedRemaining = (remainingWeekdays * wmaWeekday) + (remainingWeekends * avgWeekend)

        return PredictionResult(
            projectedAmount = alreadySpent + projectedRemaining,
            confidence = sampleConfidence * stabilityConfidence,
            variance = variance,
            sampleSize = weekdays.size + weekends.size,
        )
    }

    private fun weightedAverage(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        var weightSum = 0.0
        var acc = 0.0
        values.forEachIndexed { i, v ->
            val w = i + 1.0                       // later index = more recent = higher weight
            acc += v * w
            weightSum += w
        }
        return acc / weightSum
    }

    fun isWeekday(epochDay: Long): Boolean {
        // 1970-01-01 was Thursday (day 4). (epochDay + 4) % 7: 0=Sun, 1=Mon, ..., 5=Fri, 6=Sat
        val dayOfWeek = (((epochDay + 4) % 7) + 7) % 7
        return dayOfWeek != 0L && dayOfWeek != 6L
    }

    private fun populationVariance(xs: List<Double>): Double {
        if (xs.size < 2) return 0.0
        val mean = xs.average()
        return xs.sumOf { (it - mean).pow(2) } / xs.size
    }

    companion object {
        const val PREDICTION_WINDOW_DAYS = 14
        const val MIN_RELIABLE_SAMPLES = 10
    }
}
