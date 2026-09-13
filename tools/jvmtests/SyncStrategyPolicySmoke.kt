package com.waalothmany.linkbot.automation

fun main() {
    check(SyncStrategyPolicy.initial() == SyncStrategy.GROUP_FILTER_SCROLL)
    check(SyncStrategyPolicy.next(SyncStrategy.GROUP_FILTER_SCROLL, SyncStrategySignal.DIRECT_SCAN_STALLED) == SyncStrategy.GROUP_FILTER_SELECT_ALL)
    check(SyncStrategyPolicy.next(SyncStrategy.GROUP_FILTER_SELECT_ALL, SyncStrategySignal.SELECTION_UNAVAILABLE) == SyncStrategy.ALL_CHATS_CLASSIFY)
    check(SyncStrategyPolicy.next(SyncStrategy.GROUP_FILTER_SCROLL, SyncStrategySignal.GROUP_FILTER_UNAVAILABLE) == SyncStrategy.ALL_CHATS_CLASSIFY)
    check(SyncStrategyPolicy.next(SyncStrategy.ALL_CHATS_CLASSIFY, SyncStrategySignal.GROUP_FILTER_UNAVAILABLE) == SyncStrategy.ALL_CHATS_CLASSIFY)
    println("SyncStrategyPolicySmoke: PASS")
}
