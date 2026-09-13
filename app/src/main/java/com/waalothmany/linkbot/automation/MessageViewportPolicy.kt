package com.waalothmany.linkbot.automation

data class ViewportMessage(
    val fingerprint: String?,
    val unreadMarker: Boolean,
)

/**
 * Pure overlap/unread-window policy. WhatsApp RecyclerViews intentionally overlap rows between
 * scrolls, so repeated fingerprints are filtered before expensive URL parsing and DB work.
 */
object MessageViewportPolicy {
    fun select(
        items: List<ViewportMessage>,
        seen: Set<String>,
        unreadOnly: Boolean,
    ): List<String> {
        val marker = items.indexOfLast { it.unreadMarker }
        val candidates = if (unreadOnly && marker >= 0) items.drop(marker + 1) else items
        return candidates.asSequence()
            .mapNotNull { it.fingerprint }
            .filter { it !in seen }
            .distinct()
            .toList()
    }

    fun mergeSeen(current: Set<String>, newlySeen: Collection<String>, maxEntries: Int = 512): Set<String> {
        require(maxEntries > 0)
        val merged = LinkedHashSet<String>(current.size + newlySeen.size)
        current.forEach(merged::add)
        newlySeen.forEach {
            merged.remove(it)
            merged.add(it)
        }
        while (merged.size > maxEntries) {
            val first = merged.firstOrNull() ?: break
            merged.remove(first)
        }
        return merged
    }
}
