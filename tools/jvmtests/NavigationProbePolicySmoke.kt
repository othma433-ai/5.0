package com.waalothmany.linkbot.automation

fun main() {
    check(NavigationProbePolicy.delayMs(PerformanceMode.FAST, 0) < NavigationProbePolicy.delayMs(PerformanceMode.SAFE, 0))
    check(NavigationProbePolicy.delayMs(PerformanceMode.BALANCED, 999) <= 420L)
    check(NavigationProbePolicy.maxAttempts(PerformanceMode.FAST) < NavigationProbePolicy.maxAttempts(PerformanceMode.SAFE))
    check(NavigationProbePolicy.totalBudgetMs(PerformanceMode.BALANCED) > 0)
    println("NavigationProbePolicySmoke: PASS")
}
