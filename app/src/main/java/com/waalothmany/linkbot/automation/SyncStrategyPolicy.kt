package com.waalothmany.linkbot.automation

/** Synchronization paths ordered from highest-confidence to last-resort. */
enum class SyncStrategy {
    /** Groups chip -> normal RecyclerView scan. */
    GROUP_FILTER_SCROLL,

    /** Groups chip -> selection mode -> Select all -> scan selected group rows. */
    GROUP_FILTER_SELECT_ALL,

    /** All chats -> conservative group-row classification. Never marks unseen groups missing. */
    ALL_CHATS_CLASSIFY,
}

enum class SyncStrategySignal {
    GROUP_FILTER_UNAVAILABLE,
    DIRECT_SCAN_STALLED,
    SELECTION_UNAVAILABLE,
    STRATEGY_SUCCEEDED,
}

object SyncStrategyPolicy {
    fun initial(): SyncStrategy = SyncStrategy.GROUP_FILTER_SCROLL

    fun next(current: SyncStrategy, signal: SyncStrategySignal): SyncStrategy = when (current) {
        SyncStrategy.GROUP_FILTER_SCROLL -> when (signal) {
            SyncStrategySignal.DIRECT_SCAN_STALLED -> SyncStrategy.GROUP_FILTER_SELECT_ALL
            SyncStrategySignal.GROUP_FILTER_UNAVAILABLE -> SyncStrategy.ALL_CHATS_CLASSIFY
            SyncStrategySignal.SELECTION_UNAVAILABLE,
            SyncStrategySignal.STRATEGY_SUCCEEDED -> current
        }

        SyncStrategy.GROUP_FILTER_SELECT_ALL -> when (signal) {
            SyncStrategySignal.SELECTION_UNAVAILABLE,
            SyncStrategySignal.GROUP_FILTER_UNAVAILABLE,
            SyncStrategySignal.DIRECT_SCAN_STALLED -> SyncStrategy.ALL_CHATS_CLASSIFY
            SyncStrategySignal.STRATEGY_SUCCEEDED -> current
        }

        SyncStrategy.ALL_CHATS_CLASSIFY -> current
    }
}
