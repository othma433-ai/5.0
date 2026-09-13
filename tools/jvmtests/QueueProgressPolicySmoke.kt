package com.waalothmany.linkbot.automation

fun main() {
    val summary = QueueProgressPolicy.summarize(listOf("COMPLETED", "SKIPPED", "FAILED", "WAITING", "SCANNING", "PAUSED"))
    check(summary.total == 6)
    check(summary.completed == 2)
    check(summary.failed == 1)
    check(summary.remaining == 3)
    check(summary.inFlight == 2)
    println("QueueProgressPolicySmoke: PASS")
}
