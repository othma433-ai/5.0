package com.waalothmany.linkbot.capability

fun main() {
    val neverConnected = AccessibilityConnectionSnapshot()
    check(
        AccessibilityConnectionPolicy.deriveState(
            enabledInSettings = false,
            snapshot = neverConnected,
            nowMs = 10_000,
        ) == AccessibilityRuntimeState.DISABLED
    )
    check(
        AccessibilityConnectionPolicy.deriveState(
            enabledInSettings = true,
            snapshot = neverConnected,
            nowMs = 10_000,
        ) == AccessibilityRuntimeState.ENABLED_NOT_CONNECTED
    )

    val connected = AccessibilityConnectionSnapshot(
        state = AccessibilityRuntimeState.CONNECTED,
        connectedAtMs = 1_000,
        lastSignalAtMs = 9_000,
        generation = 1,
    )
    check(
        AccessibilityConnectionPolicy.deriveState(
            enabledInSettings = true,
            snapshot = connected,
            nowMs = 10_000,
            staleAfterMs = 5_000,
        ) == AccessibilityRuntimeState.CONNECTED
    )
    check(
        AccessibilityConnectionPolicy.deriveState(
            enabledInSettings = true,
            snapshot = connected.copy(lastSignalAtMs = 1_000),
            nowMs = 10_000,
            staleAfterMs = 5_000,
        ) == AccessibilityRuntimeState.STALE
    )

    val disconnected = connected.copy(state = AccessibilityRuntimeState.DISCONNECTED, lastSignalAtMs = 9_500)
    check(
        AccessibilityConnectionPolicy.deriveState(
            enabledInSettings = true,
            snapshot = disconnected,
            nowMs = 10_000,
        ) == AccessibilityRuntimeState.DISCONNECTED
    )

    check(AccessibilityConnectionPolicy.isOperational(AccessibilityRuntimeState.CONNECTED))
    check(!AccessibilityConnectionPolicy.isOperational(AccessibilityRuntimeState.STALE))
    check(!AccessibilityConnectionPolicy.isOperational(AccessibilityRuntimeState.ENABLED_NOT_CONNECTED))
    check(!AccessibilityConnectionPolicy.isOperational(AccessibilityRuntimeState.DISCONNECTED))
    check(!AccessibilityConnectionPolicy.isOperational(AccessibilityRuntimeState.DISABLED))

    println("AccessibilityConnectionPolicySmoke: PASS")
}
