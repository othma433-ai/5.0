package com.waalothmany.linkbot.capability

data class CapabilityFlags(
    val accessibility: Boolean,
    val overlay: Boolean,
    val notifications: Boolean,
    val shizukuAvailable: Boolean,
    val rootDetected: Boolean,
)

data class ReadinessReport(
    val coreReady: Boolean,
    val blockers: List<String>,
    val notes: List<String>,
    val executionMode: String,
)

object ReadinessEvaluator {
    fun evaluate(
        flags: CapabilityFlags,
        whatsappInstances: Int,
        accessibilityServiceConnected: Boolean,
    ): ReadinessReport {
        val blockers = buildList {
            if (!flags.accessibility) add("Accessibility disabled")
            if (!flags.notifications) add("Notifications disabled")
            if (whatsappInstances <= 0) add("No WhatsApp instance detected")
            if (flags.accessibility && !accessibilityServiceConnected) add("Accessibility service not connected")
        }
        val notes = buildList {
            if (!flags.overlay) add("Overlay optional")
            if (flags.shizukuAvailable) add("Shizuku detected; optional adapter available when configured")
            if (flags.rootDetected) add("Root binary detected; privileged mode remains opt-in")
        }
        val mode = when {
            flags.rootDetected -> "STANDARD + ROOT OPTIONAL"
            flags.shizukuAvailable -> "STANDARD + SHIZUKU OPTIONAL"
            else -> "STANDARD"
        }
        return ReadinessReport(
            coreReady = blockers.isEmpty(),
            blockers = blockers,
            notes = notes,
            executionMode = mode,
        )
    }
}
