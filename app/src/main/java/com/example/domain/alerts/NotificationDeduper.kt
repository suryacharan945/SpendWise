package com.example.domain.alerts

import com.example.domain.repository.Clock

interface DedupDataStore {
    suspend fun getInt(key: String, default: Int = -1): Int
    suspend fun put(key: String, value: Int)
}

class InMemoryDedupDataStore : DedupDataStore {
    private val store = mutableMapOf<String, Int>()

    override suspend fun getInt(key: String, default: Int): Int =
        store[key] ?: default

    override suspend fun put(key: String, value: Int) {
        store[key] = value
    }
}

/**
 * Worker-path notification deduplication engine.
 * Ensures alerts sharing the same deduplication key (e.g. `streak_risk`, `budget_pace`,
 * or `unusual_spending_${category}`) are sent at most once per calendar day.
 */
class NotificationDeduper(private val prefs: DedupDataStore) {

    suspend fun shouldSend(key: String, clock: Clock): Boolean {
        val lastSentDay = prefs.getInt("sent_$key", -1)
        val today = clock.epochDay().toInt()
        if (lastSentDay == today) return false
        prefs.put("sent_$key", today)
        return true
    }

    suspend fun markDelivered(key: String, clock: Clock) {
        prefs.put("sent_$key", clock.epochDay().toInt())
    }

    suspend fun reset(key: String) {
        prefs.put("sent_$key", -1)
    }
}
