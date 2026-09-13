package com.waalothmany.linkbot.automation

enum class ExtractionNavigationAction {
    GROUP_FILTER_SEARCH,
    ANCHOR_CHATS,
    OPEN_SEARCH,
    BACK_TOWARD_CHATS,
    WAIT,
    FAIL,
}

/**
 * Extraction must not depend on the Groups filter being exposed by WhatsApp.
 * When the filter is unavailable, a verified Chats screen may use global search.
 */
object ExtractionNavigationPolicy {
    fun decide(
        groupsFound: Boolean,
        chatsFound: Boolean,
        searchFound: Boolean,
        inChat: Boolean,
        missCount: Int,
    ): ExtractionNavigationAction = when {
        groupsFound -> ExtractionNavigationAction.GROUP_FILTER_SEARCH
        inChat -> ExtractionNavigationAction.BACK_TOWARD_CHATS
        searchFound -> ExtractionNavigationAction.OPEN_SEARCH
        chatsFound && missCount >= 4 -> ExtractionNavigationAction.OPEN_SEARCH
        chatsFound -> ExtractionNavigationAction.ANCHOR_CHATS
        !groupsFound && !chatsFound && !searchFound && missCount in setOf(3, 6) -> ExtractionNavigationAction.BACK_TOWARD_CHATS
        missCount >= 8 -> ExtractionNavigationAction.FAIL
        else -> ExtractionNavigationAction.WAIT
    }
}
