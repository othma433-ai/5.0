package com.waalothmany.linkbot.automation

fun main() {
    check(ExtractionNavigationPolicy.decide(groupsFound = true, chatsFound = true, searchFound = false, inChat = false, missCount = 0) == ExtractionNavigationAction.GROUP_FILTER_SEARCH)
    check(ExtractionNavigationPolicy.decide(groupsFound = false, chatsFound = true, searchFound = true, inChat = false, missCount = 2) == ExtractionNavigationAction.OPEN_SEARCH)
    check(ExtractionNavigationPolicy.decide(groupsFound = false, chatsFound = true, searchFound = false, inChat = false, missCount = 1) == ExtractionNavigationAction.ANCHOR_CHATS)
    check(ExtractionNavigationPolicy.decide(groupsFound = false, chatsFound = true, searchFound = false, inChat = false, missCount = 5) == ExtractionNavigationAction.OPEN_SEARCH)
    check(ExtractionNavigationPolicy.decide(groupsFound = false, chatsFound = false, searchFound = true, inChat = true, missCount = 1) == ExtractionNavigationAction.BACK_TOWARD_CHATS)
    check(ExtractionNavigationPolicy.decide(groupsFound = false, chatsFound = false, searchFound = false, inChat = false, missCount = 3) == ExtractionNavigationAction.BACK_TOWARD_CHATS)
    check(ExtractionNavigationPolicy.decide(groupsFound = false, chatsFound = false, searchFound = false, inChat = false, missCount = 6) == ExtractionNavigationAction.BACK_TOWARD_CHATS)
    check(ExtractionNavigationPolicy.decide(groupsFound = false, chatsFound = false, searchFound = false, inChat = false, missCount = 9) == ExtractionNavigationAction.FAIL)
    println("ExtractionNavigationPolicySmoke: PASS")
}
