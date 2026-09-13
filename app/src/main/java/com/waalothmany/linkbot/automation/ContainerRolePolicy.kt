package com.waalothmany.linkbot.automation

enum class ContainerRole { CONVERSATIONS, MESSAGES, UNKNOWN }

data class ContainerSignals(
    val childCount: Int,
    val conversationRows: Int,
    val textChildren: Int,
    val urlChildren: Int,
)

data class ContainerRoleScore(
    val conversationScore: Int,
    val messageScore: Int,
)

/**
 * Scores scrollable containers by semantic role so the engine does not blindly choose the
 * largest RecyclerView when WhatsApp adds nested/auxiliary scrollables.
 */
object ContainerRolePolicy {
    fun score(signals: ContainerSignals): ContainerRoleScore {
        val conversation = (
            signals.conversationRows * 28 +
                minOf(signals.childCount, 12) * 2 +
                if (signals.conversationRows >= 2) 20 else 0
            ).coerceIn(0, 100)
        val messages = (
            signals.textChildren * 7 +
                signals.urlChildren * 12 +
                minOf(signals.childCount, 16) * 2 -
                signals.conversationRows * 10
            ).coerceIn(0, 100)
        return ContainerRoleScore(conversation, messages)
    }

    fun preferredRole(score: ContainerRoleScore): ContainerRole = when {
        score.conversationScore >= 45 && score.conversationScore >= score.messageScore + 10 -> ContainerRole.CONVERSATIONS
        score.messageScore >= 45 && score.messageScore >= score.conversationScore + 10 -> ContainerRole.MESSAGES
        else -> ContainerRole.UNKNOWN
    }
}
