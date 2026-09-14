package com.waalothmany.linkbot.data

import androidx.room.withTransaction
import com.waalothmany.linkbot.core.link.LinkCandidate
import java.security.MessageDigest

class GroupRepository(private val db: AppDatabase) {
    val groups = db.groupDao().observeAll()
    val count = db.groupDao().observeCount()

    suspend fun upsert(items: List<GroupEntity>) {
        if (items.isEmpty()) return
        db.withTransaction {
            val dao = db.groupDao()
            val insertResults = dao.insertIgnore(items)
            items.forEachIndexed { index, item ->
                dao.updateSyncFields(
                    id = item.id,
                    displayTitle = item.displayTitle,
                    normalizedTitle = item.normalizedTitle,
                    unread = item.unread,
                    unreadCount = item.unreadCount,
                    active = item.active,
                    isNew = insertResults.getOrNull(index) != -1L,
                    lastPreview = item.lastPreview,
                    lastSeenAt = item.lastSeenAt,
                    syncId = item.lastSeenSyncId,
                )
            }
        }
    }

    suspend fun markMissing(instanceId: String, syncId: String) = db.groupDao().markMissing(instanceId, syncId)
    suspend fun selected(instanceId: String) = db.groupDao().selected(instanceId)
    suspend fun setSelected(id: String, value: Boolean) = db.groupDao().setSelected(id, value)
    suspend fun selectAll(instanceId: String) = db.groupDao().setAllSelected(instanceId, true)
    suspend fun clearSelection(instanceId: String) = db.groupDao().clearSelection(instanceId)
    suspend fun selectUnread(instanceId: String) { db.groupDao().clearSelection(instanceId); db.groupDao().selectByUnread(instanceId, true) }
    suspend fun selectRead(instanceId: String) { db.groupDao().clearSelection(instanceId); db.groupDao().selectByUnread(instanceId, false) }
    suspend fun selectActive(instanceId: String) { db.groupDao().clearSelection(instanceId); db.groupDao().selectActive(instanceId) }
    suspend fun selectNeverScanned(instanceId: String) { db.groupDao().clearSelection(instanceId); db.groupDao().selectNeverScanned(instanceId) }
    suspend fun selectFailed(instanceId: String) { db.groupDao().clearSelection(instanceId); db.groupDao().selectFailed(instanceId) }
    suspend fun selectCompleted(instanceId: String) { db.groupDao().clearSelection(instanceId); db.groupDao().selectCompleted(instanceId) }
    suspend fun selectPending(instanceId: String) { db.groupDao().clearSelection(instanceId); db.groupDao().selectPending(instanceId) }
    suspend fun selectNew(instanceId: String) { db.groupDao().clearSelection(instanceId); db.groupDao().selectNew(instanceId) }
    suspend fun invertSelection(instanceId: String) = db.groupDao().invertSelection(instanceId)

    suspend fun selectCurrentFilter(instanceId: String, filter: com.waalothmany.linkbot.automation.GroupFilterMode) {
        when (filter) {
            com.waalothmany.linkbot.automation.GroupFilterMode.ALL -> selectAll(instanceId)
            com.waalothmany.linkbot.automation.GroupFilterMode.UNREAD -> selectUnread(instanceId)
            com.waalothmany.linkbot.automation.GroupFilterMode.READ -> selectRead(instanceId)
            com.waalothmany.linkbot.automation.GroupFilterMode.ACTIVE -> selectActive(instanceId)
            com.waalothmany.linkbot.automation.GroupFilterMode.NEW -> selectNew(instanceId)
            com.waalothmany.linkbot.automation.GroupFilterMode.NOT_SCANNED -> selectNeverScanned(instanceId)
            com.waalothmany.linkbot.automation.GroupFilterMode.COMPLETED -> selectCompleted(instanceId)
            com.waalothmany.linkbot.automation.GroupFilterMode.FAILED -> selectFailed(instanceId)
            com.waalothmany.linkbot.automation.GroupFilterMode.PENDING -> selectPending(instanceId)
        }
    }
    suspend fun updateExtraction(id: String, state: String, checkpoint: String?) = db.groupDao().updateExtraction(id, state, checkpoint)

    suspend fun finalizeExtractionTransition(
        queueId: String,
        queueState: String,
        attempts: Int,
        error: String?,
        groupId: String,
        groupState: String,
        checkpoint: String?,
    ) {
        db.withTransaction {
            db.queueDao().updateState(queueId, queueState, attempts, error)
            db.groupDao().updateExtraction(groupId, groupState, checkpoint)
        }
    }
}

data class LinkRecordRequest(
    val candidate: LinkCandidate,
    val groupId: String?,
    val sessionId: String?,
    val source: String,
    val sender: String? = null,
    val timestampRaw: String? = null,
    val messageText: String? = null,
    val messageFingerprintSource: String? = null,
)

class LinkRepository(private val db: AppDatabase) {
    val links = db.linkDao().observeAll()
    val linkCount = db.linkDao().observeCount()
    val occurrenceCount = db.occurrenceDao().observeCount()

    suspend fun record(
        candidate: LinkCandidate,
        groupId: String?,
        sessionId: String?,
        source: String,
        sender: String? = null,
        timestampRaw: String? = null,
        messageText: String? = null,
        messageFingerprintSource: String? = null,
    ) = recordBatch(
        listOf(
            LinkRecordRequest(
                candidate = candidate,
                groupId = groupId,
                sessionId = sessionId,
                source = source,
                sender = sender,
                timestampRaw = timestampRaw,
                messageText = messageText,
                messageFingerprintSource = messageFingerprintSource,
            )
        )
    )

    /**
     * Persists a whole accessibility viewport/import batch in one Room transaction.
     * This both reduces WAL churn and makes occurrence counters consistent if the
     * process is interrupted mid-batch.
     */
    suspend fun recordBatch(requests: List<LinkRecordRequest>): LinkPersistResult {
        if (requests.isEmpty()) return LinkPersistResult()
        return db.withTransaction {
            val linkDao = db.linkDao()
            val occurrenceDao = db.occurrenceDao()
            val cache = HashMap<String, LinkEntity>()
            val now = System.currentTimeMillis()
            var newLinks = 0
            var insertedOccurrences = 0
            var duplicateOccurrences = 0

            for (request in requests) {
                val candidate = request.candidate
                var link = cache[candidate.canonicalUrl] ?: linkDao.find(candidate.canonicalUrl)
                if (link == null) {
                    val created = LinkEntity(
                        id = sha256(candidate.canonicalUrl),
                        canonicalUrl = candidate.canonicalUrl,
                        normalizedHash = sha256(candidate.canonicalUrl),
                        category = candidate.category.name,
                        firstSeenAt = now,
                        lastSeenAt = now,
                        occurrenceCount = 0,
                    )
                    val insertedLink = linkDao.insert(created) != -1L
                    if (insertedLink) newLinks++
                    link = linkDao.find(candidate.canonicalUrl) ?: created
                }

                val fingerprint = sha256(
                    "${request.groupId.orEmpty()}|${candidate.canonicalUrl}|${request.timestampRaw.orEmpty()}|${request.messageFingerprintSource ?: request.messageText.orEmpty()}"
                )
                val occurrenceId = sha256("occ|${link.id}|$fingerprint")
                val inserted = occurrenceDao.insert(
                    OccurrenceEntity(
                        id = occurrenceId,
                        linkId = link.id,
                        groupId = request.groupId,
                        sessionId = request.sessionId,
                        source = request.source,
                        sender = request.sender,
                        timestampRaw = request.timestampRaw,
                        messageFingerprint = fingerprint,
                        messageText = request.messageText,
                    )
                )
                if (inserted != -1L) {
                    insertedOccurrences++
                    link = link.copy(lastSeenAt = now, occurrenceCount = link.occurrenceCount + 1)
                    linkDao.update(link)
                } else {
                    duplicateOccurrences++
                }
                cache[candidate.canonicalUrl] = link
            }

            LinkPersistResult(
                newLinks = newLinks,
                insertedOccurrences = insertedOccurrences,
                duplicateOccurrences = duplicateOccurrences,
            )
        }
    }

    suspend fun allOccurrences() = db.occurrenceDao().all()

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}
