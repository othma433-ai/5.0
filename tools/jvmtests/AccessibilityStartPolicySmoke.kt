package com.waalothmany.linkbot.capability

import com.waalothmany.linkbot.runtime.engine.accessibility.AccessibilityRuntimeSnapshot
import com.waalothmany.linkbot.runtime.engine.accessibility.AccessibilityRuntimeState

fun main() {
    check(
        AccessibilityStartPolicy.evaluate(
            AccessibilityRuntimeSnapshot(state = AccessibilityRuntimeState.DISABLED, settingsEnabled = false),
            nowMs = 10_000L,
        ) == AccessibilityStartDisposition.REQUIRE_USER_ACTION
    )

    val waiting = AccessibilityRuntimeSnapshot(
        state = AccessibilityRuntimeState.ENABLED_WAITING_BIND,
        settingsEnabled = true,
        enabledDetectedAtMs = 9_000L,
    )
    check(AccessibilityStartPolicy.evaluate(waiting, 10_000L) == AccessibilityStartDisposition.WAIT_FOR_BIND)
    check(AccessibilityStartPolicy.evaluate(waiting, 20_000L) == AccessibilityStartDisposition.REQUIRE_USER_ACTION)

    val ready = AccessibilityRuntimeSnapshot(
        state = AccessibilityRuntimeState.WINDOW_ACCESS_READY,
        settingsEnabled = true,
        binderConnected = true,
        enabledDetectedAtMs = 1L,
        serviceConnectedAtMs = 2L,
        lastAccessibilityEventAtMs = 3L,
        lastWindowRootAtMs = 4L,
    )
    check(AccessibilityStartPolicy.evaluate(ready, 5L) == AccessibilityStartDisposition.ALLOW)

    val binderOnly = ready.copy(state = AccessibilityRuntimeState.BINDER_CONNECTED, lastWindowRootAtMs = null)
    check(AccessibilityStartPolicy.evaluate(binderOnly, 5L) == AccessibilityStartDisposition.ALLOW)

    println("AccessibilityStartPolicySmoke: PASS")
}
