package com.waalothmany.linkbot.capability

fun main() {
    val ready = ReadinessEvaluator.evaluate(
        CapabilityFlags(accessibility = true, overlay = false, notifications = true, shizukuAvailable = false, rootDetected = false),
        whatsappInstances = 1,
        accessibilityServiceConnected = true,
    )
    check(ready.coreReady) { ready.toString() }
    check(ready.blockers.isEmpty()) { ready.toString() }
    check("Overlay optional" in ready.notes)

    val blocked = ReadinessEvaluator.evaluate(
        CapabilityFlags(accessibility = false, overlay = true, notifications = false, shizukuAvailable = true, rootDetected = true),
        whatsappInstances = 0,
        accessibilityServiceConnected = false,
    )
    check(!blocked.coreReady)
    check(blocked.blockers.contains("Accessibility disabled"))
    check(blocked.blockers.contains("Notifications disabled"))
    check(blocked.blockers.contains("No WhatsApp instance detected"))
    println("ReadinessEvaluatorSmoke: PASS")
}
