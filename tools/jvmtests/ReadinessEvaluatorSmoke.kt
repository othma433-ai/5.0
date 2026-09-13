package com.waalothmany.linkbot.capability

fun main() {
    val ready = ReadinessEvaluator.evaluate(
        CapabilityFlags(
            accessibilityEnabled = true,
            accessibilityConnected = true,
            overlay = false,
            notifications = true,
            shizukuInstalled = false,
            rootDetected = false,
        ),
        whatsappInstances = 1,
    )
    check(ready.coreReady) { ready.toString() }
    check(ready.blockers.isEmpty()) { ready.toString() }
    check("Overlay optional" in ready.notes)
    check(ready.executionMode == "STANDARD") { ready.toString() }


    val notificationsOptional = ReadinessEvaluator.evaluate(
        CapabilityFlags(
            accessibilityEnabled = true,
            accessibilityConnected = true,
            overlay = false,
            notifications = false,
            shizukuInstalled = false,
            rootDetected = false,
        ),
        whatsappInstances = 1,
    )
    check(notificationsOptional.coreReady) { notificationsOptional.toString() }
    check("Notifications recommended" in notificationsOptional.notes)

    val enabledButDisconnected = ReadinessEvaluator.evaluate(
        CapabilityFlags(
            accessibilityEnabled = true,
            accessibilityConnected = false,
            overlay = true,
            notifications = true,
            shizukuInstalled = true,
            rootDetected = false,
        ),
        whatsappInstances = 1,
    )
    check(!enabledButDisconnected.coreReady)
    check(enabledButDisconnected.blockers.contains("Accessibility service not connected"))
    check(enabledButDisconnected.notes.contains("Shizuku app detected; privileged adapter not active"))
    check(enabledButDisconnected.executionMode == "STANDARD")

    val disabled = ReadinessEvaluator.evaluate(
        CapabilityFlags(
            accessibilityEnabled = false,
            accessibilityConnected = false,
            overlay = true,
            notifications = false,
            shizukuInstalled = false,
            rootDetected = true,
        ),
        whatsappInstances = 0,
    )
    check(!disabled.coreReady)
    check(disabled.blockers.contains("Accessibility disabled"))
    check("Notifications recommended" in disabled.notes)
    check(disabled.blockers.contains("No WhatsApp instance detected"))
    check(disabled.notes.contains("Root binary detected; privileged adapter not active"))
    check(disabled.executionMode == "STANDARD")

    println("ReadinessEvaluatorSmoke: PASS")
}
