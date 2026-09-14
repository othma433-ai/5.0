package com.waalothmany.linkbot.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface InstanceDao {
    @Query("SELECT * FROM whatsapp_instances ORDER BY label")
    fun observeAll(): Flow<List<WhatsAppInstanceEntity>>

    @Query("SELECT * FROM whatsapp_instances ORDER BY label")
    suspend fun all(): List<WhatsAppInstanceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<WhatsAppInstanceEntity>)

    @Query("SELECT * FROM whatsapp_instances WHERE id=:id LIMIT 1")
    suspend fun get(id: String): WhatsAppInstanceEntity?

    @Query("SELECT * FROM whatsapp_instances WHERE packageName=:packageName LIMIT 1")
    suspend fun getByPackage(packageName: String): WhatsAppInstanceEntity?
}

@Dao
interface GroupDao {
    @Query("SELECT * FROM groups WHERE present=1 ORDER BY unread DESC, lastSeenAt DESC, displayTitle COLLATE NOCASE")
    fun observeAll(): Flow<List<GroupEntity>>

    @Query("SELECT COUNT(*) FROM groups WHERE present=1")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM groups WHERE selected=1 AND present=1 AND instanceId=:instanceId ORDER BY displayTitle COLLATE NOCASE")
    suspend fun selected(instanceId: String): List<GroupEntity>

    @Query("SELECT * FROM groups WHERE id=:id LIMIT 1")
    suspend fun get(id: String): GroupEntity?

    @Query("SELECT * FROM groups WHERE instanceId=:instanceId ORDER BY normalizedTitle, firstSeenAt")
    suspend fun byInstance(instanceId: String): List<GroupEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(items: List<GroupEntity>): List<Long>

    @Query("""
        UPDATE groups SET displayTitle=:displayTitle, normalizedTitle=:normalizedTitle, unread=:unread, unreadCount=:unreadCount, active=:active, isNew=:isNew,
               lastPreview=:lastPreview, lastSeenAt=:lastSeenAt, lastSeenSyncId=:syncId, present=1
        WHERE id=:id
    """)
    suspend fun updateSyncFields(
        id: String, displayTitle: String, normalizedTitle: String, unread: Boolean, unreadCount: Int?, active: Boolean, isNew: Boolean,
        lastPreview: String?, lastSeenAt: Long, syncId: String?
    )

    @Query("UPDATE groups SET present=0, selected=0 WHERE instanceId=:instanceId AND (lastSeenSyncId IS NULL OR lastSeenSyncId != :syncId)")
    suspend fun markMissing(instanceId: String, syncId: String)

    @Query("UPDATE groups SET selected=:selected WHERE id=:id")
    suspend fun setSelected(id: String, selected: Boolean)

    @Query("UPDATE groups SET selected=:selected WHERE instanceId=:instanceId AND present=1")
    suspend fun setAllSelected(instanceId: String, selected: Boolean)

    @Query("UPDATE groups SET selected=0 WHERE instanceId=:instanceId")
    suspend fun clearSelection(instanceId: String)

    @Query("UPDATE groups SET selected=1 WHERE instanceId=:instanceId AND unread=:unread AND present=1")
    suspend fun selectByUnread(instanceId: String, unread: Boolean)

    @Query("UPDATE groups SET selected=1 WHERE instanceId=:instanceId AND active=1 AND present=1")
    suspend fun selectActive(instanceId: String)

    @Query("UPDATE groups SET selected=1 WHERE instanceId=:instanceId AND extractionState='NEVER_SCANNED' AND present=1")
    suspend fun selectNeverScanned(instanceId: String)

    @Query("UPDATE groups SET selected=1 WHERE instanceId=:instanceId AND extractionState='FAILED' AND present=1")
    suspend fun selectFailed(instanceId: String)

    @Query("UPDATE groups SET selected=1 WHERE instanceId=:instanceId AND extractionState='COMPLETED' AND present=1")
    suspend fun selectCompleted(instanceId: String)

    @Query("UPDATE groups SET selected=1 WHERE instanceId=:instanceId AND extractionState='PENDING' AND present=1")
    suspend fun selectPending(instanceId: String)

    @Query("UPDATE groups SET selected=1 WHERE instanceId=:instanceId AND isNew=1 AND present=1")
    suspend fun selectNew(instanceId: String)

    @Query("UPDATE groups SET selected=CASE WHEN selected=1 THEN 0 ELSE 1 END WHERE instanceId=:instanceId AND present=1")
    suspend fun invertSelection(instanceId: String)

    @Query("UPDATE groups SET extractionState=:state, checkpoint=:checkpoint WHERE id=:id")
    suspend fun updateExtraction(id: String, state: String, checkpoint: String?)
}

@Dao
interface LinkDao {
    @Query("SELECT COUNT(*) FROM links")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM links")
    suspend fun countNow(): Int

    @Query("SELECT * FROM links ORDER BY lastSeenAt DESC")
    fun observeAll(): Flow<List<LinkEntity>>

    @Query("SELECT * FROM links WHERE canonicalUrl=:url LIMIT 1")
    suspend fun find(url: String): LinkEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: LinkEntity): Long

    @Update
    suspend fun update(item: LinkEntity)
}

@Dao
interface OccurrenceDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: OccurrenceEntity): Long

    @Query("SELECT COUNT(*) FROM occurrences")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM occurrences")
    suspend fun countNow(): Int

    @Query("SELECT * FROM occurrences ORDER BY createdAt DESC")
    suspend fun all(): List<OccurrenceEntity>
}

@Dao
interface QueueDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<QueueItemEntity>)

    @Query("SELECT * FROM queue_items WHERE sessionId=:sessionId ORDER BY position")
    suspend fun forSession(sessionId: String): List<QueueItemEntity>

    @Query("SELECT * FROM queue_items WHERE id=:id LIMIT 1")
    suspend fun get(id: String): QueueItemEntity?

    @Query("SELECT * FROM queue_items WHERE sessionId=:sessionId AND state IN ('WAITING','RUNNING','PAUSED','PARTIAL') ORDER BY position LIMIT 1")
    suspend fun nextPending(sessionId: String): QueueItemEntity?

    @Query("UPDATE queue_items SET state=:state, attempts=:attempts, lastError=:error, updatedAt=:updatedAt WHERE id=:id")
    suspend fun updateState(id: String, state: String, attempts: Int, error: String?, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE queue_items SET state='WAITING', lastError=NULL, updatedAt=:updatedAt WHERE sessionId=:sessionId AND state='FAILED'")
    suspend fun resetFailed(sessionId: String, updatedAt: Long = System.currentTimeMillis()): Int

    @Query("SELECT COUNT(*) FROM queue_items WHERE sessionId=:sessionId AND state='FAILED'")
    suspend fun failedCount(sessionId: String): Int

    @Query("DELETE FROM queue_items")
    suspend fun clear()
}

@Dao
interface SessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: BotSessionEntity)

    @Query("SELECT * FROM sessions WHERE state IN ('RUNNING','PAUSED','RECOVERING') ORDER BY updatedAt DESC LIMIT 1")
    suspend fun active(): BotSessionEntity?

    @Query("SELECT * FROM sessions WHERE type='EXTRACTION' AND state IN ('RUNNING','PAUSED','RECOVERING','STOPPED') ORDER BY updatedAt DESC LIMIT 1")
    suspend fun latestResumable(): BotSessionEntity?

    @Query("SELECT * FROM sessions ORDER BY startedAt DESC LIMIT 1")
    fun observeLatest(): Flow<BotSessionEntity?>

    @Query("SELECT * FROM sessions ORDER BY startedAt DESC LIMIT 1")
    suspend fun latest(): BotSessionEntity?

    @Query("SELECT * FROM sessions WHERE id=:id LIMIT 1")
    suspend fun get(id: String): BotSessionEntity?

    @Query("UPDATE sessions SET state=:state, updatedAt=:updatedAt, completedAt=:completedAt WHERE id=:id")
    suspend fun updateState(id: String, state: String, updatedAt: Long = System.currentTimeMillis(), completedAt: Long? = null)
}

data class ExportOccurrenceRow(
    val occurrenceId: String,
    val linkId: String,
    val groupId: String?,
    val url: String,
    val category: String,
    val occurrenceCount: Int,
    val groupTitle: String?,
    val sender: String?,
    val timestampRaw: String?,
    val createdAt: Long,
)

@Dao
interface ExportDao {
    @Query("""
        SELECT o.id AS occurrenceId, o.linkId AS linkId, o.groupId AS groupId,
               l.canonicalUrl AS url, l.category AS category, l.occurrenceCount AS occurrenceCount,
               g.displayTitle AS groupTitle, o.sender AS sender, o.timestampRaw AS timestampRaw,
               o.createdAt AS createdAt
        FROM occurrences o
        JOIN links l ON l.id=o.linkId
        LEFT JOIN groups g ON g.id=o.groupId
        ORDER BY o.createdAt DESC
    """)
    suspend fun occurrences(): List<ExportOccurrenceRow>

    @Query("""
        SELECT o.id AS occurrenceId, o.linkId AS linkId, o.groupId AS groupId,
               l.canonicalUrl AS url, l.category AS category, l.occurrenceCount AS occurrenceCount,
               g.displayTitle AS groupTitle, o.sender AS sender, o.timestampRaw AS timestampRaw,
               o.createdAt AS createdAt
        FROM occurrences o
        JOIN links l ON l.id=o.linkId
        LEFT JOIN groups g ON g.id=o.groupId
        ORDER BY o.createdAt DESC
    """)
    fun observeOccurrences(): Flow<List<ExportOccurrenceRow>>
}
