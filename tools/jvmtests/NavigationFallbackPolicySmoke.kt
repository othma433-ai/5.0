package com.waalothmany.linkbot.automation

fun main() {
    check(NavigationFallbackPolicy.decide(groupsFound = true, chatsFound = true, missCount = 0, inChat = false) == NavigationFallbackAction.OPEN_GROUPS)
    check(NavigationFallbackPolicy.decide(groupsFound = false, chatsFound = true, missCount = 1, inChat = false) == NavigationFallbackAction.ANCHOR_CHATS)
    check(NavigationFallbackPolicy.decide(groupsFound = false, chatsFound = true, missCount = 5, inChat = false) == NavigationFallbackAction.FALLBACK_ALL_CHATS)
    check(NavigationFallbackPolicy.decide(groupsFound = false, chatsFound = false, missCount = 2, inChat = true) == NavigationFallbackAction.BACK_FROM_CHAT)
    check(NavigationFallbackPolicy.decide(groupsFound = false, chatsFound = false, missCount = 2, inChat = false) == NavigationFallbackAction.WAIT)
    check(NavigationFallbackPolicy.decide(groupsFound = false, chatsFound = false, missCount = 3, inChat = false) == NavigationFallbackAction.BACK_TOWARD_CHATS)
    check(NavigationFallbackPolicy.decide(groupsFound = false, chatsFound = false, missCount = 6, inChat = false) == NavigationFallbackAction.BACK_TOWARD_CHATS)
    println("NavigationFallbackPolicySmoke: PASS")
}
