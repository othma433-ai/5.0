package com.waalothmany.linkbot.capability

data class CapabilityFlags(
    val accessibilityEnabled: Boolean,
    val accessibilityConnected: Boolean,
    val overlay: Boolean,
    val notifications: Boolean,
    val foregroundServiceReady: Boolean = true,
    val storageAccessFrameworkReady: Boolean = true,
    val shizukuInstalled: Boolean,
    val shizukuUsable: Boolean = false,
    val rootDetected: Boolean,
    val rootUsable: Boolean = false,
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
            if (!flags.foregroundServiceReady) add("Foreground service unavailable")
            if (whatsappInstances <= 0) add("No WhatsApp instance detected")
        }

        val notes = buildList {
            if (!flags.notifications) add("Notifications recommended")
            if (!flags.overlay) add("Overlay optional")
            if (!flags.storageAccessFrameworkReady) add("Storage Access Framework unavailable; import/export browsing limited")
            if (flags.shizukuInstalled && !flags.shizukuUsable) add("Shizuku detected; permission/binder not ready")
            if (flags.rootDetected && !flags.rootUsable) add("Root binary detected; usable root permission not proven")
        }
        val mode = RuntimeCapabilityResolver.resolve(
            shizukuUsable = flags.shizukuUsable,
            rootUsable = flags.rootUsable,
            shizukuInstalled = flags.shizukuInstalled,
            rootDetected = flags.rootDetected,
        )
        return ReadinessReport(
            coreReady = blockers.isEmpty(),
            blockers = blockers,
            notes = notes,
            executionMode = mode.name,
        )
    }
}
