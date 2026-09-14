package com.waalothmany.linkbot.automation

enum class GroupFilterMode { ALL, UNREAD, READ, ACTIVE, NEW, NOT_SCANNED, COMPLETED, FAILED, PENDING }

data class GroupFilterFacts(
    val unread: Boolean,
    val active: Boolean,
    val isNew: Boolean,
    val extractionState: String,
)

object GroupFilterPolicy {
    fun matches(filter: GroupFilterMode, facts: GroupFilterFacts): Boolean = when (filter) {
        GroupFilterMode.ALL -> true
        GroupFilterMode.UNREAD -> facts.unread
        GroupFilterMode.READ -> !facts.unread
        GroupFilterMode.ACTIVE -> facts.active
        GroupFilterMode.NEW -> facts.isNew
        GroupFilterMode.NOT_SCANNED -> facts.extractionState == "NEVER_SCANNED"
        GroupFilterMode.COMPLETED -> facts.extractionState == "COMPLETED"
        GroupFilterMode.FAILED -> facts.extractionState == "FAILED"
        GroupFilterMode.PENDING -> facts.extractionState == "PENDING"
    }
}
