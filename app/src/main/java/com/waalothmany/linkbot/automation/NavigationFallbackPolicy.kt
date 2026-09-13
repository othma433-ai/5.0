package com.waalothmany.linkbot.automation

enum class NavigationFallbackAction {
    OPEN_GROUPS,
    ANCHOR_CHATS,
    BACK_FROM_CHAT,
    FALLBACK_ALL_CHATS,
    BACK_TOWARD_CHATS,
    WAIT,
}

/**
 * Pure decision policy for the sync-opening stage.
 * It prevents blind click/back loops while still escaping a WhatsApp UI profile
 * that does not expose the Groups filter to Accessibility.
 */
object NavigationFallbackPolicy {
    fun decide(
        groupsFound: Boolean,
        chatsFound: Boolean,
        missCount: Int,
        inChat: Boolean,
    ): NavigationFallbackAction = when {
        groupsFound -> NavigationFallbackAction.OPEN_GROUPS
        inChat -> NavigationFallbackAction.BACK_FROM_CHAT
        chatsFound && missCount >= 4 -> NavigationFallbackAction.FALLBACK_ALL_CHATS
        chatsFound -> NavigationFallbackAction.ANCHOR_CHATS
        !groupsFound && !chatsFound && missCount in setOf(3, 6) -> NavigationFallbackAction.BACK_TOWARD_CHATS
        else -> NavigationFallbackAction.WAIT
    }
}
