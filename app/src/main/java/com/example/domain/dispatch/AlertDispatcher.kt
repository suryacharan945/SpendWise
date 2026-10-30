package com.example.domain.dispatch

import com.example.core.monitoring.Notifier
import com.example.domain.learning.EngagementTracker
import com.example.domain.model.alerts.AlertSeverity
import com.example.domain.model.alerts.AlertType
import com.example.domain.model.alerts.SmartAlert
import com.example.domain.model.alerts.UserNotificationPreferences
import com.example.domain.repository.Clock
import java.util.Calendar

/**
 * Analytics hook interface for notification telemetry.
 */
interface AlertAnalyticsHook {
    fun onTriggered(userId: String, alert: SmartAlert)
    fun onTapped(userId: String, alertType: AlertType, fingerprint: String)
    fun onDismissed(userId: String, alertType: AlertType, fingerprint: String)
    fun onActionTaken(userId: String, alertType: AlertType, fingerprint: String)
    fun onDeliveredViaDigest(userId: String, alert: SmartAlert)
}

class NoopAlertAnalyticsHook : AlertAnalyticsHook {
    override fun onTriggered(userId: String, alert: SmartAlert) {}
    override fun onTapped(userId: String, alertType: AlertType, fingerprint: String) {}
    override fun onDismissed(userId: String, alertType: AlertType, fingerprint: String) {}
    override fun onActionTaken(userId: String, alertType: AlertType, fingerprint: String) {}
    override fun onDeliveredViaDigest(userId: String, alert: SmartAlert) {}
}

/**
 * Routes alerts by severity:
 *  - CRITICAL -> immediate push (bypasses quiet hours unless hard muted)
 *  - WARNING  -> pushed immediately during active hours, queued in quiet hours
 *  - INFO     -> batched into daily digest
 * Enforces learned suppression, user opt-outs, and fingerprint cooldowns.
 */
class AlertDispatcher(
    private val notifier: Notifier,
    private val engagementTracker: EngagementTracker,
    private val preferencesProvider: suspend (String) -> UserNotificationPreferences = { UserNotificationPreferences() },
    private val analyticsHook: AlertAnalyticsHook = NoopAlertAnalyticsHook(),
    private val clock: Clock,
) {
    val infoQueue = mutableListOf<SmartAlert>()
    val quietHoursQueue = mutableListOf<SmartAlert>()

    suspend fun dispatch(userId: String, alerts: List<SmartAlert>) {
        val prefs = preferencesProvider(userId)
        val profile = engagementTracker.profile(userId)
        val samples = engagementTracker.samplesByType(userId)

        alerts
            .distinctBy { it.fingerprint }
            .filter { it.type in prefs.enabledAlertTypes }
            .filterNot { profile.shouldSuppress(it.type, samples) }
            .forEach { alert ->
                route(userId, alert, prefs, engagementTracker.cooldownRemainingMillis(alert.fingerprint))
            }
    }

    private suspend fun route(
        userId: String,
        alert: SmartAlert,
        prefs: UserNotificationPreferences,
        cooldownRemainingMillis: Long?,
    ) {
        // Cooldown check prevents repeated spam about the same trigger
        if (cooldownRemainingMillis != null && alert.severity != AlertSeverity.CRITICAL) return

        // Low-confidence predictions get downgraded rather than dropped
        val effective = if (alert.confidence < CONFIDENCE_FLOOR && alert.severity == AlertSeverity.CRITICAL) {
            alert.copy(
                severity = AlertSeverity.WARNING,
                body = "${alert.body}\n(early estimate — may change)"
            )
        } else alert

        val currentHour = Calendar.getInstance().apply { timeInMillis = clock.nowMillis() }.get(Calendar.HOUR_OF_DAY)
        val inQuietHours = prefs.quietHours.contains(currentHour)

        when {
            effective.severity == AlertSeverity.CRITICAL -> {
                notifyNow(effective)
            }
            inQuietHours -> {
                synchronized(quietHoursQueue) { quietHoursQueue += effective }
            }
            effective.severity == AlertSeverity.INFO -> {
                synchronized(infoQueue) { infoQueue += effective }
            }
            else -> {
                notifyNow(effective)
            }
        }
        engagementTracker.markSent(effective.fingerprint, effective.severity)
        analyticsHook.onTriggered(userId, effective)
    }

    private fun notifyNow(alert: SmartAlert) {
        notifier.send(
            id = alert.fingerprint.hashCode(),
            title = alert.title,
            body = alert.body,
            action = alert.action
        )
    }

    suspend fun flushDigest(userId: String): List<SmartAlert> {
        val queued = synchronized(infoQueue) {
            val copy = infoQueue.toList()
            infoQueue.clear()
            copy
        }
        if (queued.isEmpty()) return emptyList()

        val lines = queued.joinToString("\n") { "${severityEmoji(it.severity)} ${it.title}: ${it.body}" }
        notifier.send(
            id = "digest_${clock.nowMillis()}".hashCode(),
            title = "📋 Your Daily Summary (${queued.size})",
            body = lines,
            action = "OpenDashboard"
        )
        queued.forEach { analyticsHook.onDeliveredViaDigest(userId, it) }
        return queued
    }

    private fun severityEmoji(s: AlertSeverity) = when (s) {
        AlertSeverity.CRITICAL -> "🚨"
        AlertSeverity.WARNING -> "⚠️"
        AlertSeverity.INFO -> "💡"
    }

    companion object {
        const val CONFIDENCE_FLOOR = 0.35
    }
}
