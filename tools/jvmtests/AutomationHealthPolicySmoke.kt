package com.waalothmany.linkbot.automation

fun main() {
    val policy = AutomationHealthPolicy()
    val initial = policy.snapshot()
    check(initial.score == 100)
    check(initial.recommendedMode == PerformanceMode.BALANCED)

    repeat(10) { policy.recordSuccess() }
    val fast = policy.snapshot()
    check(fast.recommendedMode == PerformanceMode.FAST) { fast.toString() }

    policy.recordAmbiguity()
    policy.recordTransientFailure()
    policy.recordTransientFailure()
    val safe = policy.snapshot()
    check(safe.score < fast.score)
    check(safe.recommendedMode == PerformanceMode.SAFE) { safe.toString() }

    repeat(20) { policy.recordSuccess() }
    val recovered = policy.snapshot()
    check(recovered.score > safe.score)
    check(recovered.recommendedMode != PerformanceMode.SAFE)
    println("AutomationHealthPolicySmoke: PASS")
}
