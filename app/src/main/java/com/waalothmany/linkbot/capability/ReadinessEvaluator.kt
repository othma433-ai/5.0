package com.waalothmany.linkbot.capability

data class CapabilityFlags(
    val accessibilityEnabled: Boolean,
    val accessibilityConnected: Boolean,
    val overlay: Boolean,
    val notifications: Boolean,
    val shizukuInstalled: Boolean,
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
    ): ReadinessReport {
        val blockers = buildList {
            if (!flags.accessibilityEnabled) {
                add("Accessibility disabled")
            } else if (!flags.accessibilityConnected) {
                add("Accessibility service not connected")
            }
            if (whatsappInstances <= 0) add("No WhatsApp instance detected")
        }

        val notes = buildList {
            if (!flags.notifications) add("Notifications recommended")
            if (!flags.overlay) add("Overlay optional")
            if (flags.shizukuInstalled) {
                add("Shizuku detected; Binder/permission readiness is verified at runtime")
            }
            if (flags.rootDetected) {
                add("Root detected; readiness requires an execution probe")
            }
        }

        return ReadinessReport(
            coreReady = blockers.isEmpty(),
            blockers = blockers,
            notes = notes,
            executionMode = "ADAPTIVE_MULTI_ENGINE",
        )
    }
}
