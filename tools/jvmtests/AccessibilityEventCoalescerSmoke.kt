package com.waalothmany.linkbot.automation

fun main() {
    val c = AccessibilityEventCoalescer(contentWindowMs = 120)
    check(c.shouldProcess(AccessibilityEventClass.WINDOW_STATE, nowMs = 1_000, operationGeneration = 1, windowId = 7))
    check(c.shouldProcess(AccessibilityEventClass.WINDOW_STATE, nowMs = 1_001, operationGeneration = 1, windowId = 7)) { "window transitions must never be coalesced" }

    check(c.shouldProcess(AccessibilityEventClass.WINDOW_CONTENT_CHANGED, nowMs = 1_010, operationGeneration = 1, windowId = 7))
    check(!c.shouldProcess(AccessibilityEventClass.WINDOW_CONTENT_CHANGED, nowMs = 1_050, operationGeneration = 1, windowId = 7))
    check(c.shouldProcess(AccessibilityEventClass.WINDOW_CONTENT_CHANGED, nowMs = 1_131, operationGeneration = 1, windowId = 7))

    check(c.shouldProcess(AccessibilityEventClass.WINDOW_CONTENT_CHANGED, nowMs = 1_140, operationGeneration = 2, windowId = 7)) { "new operation generation must bypass old coalescing state" }
    check(c.shouldProcess(AccessibilityEventClass.WINDOW_CONTENT_CHANGED, nowMs = 1_150, operationGeneration = 2, windowId = 8)) { "new window must bypass old coalescing state" }
    check(c.shouldProcess(AccessibilityEventClass.OTHER, nowMs = 1_151, operationGeneration = 2, windowId = 8))

    println("AccessibilityEventCoalescerSmoke: PASS")
}
