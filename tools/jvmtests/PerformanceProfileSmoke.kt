package com.waalothmany.linkbot.automation

fun main() {
    check(PerformanceProfiles.forMode(PerformanceMode.FAST).scrollProbeMs < PerformanceProfiles.forMode(PerformanceMode.BALANCED).scrollProbeMs)
    check(PerformanceProfiles.forMode(PerformanceMode.SAFE).stableEndCycles > PerformanceProfiles.forMode(PerformanceMode.FAST).stableEndCycles)
    check(PerformanceProfiles.parse("unknown") == PerformanceMode.BALANCED)
    println("PerformanceProfileSmoke: PASS")
}
