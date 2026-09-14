package com.waalothmany.linkbot.data

import android.content.Context
import android.os.UserHandle
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        WhatsAppInstanceEntity::class,
        GroupEntity::class,
        LinkEntity::class,
        OccurrenceEntity::class,
        QueueItemEntity::class,
        BotSessionEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun instanceDao(): InstanceDao
    abstract fun groupDao(): GroupDao
    abstract fun linkDao(): LinkDao
    abstract fun occurrenceDao(): OccurrenceDao
    abstract fun queueDao(): QueueDao
    abstract fun sessionDao(): SessionDao
    abstract fun exportDao(): ExportDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP INDEX IF EXISTS index_groups_instanceId_normalizedTitle_rowFingerprint")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_groups_instanceId_normalizedTitle ON groups(instanceId, normalizedTitle)")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS whatsapp_instances_v3 (
                        id TEXT NOT NULL PRIMARY KEY,
                        packageName TEXT NOT NULL,
                        label TEXT NOT NULL,
                        kind TEXT NOT NULL,
                        androidUserId INTEGER NOT NULL DEFAULT -1,
                        profileType TEXT NOT NULL DEFAULT 'UNKNOWN',
                        profileLabel TEXT,
                        launchStrategy TEXT NOT NULL DEFAULT 'STANDARD',
                        lastResolvedEngine TEXT,
                        reachable INTEGER NOT NULL DEFAULT 1,
                        lastSuccessfulLaunchAt INTEGER,
                        enabled INTEGER NOT NULL DEFAULT 1,
                        lastSeenAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO whatsapp_instances_v3 (
                        id, packageName, label, kind, androidUserId, profileType,
                        profileLabel, launchStrategy, lastResolvedEngine, reachable,
                        lastSuccessfulLaunchAt, enabled, lastSeenAt
                    )
                    SELECT id, packageName, label, kind, -1, 'UNKNOWN',
                           NULL, 'STANDARD', NULL, 1, NULL, enabled, lastSeenAt
                    FROM whatsapp_instances
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE whatsapp_instances")
                db.execSQL("ALTER TABLE whatsapp_instances_v3 RENAME TO whatsapp_instances")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_whatsapp_instances_androidUserId_packageName " +
                        "ON whatsapp_instances(androidUserId, packageName)"
                )
            }
        }

        fun create(context: Context): AppDatabase {
            val currentUserId = UserHandle.myUserId()
            val currentProfileType = if (currentUserId == 0) "PERSONAL" else "UNKNOWN"
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "wa_link_bot.db",
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        // Rows migrated from v7.2 came from the profile in which the app
                        // itself was running. Resolve that profile at runtime rather than
                        // hard-coding user 0 during SQL migration.
                        db.execSQL(
                            "UPDATE whatsapp_instances SET androidUserId=?, profileType=? WHERE androidUserId=-1",
                            arrayOf(currentUserId, currentProfileType),
                        )
                    }
                })
                .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                .build()
        }
    }
}
