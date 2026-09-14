package com.waalothmany.linkbot.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.waalothmany.linkbot.core.link.LinkCandidate
import com.waalothmany.linkbot.core.link.LinkCategory
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PersistenceRepositoryInstrumentedTest {
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun duplicateOccurrenceIsNotCountedAsPersisted() = runBlocking {
        val repo = LinkRepository(db)
        val request = LinkRecordRequest(
            candidate = LinkCandidate(
                originalUrl = "https://example.com/a",
                canonicalUrl = "https://example.com/a",
                category = LinkCategory.WEBSITE,
            ),
            groupId = "g1",
            sessionId = "s1",
            source = "TEST",
            messageFingerprintSource = "message-1",
        )

        val first = repo.recordBatch(listOf(request))
        val second = repo.recordBatch(listOf(request))

        assertEquals(1, first.newLinks)
        assertEquals(1, first.insertedOccurrences)
        assertEquals(0, first.duplicateOccurrences)
        assertEquals(0, second.newLinks)
        assertEquals(0, second.insertedOccurrences)
        assertEquals(1, second.duplicateOccurrences)
        assertEquals(1, repo.allOccurrences().size)
        assertEquals(1, db.linkDao().find("https://example.com/a")?.occurrenceCount)
    }

    @Test
    fun checkpointAndQueueStateRollbackTogetherWhenGroupUpdateFails() = runBlocking {
        val instance = WhatsAppInstanceEntity("i1", "com.whatsapp", "WhatsApp", "PERSONAL")
        db.instanceDao().upsertAll(listOf(instance))
        val group = GroupEntity(
            id = "g1",
            instanceId = "i1",
            displayTitle = "Group",
            normalizedTitle = "group",
            rowFingerprint = "fp",
            checkpoint = "old-checkpoint",
        )
        db.groupDao().insertIgnore(listOf(group))
        val queue = QueueItemEntity("q1", "s1", "g1", 0)
        db.queueDao().upsertAll(listOf(queue))

        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_group_checkpoint BEFORE UPDATE OF checkpoint ON groups " +
                "BEGIN SELECT RAISE(ABORT, 'forced checkpoint failure'); END"
        )

        val repo = GroupRepository(db)
        assertThrows(Exception::class.java) {
            runBlocking {
                repo.finalizeExtractionTransition(
                    queueId = "q1",
                    queueState = "COMPLETED",
                    attempts = 0,
                    error = null,
                    groupId = "g1",
                    groupState = "COMPLETED",
                    checkpoint = "new-checkpoint",
                )
            }
        }

        assertEquals("WAITING", db.queueDao().get("q1")?.state)
        assertEquals("old-checkpoint", db.groupDao().get("g1")?.checkpoint)
        assertNotNull(db.groupDao().get("g1"))
    }
}
