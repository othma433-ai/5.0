package com.waalothmany.linkbot.capability

enum class RuntimeCapabilityMode { STANDARD, SHIZUKU_ENHANCED, ROOT_ENHANCED }

object RuntimeCapabilityResolver {
    fun resolve(
        shizukuUsable: Boolean,
        rootUsable: Boolean,
        shizukuInstalled: Boolean = shizukuUsable,
        rootDetected: Boolean = rootUsable,
    ): RuntimeCapabilityMode = when {
        shizukuUsable -> RuntimeCapabilityMode.SHIZUKU_ENHANCED
        rootUsable -> RuntimeCapabilityMode.ROOT_ENHANCED
        else -> RuntimeCapabilityMode.STANDARD
    }
}
