package com.example.core.monitoring

import android.util.Log

/**
 * Performance Monitoring and Diagnostics Tracer for FinTech transactions,
 * query latencies, and AI inference timings.
 */
object PerformanceTracer {
    private const val TAG = "PerformanceTracer"

    data class TraceMetric(
        val name: String,
        val durationMs: Long,
        val timestamp: Long = System.currentTimeMillis()
    )

    private val metricsHistory = mutableListOf<TraceMetric>()

    fun recordMetric(metricName: String, durationMs: Long) {
        synchronized(metricsHistory) {
            metricsHistory.add(TraceMetric(metricName, durationMs))
            if (metricsHistory.size > 200) metricsHistory.removeAt(0)
        }
        if (durationMs > 100) {
            Log.w(TAG, "[SLOW OPERATION] $metricName took ${durationMs}ms")
        } else {
            Log.d(TAG, "$metricName completed in ${durationMs}ms")
        }
    }

    inline fun <T> trace(metricName: String, block: () -> T): T {
        val start = System.nanoTime()
        return try {
            block()
        } finally {
            val durationMs = (System.nanoTime() - start) / 1_000_000
            recordMetric(metricName, durationMs)
        }
    }

    suspend inline fun <T> traceSuspend(metricName: String, crossinline block: suspend () -> T): T {
        val start = System.nanoTime()
        return try {
            block()
        } finally {
            val durationMs = (System.nanoTime() - start) / 1_000_000
            recordMetric(metricName, durationMs)
        }
    }

    fun getAverageLatency(metricName: String): Double {
        synchronized(metricsHistory) {
            val matches = metricsHistory.filter { it.name == metricName }
            return if (matches.isNotEmpty()) matches.map { it.durationMs }.average() else 0.0
        }
    }
}
