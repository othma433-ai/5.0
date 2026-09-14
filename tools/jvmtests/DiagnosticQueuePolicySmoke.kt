package com.waalothmany.linkbot.runtime

fun main() {
    val metrics = DiagnosticQueueMetrics()
    metrics.recordOffer(true)
    metrics.recordOffer(false)
    metrics.recordOffer(false)
    check(metrics.acceptedCount() == 1L)
    check(metrics.droppedCount() == 2L)
    check(DiagnosticQueuePolicy.shouldRotate(fileBytes = 513 * 1024L, maxBytes = 512 * 1024L))
    check(!DiagnosticQueuePolicy.shouldRotate(fileBytes = 511 * 1024L, maxBytes = 512 * 1024L))
    check(DiagnosticQueuePolicy.validCapacity(1))
    check(!DiagnosticQueuePolicy.validCapacity(0))
    println("DiagnosticQueuePolicySmoke: PASS")
}
