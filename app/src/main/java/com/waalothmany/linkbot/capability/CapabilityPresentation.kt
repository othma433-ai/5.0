package com.waalothmany.linkbot.capability

import com.waalothmany.linkbot.runtime.engine.ProbeState
import com.waalothmany.linkbot.runtime.engine.shizuku.ShizukuRuntimeSnapshot
import com.waalothmany.linkbot.runtime.engine.shizuku.ShizukuRuntimeState

data class EnginePresentation(
    val text: String,
    val ready: Boolean,
    val action: String? = null,
)

object CapabilityPresentation {
    fun shizuku(snapshot: ShizukuRuntimeSnapshot): EnginePresentation = when (snapshot.state) {
        ShizukuRuntimeState.NOT_INSTALLED -> EnginePresentation("Not installed", false, "OPEN_SHIZUKU")
        ShizukuRuntimeState.BINDER_UNAVAILABLE -> EnginePresentation("Binder unavailable", false, "OPEN_SHIZUKU")
        ShizukuRuntimeState.BINDER_ALIVE_NO_PERMISSION -> EnginePresentation("Permission required", false, "GRANT_PERMISSION")
        ShizukuRuntimeState.PERMISSION_REQUESTING -> EnginePresentation("Permission requesting", false)
        ShizukuRuntimeState.PROBING -> EnginePresentation("Probing", false)
        ShizukuRuntimeState.READY -> EnginePresentation("Ready", true)
        ShizukuRuntimeState.BINDER_DEAD -> EnginePresentation("Binder disconnected", false, "OPEN_SHIZUKU")
        ShizukuRuntimeState.UNSUPPORTED -> EnginePresentation("Unsupported", false)
        ShizukuRuntimeState.ERROR -> EnginePresentation("Error", false, "RETRY_PROBE")
    }

    fun root(enabled: Boolean, state: ProbeState): EnginePresentation {
        if (!enabled) return EnginePresentation("Disabled", false, "ENABLE_ROOT")
        return when (state) {
            ProbeState.READY -> EnginePresentation("Ready", true)
            ProbeState.DEGRADED -> EnginePresentation("Degraded", true, "RETRY_PROBE")
            ProbeState.PERMISSION_REQUIRED -> EnginePresentation("Permission required", false, "RETRY_PROBE")
            ProbeState.UNAVAILABLE -> EnginePresentation("Unavailable", false, "RETRY_PROBE")
            ProbeState.ERROR -> EnginePresentation("Error", false, "RETRY_PROBE")
        }
    }

    fun executionMode(shizukuReady: Boolean, rootReady: Boolean): String = when {
        shizukuReady && rootReady -> "ADAPTIVE • SHIZUKU + ROOT + ACCESSIBILITY"
        shizukuReady -> "ADAPTIVE • SHIZUKU + ACCESSIBILITY"
        rootReady -> "ADAPTIVE • ROOT + ACCESSIBILITY"
        else -> "ADAPTIVE • STANDARD + ACCESSIBILITY"
    }
}
