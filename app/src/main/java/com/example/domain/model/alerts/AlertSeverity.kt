package com.example.domain.model.alerts

/**
 * Severity level of proactive financial alerts.
 * Pure Kotlin enum with priority mapping and rate-limiting cooldown intervals.
 */
enum class AlertSeverity {
    INFO,      // gentle nudge — batched, quiet delivery
    WARNING,   // standard heads-up — normal channel
    CRITICAL;  // immediate push, sound + vibration, bypasses batching

    /** Severity → Android notification priority mapping (-1: Low, 0: Default, 1: High). */
    val androidPriority: Int
        get() = when (this) {
            INFO -> -1     // NotificationCompat.PRIORITY_LOW
            WARNING -> 0   // NotificationCompat.PRIORITY_DEFAULT
            CRITICAL -> 1  // NotificationCompat.PRIORITY_HIGH
        }

    val minIntervalHours: Long
        get() = when (this) {
            INFO -> 72L    // max one per 3 days
            WARNING -> 24L
            CRITICAL -> 6L
        }
}
