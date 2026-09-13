package com.waalothmany.linkbot.automation

fun main() {
    val guard = EndOfListGuard(stableRequired = 3, rejectedScrollRequired = 2)
    check(!guard.observe(ProgressSample("A", newItems = 8, scrollAccepted = true)))
    check(!guard.observe(ProgressSample("B", newItems = 4, scrollAccepted = true)))
    check(!guard.observe(ProgressSample("B", newItems = 0, scrollAccepted = false)))
    check(!guard.observe(ProgressSample("B", newItems = 0, scrollAccepted = false)))
    check(guard.observe(ProgressSample("B", newItems = 0, scrollAccepted = false)))

    val transient = EndOfListGuard(stableRequired = 3, rejectedScrollRequired = 2)
    check(!transient.observe(ProgressSample("X", 0, false)))
    check(!transient.observe(ProgressSample("Y", 3, true)))
    check(!transient.observe(ProgressSample("Y", 0, false)))
    println("AutomationPolicySmoke: PASS")
}
