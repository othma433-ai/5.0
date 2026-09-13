package com.waalothmany.linkbot.automation

fun main() {
    check(SyncCoveragePolicy.decide(previousPresent = 0, discovered = 0) == SyncCoverageDecision.ACCEPT)
    check(SyncCoveragePolicy.decide(previousPresent = 1000, discovered = 980) == SyncCoverageDecision.ACCEPT)
    check(SyncCoveragePolicy.decide(previousPresent = 1000, discovered = 200) == SyncCoverageDecision.SAFETY_STOP)
    check(SyncCoveragePolicy.decide(previousPresent = 50, discovered = 0) == SyncCoverageDecision.SAFETY_STOP)
    // Small registries can genuinely change dramatically; avoid over-blocking them.
    check(SyncCoveragePolicy.decide(previousPresent = 5, discovered = 2) == SyncCoverageDecision.ACCEPT)
    println("SyncCoveragePolicySmoke: PASS")
}
