package com.waalothmany.linkbot.automation

fun main() {
    check(SearchEntryPolicy.decide(ScreenKind.CHAT, editorVisible = true, searchControlVisible = true) == SearchEntryAction.BACK_TO_CHATS)
    check(SearchEntryPolicy.decide(ScreenKind.SEARCH, editorVisible = true, searchControlVisible = false) == SearchEntryAction.USE_EXISTING_EDITOR)
    check(SearchEntryPolicy.decide(ScreenKind.GROUP_LIST, editorVisible = false, searchControlVisible = true) == SearchEntryAction.OPEN_GLOBAL_SEARCH)
    check(SearchEntryPolicy.decide(ScreenKind.UNKNOWN, editorVisible = true, searchControlVisible = false) == SearchEntryAction.WAIT)
    check(SearchEntryPolicy.decide(ScreenKind.UNKNOWN, editorVisible = false, searchControlVisible = true) == SearchEntryAction.OPEN_GLOBAL_SEARCH)
    println("SearchEntryPolicySmoke: PASS")
}
