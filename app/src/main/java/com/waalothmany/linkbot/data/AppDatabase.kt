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
                db.execSQL("ALTER TABLE whatsapp_instances ADD COLUMN profileIdentity TEXT NOT NULL DEFAULT 'current'")
                db.execSQL("ALTER TABLE whatsapp_instances ADD COLUMN installationIdentity TEXT NOT NULL DEFAULT 'default'")
                db.execSQL("ALTER TABLE whatsapp_instances ADD COLUMN profileSerial INTEGER")
                db.execSQL("ALTER TABLE whatsapp_instances ADD COLUMN adapterId TEXT NOT NULL DEFAULT 'generic-discoverable'")
                db.execSQL("ALTER TABLE whatsapp_instances ADD COLUMN discoveryEvidence TEXT NOT NULL DEFAULT 'CURRENT_PROFILE_PACKAGE'")
                db.execSQL("ALTER TABLE groups ADD COLUMN isNew INTEGER NOT NULL DEFAULT 0")
                db.execSQL("DROP INDEX IF EXISTS index_whatsapp_instances_packageName")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_whatsapp_instances_packageName_profileIdentity_installationIdentity ON whatsapp_instances(packageName, profileIdentity, installationIdentity)")
            }
        }

        fun create(context: Context): AppDatabase = Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "wa_link_bot.db",
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .build()
    }
}
