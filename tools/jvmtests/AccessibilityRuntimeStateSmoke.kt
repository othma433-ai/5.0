package com.waalothmany.linkbot.runtime.engine.accessibility

fun main() {
    var s = AccessibilityRuntimeSnapshot()
    s = AccessibilityRuntimeReducer.reduce(s, AccessibilityRuntimeSignal.SettingsEnabled(100L))
    check(s.state == AccessibilityRuntimeState.ENABLED_WAITING_BIND)
    s = AccessibilityRuntimeReducer.reduce(s, AccessibilityRuntimeSignal.BinderConnected(200L))
    check(s.binderConnected)
    check(s.state == AccessibilityRuntimeState.BINDER_CONNECTED)
    s = AccessibilityRuntimeReducer.reduce(s, AccessibilityRuntimeSignal.EventObserved(250L, "com.whatsapp"))
    check(s.state == AccessibilityRuntimeState.EVENT_CHANNEL_ALIVE)
    check(s.lastEventPackageName == "com.whatsapp")
    s = AccessibilityRuntimeReducer.reduce(s, AccessibilityRuntimeSignal.WindowRootObserved(300L))
    check(s.state == AccessibilityRuntimeState.WINDOW_ACCESS_READY)
    s = AccessibilityRuntimeReducer.reduce(s, AccessibilityRuntimeSignal.Interrupted(350L))
    check(s.state == AccessibilityRuntimeState.INTERRUPTED)
    s = AccessibilityRuntimeReducer.reduce(s, AccessibilityRuntimeSignal.Disconnected(400L))
    check(s.state == AccessibilityRuntimeState.DISCONNECTED)
    check(!s.binderConnected)
    println("AccessibilityRuntimeStateSmoke: PASS")
}
