package com.waalothmany.linkbot.automation

fun main() {
    val policy = AdaptiveTimingPolicy(PerformanceProfiles.forMode(PerformanceMode.BALANCED))
    val initial = policy.nextProbeDelay(scrollAccepted = true)
    check(initial in 180L..520L) { "unexpected initial delay=$initial" }

    repeat(4) { policy.observeUiLatency(120) }
    val fast = policy.nextProbeDelay(scrollAccepted = true)
    check(fast < initial) { "fast UI should reduce probe delay: $fast >= $initial" }
    check(fast >= 90) { "probe delay below safety floor: $fast" }

    repeat(4) { policy.observeUiLatency(900) }
    val slow = policy.nextProbeDelay(scrollAccepted = true)
    check(slow > fast) { "slow UI should increase probe delay: $slow <= $fast" }
    check(slow <= 1_600) { "probe delay exceeded cap: $slow" }

    val rejected = policy.nextProbeDelay(scrollAccepted = false)
    check(rejected <= slow) { "rejected-scroll probe should not wait longer than normal probe" }
    println("AdaptiveTimingSmoke: PASS")
}
