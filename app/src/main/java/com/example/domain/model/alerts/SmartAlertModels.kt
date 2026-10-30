package com.example.domain.model.alerts

/**
 * SmartAlert model representing a generated actionable alert.
 */
data class SmartAlert(
    val type: AlertType,
    val severity: AlertSeverity,
    val title: String,
    val body: String,
    val confidence: Double = 1.0,        // prediction confidence 0..1
    val action: String = "",
    val fingerprint: String,             // dedup key (type + context hash)
)

/**
 * Result of statistical spending projections.
 */
data class PredictionResult(
    val projectedAmount: Double,
    val confidence: Double,
    val variance: Double,
    val sampleSize: Int,
)

/**
 * User customization preferences for notifications and pacing.
 */
data class UserNotificationPreferences(
    val sensitivity: Sensitivity = Sensitivity.MEDIUM,
    val enabledAlertTypes: Set<AlertType> = AlertType.entries.toSet(),
    val quietHours: TimeWindow = TimeWindow(22, 8),      // 10 PM – 8 AM
    val weekendAllowanceMultiplier: Double = 1.25,       // context-aware spending
    val salaryCycleDay: Int = 1,                         // day of month income arrives
) {
    enum class Sensitivity(val multiplier: Double) {
        LOW(1.5),      // only alert on big deviations
        MEDIUM(1.0),
        HIGH(0.7),     // alert earlier / on smaller deviations
    }
}

/**
 * Daily time window supporting overnight wrap-around (e.g. 22:00 to 08:00).
 */
data class TimeWindow(val startHour: Int, val endHour: Int) {
    fun contains(hour: Int): Boolean =
        if (startHour <= endHour) hour in startHour..endHour
        else hour >= startHour || hour <= endHour   // wraps midnight

    companion object {
        val NONE = TimeWindow(-1, -1)
    }
}

/**
 * User interaction outcome for an alert, feeding the learning loop.
 */
data class EngagementRecord(
    val id: String,
    val userId: String,
    val alertType: AlertType,
    val severity: AlertSeverity,
    val fingerprint: String,
    val outcome: Outcome,
    val timestamp: Long,
) {
    enum class Outcome { DISMISSED, TAPPED, ACTION_TAKEN, EXPIRED_UNSEEN }
}

/**
 * Learned engagement profile per alert type driving automatic suppression.
 */
data class EngagementProfile(
    val dismissRateByType: Map<AlertType, Double>,
    val responseRateBySeverity: Map<AlertSeverity, Double>,
) {
    /**
     * Suppress alerts whose type the user ignores ≥70% of the time (min 5 samples).
     */
    fun shouldSuppress(type: AlertType, samplesFor: Map<AlertType, Int>): Boolean {
        val rate = dismissRateByType[type] ?: return false
        val samples = samplesFor[type] ?: 0
        return samples >= MIN_SAMPLES && rate >= SUPPRESS_THRESHOLD
    }

    private companion object {
        const val MIN_SAMPLES = 5
        const val SUPPRESS_THRESHOLD = 0.7
    }
}
