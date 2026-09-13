package com.waalothmany.linkbot.data

import android.content.Context
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
    version = 2,
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
        fun create(context: Context): AppDatabase = Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "wa_link_bot.db",
        ).addMigrations(MIGRATION_1_2)
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .build()
    }
}
