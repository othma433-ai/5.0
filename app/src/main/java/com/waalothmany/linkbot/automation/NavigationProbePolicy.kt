package com.waalothmany.linkbot.automation

/**
 * Bounded fallback polling for navigation stages. Accessibility events remain the
 * primary driver; these probes only recover when an OEM/WhatsApp build does not
 * emit a usable event after an action.
 */
object NavigationProbePolicy {
    private data class Config(
        val firstDelayMs: Long,
        val stepMs: Long,
        val maxDelayMs: Long,
        val attempts: Int,
    )

    private fun config(mode: PerformanceMode): Config = when (mode) {
        PerformanceMode.FAST -> Config(firstDelayMs = 90, stepMs = 45, maxDelayMs = 300, attempts = 12)
        PerformanceMode.BALANCED -> Config(firstDelayMs = 130, stepMs = 65, maxDelayMs = 420, attempts = 14)
        PerformanceMode.SAFE -> Config(firstDelayMs = 200, stepMs = 80, maxDelayMs = 650, attempts = 18)
    }

    fun maxAttempts(mode: PerformanceMode): Int = config(mode).attempts

    fun delayMs(mode: PerformanceMode, attempt: Int): Long {
        require(attempt >= 0)
        val cfg = config(mode)
        return (cfg.firstDelayMs + (cfg.stepMs * attempt)).coerceAtMost(cfg.maxDelayMs)
    }

    fun totalBudgetMs(mode: PerformanceMode): Long =
        (0 until maxAttempts(mode)).sumOf { delayMs(mode, it) }
}
