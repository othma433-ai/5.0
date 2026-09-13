package com.waalothmany.linkbot.automation

data class AutomationHealthSnapshot(
    val score: Int,
    val recommendedMode: PerformanceMode,
    val successStreak: Int,
    val transientFailures: Int,
    val ambiguities: Int,
)

/**
 * Session-local governor balancing speed and reliability. It is intentionally deterministic:
 * failures immediately slow the engine, while promotion back to FAST requires a sustained
 * success streak so one lucky transition cannot cause oscillation.
 */
class AutomationHealthPolicy {
    private var score = 100
    private var successStreak = 0
    private var transientFailures = 0
    private var ambiguities = 0
    private var recommended = PerformanceMode.BALANCED

    fun recordSuccess() {
        successStreak++
        score = (score + 2).coerceAtMost(100)
        recommended = when {
            score >= 90 && successStreak >= 10 -> PerformanceMode.FAST
            score >= 65 -> PerformanceMode.BALANCED
            else -> PerformanceMode.SAFE
        }
    }

    fun recordTransientFailure() {
        transientFailures++
        successStreak = 0
        score = (score - 12).coerceAtLeast(0)
        recommended = if (score < 65) PerformanceMode.SAFE else PerformanceMode.BALANCED
    }

    fun recordAmbiguity() {
        ambiguities++
        successStreak = 0
        score = (score - 25).coerceAtLeast(0)
        recommended = PerformanceMode.SAFE
    }

    fun recordHardFailure() {
        transientFailures++
        successStreak = 0
        score = (score - 35).coerceAtLeast(0)
        recommended = PerformanceMode.SAFE
    }

    fun snapshot(): AutomationHealthSnapshot = AutomationHealthSnapshot(
        score = score,
        recommendedMode = recommended,
        successStreak = successStreak,
        transientFailures = transientFailures,
        ambiguities = ambiguities,
    )

    fun reset() {
        score = 100
        successStreak = 0
        transientFailures = 0
        ambiguities = 0
        recommended = PerformanceMode.BALANCED
    }
}
