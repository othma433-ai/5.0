package com.waalothmany.linkbot.runtime.engine.accessibility

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-local source of truth for the AccessibilityService lifecycle.
 *
 * Android's secure setting only proves that the user enabled the service. This
 * supervisor separately tracks binder connection, event delivery, and access
 * to an active-window root so automation can distinguish "enabled" from
 * "actually usable".
 */
object AccessibilityRuntimeSupervisor {
    private val mutable = MutableStateFlow(AccessibilityRuntimeSnapshot())
    val state: StateFlow<AccessibilityRuntimeSnapshot> = mutable.asStateFlow()

    fun updateSettingsEnabled(enabled: Boolean, nowMs: Long = System.currentTimeMillis()) {
        val current = mutable.value
        if (current.settingsEnabled == enabled) return
        apply(
            if (enabled) AccessibilityRuntimeSignal.SettingsEnabled(nowMs)
            else AccessibilityRuntimeSignal.SettingsDisabled(nowMs)
        )
    }

    fun markBinderConnected(nowMs: Long = System.currentTimeMillis()) {
        apply(AccessibilityRuntimeSignal.BinderConnected(nowMs))
    }

    fun markEvent(packageName: String? = null, nowMs: Long = System.currentTimeMillis()) {
        apply(AccessibilityRuntimeSignal.EventObserved(nowMs, packageName))
    }

    fun markWindowReady(nowMs: Long = System.currentTimeMillis()) {
        apply(AccessibilityRuntimeSignal.WindowRootObserved(nowMs))
    }

    fun markActionSucceeded(nowMs: Long = System.currentTimeMillis()) {
        apply(AccessibilityRuntimeSignal.ActionSucceeded(nowMs))
    }

    fun markInterrupted(nowMs: Long = System.currentTimeMillis()) {
        apply(AccessibilityRuntimeSignal.Interrupted(nowMs))
    }

    fun markDisconnected(nowMs: Long = System.currentTimeMillis()) {
        apply(AccessibilityRuntimeSignal.Disconnected(nowMs))
    }

    fun markStale(nowMs: Long = System.currentTimeMillis()) {
        apply(AccessibilityRuntimeSignal.MarkStale(nowMs))
    }

    private fun apply(signal: AccessibilityRuntimeSignal) {
        mutable.value = AccessibilityRuntimeReducer.reduce(mutable.value, signal)
    }
}
