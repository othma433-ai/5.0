package com.waalothmany.linkbot.automation

enum class ViewportPersistenceState { NO_CHANGES, PERSISTING, COMMITTED, FAILED }

/**
 * Safety invariant for extraction: UI may move only when a viewport has no new
 * records or the records have been durably committed. This prevents a process
 * death from checkpointing past links that were still waiting on Room writes.
 */
object DurableViewportPolicy {
    fun mustPersist(discoveredLinks: Int): Boolean = discoveredLinks > 0

    fun canAdvance(state: ViewportPersistenceState): Boolean = when (state) {
        ViewportPersistenceState.NO_CHANGES, ViewportPersistenceState.COMMITTED -> true
        ViewportPersistenceState.PERSISTING, ViewportPersistenceState.FAILED -> false
    }
}
