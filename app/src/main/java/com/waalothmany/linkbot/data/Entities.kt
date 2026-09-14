package com.waalothmany.linkbot.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "whatsapp_instances",
    indices = [Index(value = ["androidUserId", "packageName"], unique = true)],
)
data class WhatsAppInstanceEntity(
    @PrimaryKey val id: String,
    val packageName: String,
    val label: String,
    val kind: String,
    val androidUserId: Int = 0,
    val profileType: String = "PERSONAL",
    val profileLabel: String? = null,
    val launchStrategy: String = "STANDARD",
    val lastResolvedEngine: String? = null,
    val reachable: Boolean = true,
    val lastSuccessfulLaunchAt: Long? = null,
    val enabled: Boolean = true,
    val lastSeenAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "groups",
    indices = [
        Index(value = ["instanceId", "normalizedTitle"]),
        Index(value = ["lastSeenSyncId"]),
    ],
)
data class GroupEntity(
    @PrimaryKey val id: String,
    val instanceId: String,
    val displayTitle: String,
    val normalizedTitle: String,
    val rowFingerprint: String,
    val unread: Boolean = false,
    val unreadCount: Int? = null,
    val active: Boolean = false,
    val lastPreview: String? = null,
    val firstSeenAt: Long = System.currentTimeMillis(),
    val lastSeenAt: Long = System.currentTimeMillis(),
    val lastSeenSyncId: String? = null,
    val extractionState: String = "NEVER_SCANNED",
    val selected: Boolean = false,
    val checkpoint: String? = null,
    val present: Boolean = true,
)

@Entity(tableName = "links", indices = [Index(value = ["canonicalUrl"], unique = true)])
data class LinkEntity(
    @PrimaryKey val id: String,
    val canonicalUrl: String,
    val normalizedHash: String,
    val category: String,
    val firstSeenAt: Long = System.currentTimeMillis(),
    val lastSeenAt: Long = System.currentTimeMillis(),
    val occurrenceCount: Int = 1,
)

@Entity(
    tableName = "occurrences",
    indices = [Index(value = ["linkId"]), Index(value = ["groupId"]), Index(value = ["sessionId"])],
)
data class OccurrenceEntity(
    @PrimaryKey val id: String,
    val linkId: String,
    val groupId: String?,
    val sessionId: String?,
    val source: String,
    val sender: String? = null,
    val timestampRaw: String? = null,
    val messageFingerprint: String,
    val messageText: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "queue_items", indices = [Index(value = ["sessionId", "position"], unique = true)])
data class QueueItemEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val groupId: String,
    val position: Int,
    val state: String = "WAITING",
    val attempts: Int = 0,
    val lastError: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "sessions")
data class BotSessionEntity(
    @PrimaryKey val id: String,
    val instanceId: String,
    val type: String,
    val mode: String,
    val state: String,
    val startedAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
)
