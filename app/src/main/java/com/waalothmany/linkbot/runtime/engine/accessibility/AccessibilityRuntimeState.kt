package com.waalothmany.linkbot.runtime.engine.accessibility

enum class AccessibilityRuntimeState {
    DISABLED,
    ENABLED_WAITING_BIND,
    BINDER_CONNECTED,
    EVENT_CHANNEL_ALIVE,
    WINDOW_ACCESS_READY,
    INTERRUPTED,
    DISCONNECTED,
    STALE,
}

data class AccessibilityRuntimeSnapshot(
    val state: AccessibilityRuntimeState = AccessibilityRuntimeState.DISABLED,
    val settingsEnabled: Boolean = false,
    val binderConnected: Boolean = false,
    val enabledDetectedAtMs: Long? = null,
    val serviceConnectedAtMs: Long? = null,
    val lastAccessibilityEventAtMs: Long? = null,
    val lastEventPackageName: String? = null,
    val lastWindowRootAtMs: Long? = null,
    val lastSuccessfulActionAtMs: Long? = null,
    val generation: Long = 0L,
) {
    val connected: Boolean
        get() = binderConnected && state !in setOf(
            AccessibilityRuntimeState.DISABLED,
            AccessibilityRuntimeState.DISCONNECTED,
            AccessibilityRuntimeState.INTERRUPTED,
        )

    val windowReady: Boolean
        get() = state == AccessibilityRuntimeState.WINDOW_ACCESS_READY
}

sealed interface AccessibilityRuntimeSignal {
    data class SettingsEnabled(val nowMs: Long) : AccessibilityRuntimeSignal
    data class SettingsDisabled(val nowMs: Long) : AccessibilityRuntimeSignal
    data class BinderConnected(val nowMs: Long) : AccessibilityRuntimeSignal
    data class EventObserved(val nowMs: Long, val packageName: String?) : AccessibilityRuntimeSignal
    data class WindowRootObserved(val nowMs: Long) : AccessibilityRuntimeSignal
    data class ActionSucceeded(val nowMs: Long) : AccessibilityRuntimeSignal
    data class Interrupted(val nowMs: Long) : AccessibilityRuntimeSignal
    data class Disconnected(val nowMs: Long) : AccessibilityRuntimeSignal
    data class MarkStale(val nowMs: Long) : AccessibilityRuntimeSignal
}

object AccessibilityRuntimeReducer {
    fun reduce(
        current: AccessibilityRuntimeSnapshot,
        signal: AccessibilityRuntimeSignal,
    ): AccessibilityRuntimeSnapshot = when (signal) {
        is AccessibilityRuntimeSignal.SettingsEnabled -> {
            if (current.settingsEnabled) current
            else current.copy(
                state = if (current.binderConnected) AccessibilityRuntimeState.BINDER_CONNECTED
                else AccessibilityRuntimeState.ENABLED_WAITING_BIND,
                settingsEnabled = true,
                enabledDetectedAtMs = signal.nowMs,
            )
        }
        is AccessibilityRuntimeSignal.SettingsDisabled -> current.copy(
            state = AccessibilityRuntimeState.DISABLED,
            settingsEnabled = false,
            binderConnected = false,
            enabledDetectedAtMs = null,
            serviceConnectedAtMs = null,
            lastAccessibilityEventAtMs = null,
            lastEventPackageName = null,
            lastWindowRootAtMs = null,
            generation = current.generation + 1,
        )
        is AccessibilityRuntimeSignal.BinderConnected -> current.copy(
            state = AccessibilityRuntimeState.BINDER_CONNECTED,
            settingsEnabled = true,
            binderConnected = true,
            enabledDetectedAtMs = current.enabledDetectedAtMs ?: signal.nowMs,
            serviceConnectedAtMs = signal.nowMs,
            generation = current.generation + 1,
        )
        is AccessibilityRuntimeSignal.EventObserved -> current.copy(
            state = AccessibilityRuntimeState.EVENT_CHANNEL_ALIVE,
            binderConnected = true,
            lastAccessibilityEventAtMs = signal.nowMs,
            lastEventPackageName = signal.packageName ?: current.lastEventPackageName,
        )
        is AccessibilityRuntimeSignal.WindowRootObserved -> current.copy(
            state = AccessibilityRuntimeState.WINDOW_ACCESS_READY,
            binderConnected = true,
            lastWindowRootAtMs = signal.nowMs,
        )
        is AccessibilityRuntimeSignal.ActionSucceeded -> current.copy(
            lastSuccessfulActionAtMs = signal.nowMs,
        )
        is AccessibilityRuntimeSignal.Interrupted -> current.copy(
            state = AccessibilityRuntimeState.INTERRUPTED,
            lastAccessibilityEventAtMs = signal.nowMs,
        )
        is AccessibilityRuntimeSignal.Disconnected -> current.copy(
            state = AccessibilityRuntimeState.DISCONNECTED,
            binderConnected = false,
            lastAccessibilityEventAtMs = signal.nowMs,
            generation = current.generation + 1,
        )
        is AccessibilityRuntimeSignal.MarkStale -> current.copy(
            state = AccessibilityRuntimeState.STALE,
            lastAccessibilityEventAtMs = current.lastAccessibilityEventAtMs ?: signal.nowMs,
        )
    }
}
