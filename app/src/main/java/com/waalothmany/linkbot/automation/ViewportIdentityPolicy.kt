package com.waalothmany.linkbot.automation

/** Identity observed in the immediately previous RecyclerView viewport. */
data class ViewportIdentity(val signature: String, val groupId: String)

/**
 * Reuses IDs across adjacent RecyclerView viewports while preserving duplicate rows one-to-one.
 * A simple signature->ID map is unsafe because two legitimate groups may expose identical UI metadata.
 */
object ViewportIdentityPolicy {
    fun reuseIds(currentSignatures: List<String>, previous: List<ViewportIdentity>): List<String?> {
        if (currentSignatures.isEmpty()) return emptyList()
        val queues = LinkedHashMap<String, java.util.ArrayDeque<String>>()
        previous.forEach { item -> queues.getOrPut(item.signature) { java.util.ArrayDeque() }.addLast(item.groupId) }
        return currentSignatures.map { signature -> queues[signature]?.pollFirst() }
    }
}
