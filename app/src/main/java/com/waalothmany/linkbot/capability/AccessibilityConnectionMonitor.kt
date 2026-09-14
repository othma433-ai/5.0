package com.waalothmany.linkbot.capability

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-local source of truth for the AccessibilityService binder lifecycle.
 * Android's enabled-services setting is intentionally kept separate from this
 * live binder/heartbeat signal.
 */
object AccessibilityConnectionMonitor {
    private val mutable = MutableStateFlow(AccessibilityConnectionSnapshot())
    val state: StateFlow<AccessibilityConnectionSnapshot> = mutable.asStateFlow()

    fun markConnected(nowMs: Long = System.currentTimeMillis()) {
        val previous = mutable.value
        mutable.value = AccessibilityConnectionSnapshot(
            state = AccessibilityRuntimeState.CONNECTED,
            connectedAtMs = nowMs,
            lastSignalAtMs = nowMs,
            generation = previous.generation + 1,
        )
    }

    fun markHeartbeat(nowMs: Long = System.currentTimeMillis()) {
        val previous = mutable.value
        if (previous.state != AccessibilityRuntimeState.CONNECTED) return
        mutable.value = previous.copy(lastSignalAtMs = nowMs)
    }

    fun markActivity(nowMs: Long = System.currentTimeMillis()) = markHeartbeat(nowMs)

    fun markInterrupted(nowMs: Long = System.currentTimeMillis()) {
        val previous = mutable.value
        mutable.value = previous.copy(
            state = AccessibilityRuntimeState.DISCONNECTED,
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
