package com.waalothmany.linkbot.automation

data class QueuePriorityInput(
    val id: String,
    val unread: Boolean,
    val unreadCount: Int?,
    val active: Boolean,
    val extractionState: String,
    val lastSeenAt: Long,
)

/**
 * Deterministic priority policy for large queues. It never drops a selected group;
 * it only changes execution order so high-value/new work is reached sooner.
 */
object SmartQueuePolicy {
    fun order(items: List<QueuePriorityInput>, mode: AutomationMode): List<QueuePriorityInput> {
        val comparator = when (mode) {
            AutomationMode.UNREAD_ONLY -> compareByDescending<QueuePriorityInput> { it.unread }
                .thenByDescending { it.unreadCount ?: 0 }
                .thenByDescending { it.active }
                .thenByDescending { it.lastSeenAt }
                .thenBy { it.id }
            AutomationMode.NEW_ONLY -> compareByDescending<QueuePriorityInput> { it.unread }
                .thenBy { stateRankNewOnly(it.extractionState) }
                .thenByDescending { it.active }
                .thenByDescending { it.lastSeenAt }
                .thenBy { it.id }
            AutomationMode.DEEP -> compareBy<QueuePriorityInput> { stateRankDeep(it.extractionState) }
                .thenByDescending { it.unread }
                .thenByDescending { it.active }
                .thenByDescending { it.lastSeenAt }
                .thenBy { it.id }
        }
        return items.sortedWith(comparator)
    }

    private fun stateRankDeep(state: String): Int = when (state.uppercase()) {
        "NEVER_SCANNED" -> 0
        "FAILED", "PARTIAL" -> 1
        "COMPLETED" -> 2
        else -> 3
    }

    private fun stateRankNewOnly(state: String): Int = when (state.uppercase()) {
        "NEVER_SCANNED" -> 0
        "COMPLETED" -> 1
        "FAILED", "PARTIAL" -> 2
        else -> 3
    }
}
