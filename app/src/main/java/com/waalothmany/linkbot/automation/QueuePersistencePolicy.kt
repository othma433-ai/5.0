package com.waalothmany.linkbot.automation

enum class QueuePersistentState { WAITING, RUNNING, PAUSED, COMPLETED, PARTIAL, FAILED, SKIPPED }

object QueuePersistencePolicy {
    fun normalize(raw: String): QueuePersistentState = when (raw.uppercase()) {
        "WAITING" -> QueuePersistentState.WAITING
        "PAUSED" -> QueuePersistentState.PAUSED
        "COMPLETED" -> QueuePersistentState.COMPLETED
        "PARTIAL" -> QueuePersistentState.PARTIAL
        "FAILED" -> QueuePersistentState.FAILED
        "SKIPPED" -> QueuePersistentState.SKIPPED
        "RUNNING", "LOCATING", "OPENING", "VERIFYING", "SCANNING" -> QueuePersistentState.RUNNING
        else -> QueuePersistentState.RUNNING
    }

    fun isResumable(state: QueuePersistentState): Boolean = state in setOf(
        QueuePersistentState.WAITING,
        QueuePersistentState.RUNNING,
        QueuePersistentState.PAUSED,
        QueuePersistentState.PARTIAL,
    )

    fun isTerminal(state: QueuePersistentState): Boolean = state in setOf(
        QueuePersistentState.COMPLETED,
        QueuePersistentState.FAILED,
        QueuePersistentState.SKIPPED,
    )
}
