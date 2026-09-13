package com.waalothmany.linkbot.automation

fun main() {
    check(FilterVerificationPolicy.decide(FilterEvidence.ACTIVE, PerformanceMode.SAFE, 1) == FilterDecision.PROCEED)

    // Accuracy invariant: an unverified Groups filter must never fall through into scanning.
    check(FilterVerificationPolicy.decide(FilterEvidence.UNKNOWN, PerformanceMode.FAST, 1) == FilterDecision.RETRY)
    check(FilterVerificationPolicy.decide(FilterEvidence.UNKNOWN, PerformanceMode.FAST, 2) == FilterDecision.FAIL)
    check(FilterVerificationPolicy.decide(FilterEvidence.UNKNOWN, PerformanceMode.BALANCED, 2) == FilterDecision.RETRY)
    check(FilterVerificationPolicy.decide(FilterEvidence.UNKNOWN, PerformanceMode.BALANCED, 3) == FilterDecision.FAIL)
    check(FilterVerificationPolicy.decide(FilterEvidence.UNKNOWN, PerformanceMode.SAFE, 3) == FilterDecision.RETRY)
    check(FilterVerificationPolicy.decide(FilterEvidence.UNKNOWN, PerformanceMode.SAFE, 4) == FilterDecision.FAIL)

    check(FilterVerificationPolicy.decide(FilterEvidence.INACTIVE, PerformanceMode.BALANCED, 2) == FilterDecision.RETRY)
    check(FilterVerificationPolicy.decide(FilterEvidence.INACTIVE, PerformanceMode.BALANCED, 3) == FilterDecision.FAIL)
    println("FilterVerificationPolicySmoke: PASS")
}
