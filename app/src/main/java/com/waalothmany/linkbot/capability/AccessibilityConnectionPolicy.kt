package com.waalothmany.linkbot.capability

enum class AccessibilityRuntimeState {
    DISABLED,
    ENABLED_NOT_CONNECTED,
    CONNECTED,
    STALE,
    DISCONNECTED,
}

data class AccessibilityConnectionSnapshot(
    val state: AccessibilityRuntimeState = AccessibilityRuntimeState.DISCONNECTED,
    val connectedAtMs: Long? = null,
    val lastSignalAtMs: Long? = null,
    val generation: Long = 0L,
) {
    /** Raw binder lifecycle signal. Public readiness must use deriveState(). */
    val binderConnected: Boolean get() = state == AccessibilityRuntimeState.CONNECTED
}

object AccessibilityConnectionPolicy {
    const val DEFAULT_STALE_AFTER_MS = 30_000L

    fun deriveState(
        enabledInSettings: Boolean,
        snapshot: AccessibilityConnectionSnapshot,
        nowMs: Long = System.currentTimeMillis(),
        staleAfterMs: Long = DEFAULT_STALE_AFTER_MS,
    ): AccessibilityRuntimeState {
        require(staleAfterMs > 0L)
        if (!enabledInSettings) return AccessibilityRuntimeState.DISABLED

        if (snapshot.state == AccessibilityRuntimeState.CONNECTED) {
            val lastSignal = snapshot.lastSignalAtMs ?: snapshot.connectedAtMs
            if (lastSignal == null) return AccessibilityRuntimeState.ENABLED_NOT_CONNECTED
            if (nowMs - lastSignal > staleAfterMs) return AccessibilityRuntimeState.STALE
            return AccessibilityRuntimeState.CONNECTED
        }

        return if (snapshot.generation == 0L) {
            AccessibilityRuntimeState.ENABLED_NOT_CONNECTED
        } else {
            AccessibilityRuntimeState.DISCONNECTED
        }
    }

    fun isOperational(state: AccessibilityRuntimeState): Boolean =
        state == AccessibilityRuntimeState.CONNECTED

    fun isOperational(
        enabledInSettings: Boolean,
        serviceConnected: Boolean,
    ): Boolean = enabledInSettings && serviceConnected
}
