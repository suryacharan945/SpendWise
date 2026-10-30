package com.example.domain.anomaly

import com.example.domain.model.Category
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Statistical Z-Score Anomaly Detector.
 * Evaluates period-over-period expenditure deviation against historical distribution.
 */
class AnomalyDetector {

    sealed interface Anomaly {
        data class Significant(val zScore: Double, val category: Category?, val current: Double) : Anomaly
        data class Elevated(val zScore: Double, val category: Category?, val current: Double) : Anomaly
        data object None : Anomaly
    }

    /**
     * Z-score detection against rolling baseline of N previous periods.
     *
     * z >= 3.0  → significant anomaly (>99.7% unusual)
     * z >= 2.0  → elevated anomaly
     */
    fun detect(currentPeriodTotal: Double, historicalTotals: List<Double>): Anomaly {
        if (historicalTotals.size < MIN_BASELINE_SAMPLES) return Anomaly.None

        val mean = historicalTotals.average()
        val stdDev = sqrt(historicalTotals.sumOf { (it - mean).pow(2) } / historicalTotals.size)

        return when {
            // Degenerate baseline: historical spending was zero or near-zero
            stdDev < EPSILON ->
                if (currentPeriodTotal >= ZERO_BASELINE_MIN_ABSOLUTE_DELTA && mean < EPSILON)
                    Anomaly.Significant(zScore = 4.0, null, currentPeriodTotal)
                else Anomaly.None

            else -> {
                val z = (currentPeriodTotal - mean) / stdDev
                when {
                    z >= SIGNIFICANT_Z -> Anomaly.Significant(z, null, currentPeriodTotal)
                    z >= ELEVATED_Z -> Anomaly.Elevated(z, null, currentPeriodTotal)
                    else -> Anomaly.None
                }
            }
        }
    }

    /**
     * Evaluates anomalies across multiple spending categories.
     */
    fun detectCategory(
        categoryTotalsThisWeek: Map<Category, Double>,
        weeklyBaselines: Map<Category, List<Double>>,
    ): Map<Category, Anomaly> =
        categoryTotalsThisWeek.mapValues { (cat, total) ->
            detect(total, weeklyBaselines[cat].orEmpty()).let {
                when (it) {
                    is Anomaly.Significant -> it.copy(category = cat)
                    is Anomaly.Elevated -> it.copy(category = cat)
                    else -> it
                }
            }
        }.filterValues { it !is Anomaly.None }

    companion object {
        const val MIN_BASELINE_SAMPLES = 4
        const val SIGNIFICANT_Z = 3.0
        const val ELEVATED_Z = 2.0
        const val EPSILON = 1.0
        const val ZERO_BASELINE_MIN_ABSOLUTE_DELTA = 300.0
    }
}
