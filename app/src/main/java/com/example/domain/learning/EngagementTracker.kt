package com.example.domain.learning

import com.example.domain.model.alerts.AlertSeverity
import com.example.domain.model.alerts.AlertType
import com.example.domain.model.alerts.EngagementProfile
import com.example.domain.model.alerts.EngagementRecord
import com.example.domain.repository.Clock

/**
 * Interface for tracking user engagement with alerts and calculating personalized suppression profiles.
 */
interface EngagementTracker {
    suspend fun record(record: EngagementRecord)
    suspend fun profile(userId: String): EngagementProfile
    suspend fun samplesByType(userId: String): Map<AlertType, Int>
    suspend fun cooldownRemainingMillis(fingerprint: String): Long?
    suspend fun markSent(fingerprint: String, severity: AlertSeverity)
}

/**
 * In-memory implementation of EngagementTracker for tests and session-based state.
 */
class InMemoryEngagementTracker(private val clock: Clock) : EngagementTracker {
    val records = mutableListOf<EngagementRecord>()
    private val sentAlerts = mutableMapOf<String, Pair<Long, AlertSeverity>>()

    override suspend fun record(record: EngagementRecord) {
        records.add(record)
    }

    override suspend fun profile(userId: String): EngagementProfile {
        val sixtyDaysAgo = clock.nowMillis() - (60L * 24 * 60 * 60 * 1000)
        val userRecords = records.filter { it.userId == userId && it.timestamp >= sixtyDaysAgo }
        val byType = userRecords.groupBy { it.alertType }
        val dismissRateByType = byType.mapValues { (_, rs) ->
            if (rs.isEmpty()) 0.0
            else rs.count {
                it.outcome != EngagementRecord.Outcome.ACTION_TAKEN &&
                it.outcome != EngagementRecord.Outcome.TAPPED
            } / rs.size.toDouble()
        }

        val bySeverity = userRecords.groupBy { it.severity }
        val responseRateBySeverity = bySeverity.mapValues { (_, rs) ->
            if (rs.isEmpty()) 0.0
            else rs.count {
                it.outcome == EngagementRecord.Outcome.TAPPED ||
                it.outcome == EngagementRecord.Outcome.ACTION_TAKEN
            } / rs.size.toDouble()
        }

        return EngagementProfile(dismissRateByType, responseRateBySeverity)
    }

    override suspend fun samplesByType(userId: String): Map<AlertType, Int> {
        return records.filter { it.userId == userId }.groupBy { it.alertType }.mapValues { it.value.size }
    }

    override suspend fun cooldownRemainingMillis(fingerprint: String): Long? {
        val last = sentAlerts[fingerprint] ?: return null
        val elapsed = clock.nowMillis() - last.first
        val required = last.second.minIntervalHours * 3600_000L
        val remaining = required - elapsed
        return if (remaining > 0) remaining else null
    }

    override suspend fun markSent(fingerprint: String, severity: AlertSeverity) {
        sentAlerts[fingerprint] = Pair(clock.nowMillis(), severity)
    }
}
