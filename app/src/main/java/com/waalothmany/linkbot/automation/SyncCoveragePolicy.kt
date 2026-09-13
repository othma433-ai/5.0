package com.waalothmany.linkbot.automation

enum class SyncCoverageDecision { ACCEPT, SAFETY_STOP }

/**
 * Prevents a UI-selector regression from making a large existing registry appear
 * to have vanished in one sync. Small registries are intentionally permissive,
 * while a severe drop on an established registry is treated as suspicious.
 */
object SyncCoveragePolicy {
    fun decide(previousPresent: Int, discovered: Int): SyncCoverageDecision {
        if (previousPresent <= 0) return SyncCoverageDecision.ACCEPT
        if (previousPresent < 20) return SyncCoverageDecision.ACCEPT
        if (discovered <= 0) return SyncCoverageDecision.SAFETY_STOP
        val ratio = discovered.toDouble() / previousPresent.toDouble()
        return if (ratio < 0.35) SyncCoverageDecision.SAFETY_STOP else SyncCoverageDecision.ACCEPT
    }
}
