package com.waalothmany.linkbot.capability

import com.waalothmany.linkbot.runtime.engine.accessibility.AccessibilityRuntimeSnapshot
import com.waalothmany.linkbot.runtime.engine.accessibility.AccessibilityRuntimeState

enum class AccessibilityStartDisposition {
    ALLOW,
    WAIT_FOR_BIND,
    REQUIRE_USER_ACTION,
}

object AccessibilityStartPolicy {
    const val DEFAULT_BIND_GRACE_MS: Long = 8_000L

    fun evaluate(
        snapshot: AccessibilityRuntimeSnapshot,
        nowMs: Long,
        bindGraceMs: Long = DEFAULT_BIND_GRACE_MS,
    ): AccessibilityStartDisposition {
        if (!snapshot.settingsEnabled || snapshot.state == AccessibilityRuntimeState.DISABLED) {
            return AccessibilityStartDisposition.REQUIRE_USER_ACTION
        }
        if (snapshot.binderConnected) return AccessibilityStartDisposition.ALLOW

        val enabledAt = snapshot.enabledDetectedAtMs ?: nowMs
        val withinGrace = nowMs - enabledAt < bindGraceMs
        return if (
            snapshot.state == AccessibilityRuntimeState.ENABLED_WAITING_BIND && withinGrace
        ) {
            AccessibilityStartDisposition.WAIT_FOR_BIND
        } else {
            AccessibilityStartDisposition.REQUIRE_USER_ACTION
        }
    }
}
