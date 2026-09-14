package com.waalothmany.linkbot.runtime.engine

/**
 * Small deterministic circuit breaker used by the execution orchestrator.
 * It is intentionally time-source agnostic; callers pass monotonic timestamps.
 */
class EngineCircuitBreaker(
    private val maxFailures: Int,
    private val openMs: Long,
) {
    init {
        require(maxFailures > 0)
        require(openMs > 0)
    }

    private var failures: Int = 0
    private var openUntilMs: Long = 0L

    @Synchronized
    fun allow(nowMs: Long): Boolean = nowMs >= openUntilMs

    @Synchronized
    fun recordSuccess() {
        failures = 0
        openUntilMs = 0L
    }

    @Synchronized
    fun recordFailure(nowMs: Long) {
        failures += 1
        if (failures >= maxFailures) {
            openUntilMs = nowMs + openMs
            failures = 0
        }
    }

    @Synchronized
    fun snapshot(nowMs: Long): CircuitSnapshot = CircuitSnapshot(
        open = nowMs < openUntilMs,
        openUntilMs = openUntilMs,
        pendingFailures = failures,
    )
}

data class CircuitSnapshot(
    val open: Boolean,
    val openUntilMs: Long,
    val pendingFailures: Int,
)
