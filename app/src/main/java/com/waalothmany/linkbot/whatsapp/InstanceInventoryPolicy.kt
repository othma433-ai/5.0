package com.waalothmany.linkbot.whatsapp

data class InstanceInventoryItem(
    val id: String,
    val packageName: String,
    val label: String,
    val kind: String,
    val enabled: Boolean,
    val lastSeenAt: Long,
)

object InstanceInventoryPolicy {
    fun reconcile(
        existing: List<InstanceInventoryItem>,
        detected: List<InstanceInventoryItem>,
        nowMs: Long,
    ): List<InstanceInventoryItem> {
        val detectedById = detected.associateBy { it.id }
        val merged = existing.map { old ->
            val fresh = detectedById[old.id]
            if (fresh != null) {
                fresh.copy(enabled = true, lastSeenAt = nowMs)
            } else {
                old.copy(enabled = false)
            }
        }.toMutableList()

        val existingIds = existing.mapTo(hashSetOf()) { it.id }
        detected.filterNot { it.id in existingIds }.forEach { fresh ->
            merged += fresh.copy(enabled = true, lastSeenAt = nowMs)
        }
        return merged.sortedWith(compareByDescending<InstanceInventoryItem> { it.enabled }.thenBy { it.label.lowercase() })
    }
}
