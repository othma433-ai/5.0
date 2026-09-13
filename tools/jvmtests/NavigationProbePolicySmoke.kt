package com.waalothmany.linkbot.automation

fun main() {
    val fastFirst = NavigationProbePolicy.delayMs(PerformanceMode.FAST, 0)
    val balancedFirst = NavigationProbePolicy.delayMs(PerformanceMode.BALANCED, 0)
    val safeFirst = NavigationProbePolicy.delayMs(PerformanceMode.SAFE, 0)
    check(fastFirst < balancedFirst)
    check(balancedFirst < safeFirst)

    PerformanceMode.entries.forEach { mode ->
        val delays = (0 until NavigationProbePolicy.maxAttempts(mode)).map { attempt ->
            NavigationProbePolicy.delayMs(mode, attempt)
        }
        check(delays.isNotEmpty())
        check(delays.zipWithNext().all { (a, b) -> b >= a })
        check(delays.all { it in 75L..900L })
        check(NavigationProbePolicy.totalBudgetMs(mode) < PerformanceProfiles.forMode(mode).stageTimeoutMs)
    }

    check(NavigationProbePolicy.maxAttempts(PerformanceMode.FAST) < NavigationProbePolicy.maxAttempts(PerformanceMode.SAFE))
    println("NavigationProbePolicySmoke: PASS")
}
