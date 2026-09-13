package com.waalothmany.linkbot.automation

fun main() {
    val breaker = StageCircuitBreaker(maxConsecutiveFailures = 3)
    check(breaker.recordFailure("OPEN_SEARCH") == CircuitDecision.RETRY)
    check(breaker.recordFailure("OPEN_SEARCH") == CircuitDecision.RETRY)
    check(breaker.recordFailure("OPEN_SEARCH") == CircuitDecision.TRIP)
    check(breaker.failureCount("OPEN_SEARCH") == 3)

    breaker.recordSuccess("OPEN_SEARCH")
    check(breaker.failureCount("OPEN_SEARCH") == 0)
    check(breaker.recordFailure("OPEN_SEARCH") == CircuitDecision.RETRY)

    // Independent stages must not poison each other.
    check(breaker.failureCount("VERIFY_CHAT") == 0)
    println("StageCircuitBreakerSmoke: PASS")
}
