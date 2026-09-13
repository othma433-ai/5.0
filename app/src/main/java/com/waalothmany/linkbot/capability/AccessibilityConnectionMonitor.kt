package com.waalothmany.linkbot.capability

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-local source of truth for the AccessibilityService binder lifecycle.
 *
 * `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` only tells us that the user
 * enabled the service. It does not prove Android has instantiated and connected
 * our service process. Automation must require this monitor to be CONNECTED.
 */
object AccessibilityConnectionMonitor {
    private val mutable = MutableStateFlow(AccessibilityConnectionSnapshot())
    val state: StateFlow<AccessibilityConnectionSnapshot> = mutable.asStateFlow()

    fun markConnected(nowMs: Long = System.currentTimeMillis()) {
        val previous = mutable.value
        mutable.value = AccessibilityConnectionSnapshot(
            state = AccessibilityRuntimeState.CONNECTED,
            connectedAtMs = previous.connectedAtMs ?: nowMs,
            lastSignalAtMs = nowMs,
            generation = previous.generation + 1,
        )
    }

    fun markActivity(nowMs: Long = System.currentTimeMillis()) {
        val previous = mutable.value
        mutable.value = previous.copy(
            state = AccessibilityRuntimeState.CONNECTED,
            connectedAtMs = previous.connectedAtMs ?: nowMs,
            lastSignalAtMs = nowMs,
        )
    }

    fun markInterrupted(nowMs: Long = System.currentTimeMillis()) {
        val previous = mutable.value
        mutable.value = previous.copy(
            state = AccessibilityRuntimeState.INTERRUPTED,
            lastSignalAtMs = nowMs,
        )
    }

    fun markDisconnected(nowMs: Long = System.currentTimeMillis()) {
        val previous = mutable.value
        mutable.value = previous.copy(
            state = AccessibilityRuntimeState.DISCONNECTED,
            lastSignalAtMs = nowMs,
        )
    }
}
