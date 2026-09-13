package com.waalothmany.linkbot.automation

enum class CircuitDecision { RETRY, TRIP }

/**
 * Prevents a broken selector/stage from looping forever. Failures are isolated per
 * stage and a verified successful transition resets only that stage's counter.
 */
class StageCircuitBreaker(private val maxConsecutiveFailures: Int = 3) {
    init { require(maxConsecutiveFailures >= 1) }

    private val failures = HashMap<String, Int>()

    fun recordFailure(stage: String): CircuitDecision {
        val count = (failures[stage] ?: 0) + 1
        failures[stage] = count
        return if (count >= maxConsecutiveFailures) CircuitDecision.TRIP else CircuitDecision.RETRY
    }

    fun recordSuccess(stage: String) {
        failures.remove(stage)
    }

    fun failureCount(stage: String): Int = failures[stage] ?: 0

    fun reset() = failures.clear()
}
