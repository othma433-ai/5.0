package com.waalothmany.linkbot.automation

import kotlin.math.roundToLong

/**
 * Learns how quickly the current WhatsApp/OEM build actually rebinds its UI.
 * The policy starts from the selected performance profile, then converges toward
 * observed event latency while staying inside conservative floors/caps.
 */
class AdaptiveTimingPolicy(
    private val profile: PerformanceProfile,
    private val minProbeMs: Long = 90,
    private val maxProbeMs: Long = 1_600,
) {
    private var ewmaLatencyMs: Double = profile.scrollProbeMs.toDouble()
    private var samples: Int = 0

    fun observeUiLatency(latencyMs: Long) {
        if (latencyMs !in 20..5_000) return
        val alpha = if (samples < 3) 0.55 else 0.35
        ewmaLatencyMs = (alpha * latencyMs) + ((1.0 - alpha) * ewmaLatencyMs)
        samples++
    }

    fun nextProbeDelay(scrollAccepted: Boolean): Long {
        val learned = (ewmaLatencyMs * 1.10).roundToLong().coerceIn(minProbeMs, maxProbeMs)
        return if (scrollAccepted) {
            learned
        } else {
            minOf(learned, profile.rejectedScrollProbeMs.coerceAtLeast(minProbeMs))
        }
    }

    fun reset() {
        ewmaLatencyMs = profile.scrollProbeMs.toDouble()
        samples = 0
    }
}
