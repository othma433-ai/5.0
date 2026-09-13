package com.waalothmany.linkbot.automation

fun main() {
    check(FailureRecoveryPolicy.decide("STAGE_TIMEOUT: search", attempts = 1) == FailureDisposition.RETRY)
    check(FailureRecoveryPolicy.decide("PERSISTENCE_ERROR: disk", attempts = 1) == FailureDisposition.RETRY)
    check(FailureRecoveryPolicy.decide("AMBIGUOUS_GROUP: duplicate", attempts = 0) == FailureDisposition.MANUAL_REVIEW)
    check(FailureRecoveryPolicy.decide("GROUP_MISSING: registry", attempts = 0) == FailureDisposition.MANUAL_REVIEW)
    check(FailureRecoveryPolicy.decide("STAGE_TIMEOUT: repeated", attempts = 3) == FailureDisposition.EXHAUSTED)
    println("FailureRecoveryPolicySmoke: PASS")
}
