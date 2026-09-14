package com.waalothmany.linkbot.whatsapp

data class InstanceInventoryItem(
    val id: String,
    val packageName: String,
    val label: String,
    val kind: String,
    val enabled: Boolean,
    val lastSeenAt: Long,
    val androidUserId: Int = 0,
    val profileType: String = "PERSONAL",
    val profileLabel: String? = null,
    val launchStrategy: String = "STANDARD",
    val lastResolvedEngine: String? = null,
    val reachable: Boolean = true,
    val lastSuccessfulLaunchAt: Long? = null,
)

object InstanceInventoryPolicy {
    private data class IdentityKey(val userId: Int, val packageName: String)

    fun reconcile(
        existing: List<InstanceInventoryItem>,
        detected: List<InstanceInventoryItem>,
        nowMs: Long,
    ): List<InstanceInventoryItem> {
        val detectedByKey = detected.associateBy { it.key() }
        val existingByKey = existing.associateBy { it.key() }

        val merged = existing.map { old ->
            val fresh = detectedByKey[old.key()]
            if (fresh != null) {
                // Preserve the durable database ID for rows migrated from v7.2.
                fresh.copy(id = old.id, enabled = true, lastSeenAt = nowMs)
            } else {
                old.copy(enabled = false, reachable = false)
            }
        }.toMutableList()

        detected.filterNot { it.key() in existingByKey }.forEach { fresh ->
            merged += fresh.copy(enabled = true, lastSeenAt = nowMs)
        }

        return merged.sortedWith(
            compareByDescending<InstanceInventoryItem> { it.enabled }
                .thenBy { it.androidUserId }
                .thenBy { it.label.lowercase() }
        )
    }

    private fun InstanceInventoryItem.key() = IdentityKey(androidUserId, packageName.trim().lowercase())
}
