package com.waalothmany.linkbot.capability

enum class AccessibilityRuntimeState {
    DISCONNECTED,
    CONNECTED,
    INTERRUPTED,
}

data class AccessibilityConnectionSnapshot(
    val state: AccessibilityRuntimeState = AccessibilityRuntimeState.DISCONNECTED,
    val connectedAtMs: Long? = null,
    val lastSignalAtMs: Long? = null,
    val generation: Long = 0L,
) {
    val connected: Boolean get() = state == AccessibilityRuntimeState.CONNECTED
}

object AccessibilityConnectionPolicy {
    fun isOperational(
        enabledInSettings: Boolean,
        serviceConnected: Boolean,
    ): Boolean = enabledInSettings && serviceConnected
}
