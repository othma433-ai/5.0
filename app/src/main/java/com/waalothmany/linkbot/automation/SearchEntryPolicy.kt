package com.waalothmany.linkbot.automation

enum class SearchEntryAction {
    BACK_TO_CHATS,
    USE_EXISTING_EDITOR,
    OPEN_GLOBAL_SEARCH,
    WAIT,
}

/**
 * Prevents an arbitrary editable node (especially a chat composer) from being
 * treated as WhatsApp's global search editor.
 */
object SearchEntryPolicy {
    fun decide(
        screenKind: ScreenKind,
        editorVisible: Boolean,
        searchControlVisible: Boolean,
    ): SearchEntryAction = when {
        screenKind == ScreenKind.CHAT -> SearchEntryAction.BACK_TO_CHATS
        screenKind == ScreenKind.SEARCH && editorVisible -> SearchEntryAction.USE_EXISTING_EDITOR
        searchControlVisible -> SearchEntryAction.OPEN_GLOBAL_SEARCH
        else -> SearchEntryAction.WAIT
    }
}
