package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.dao.AuditLogDao
import com.example.data.local.dao.ChildSafetyDao
import com.example.data.local.dao.EmergencyEventDao
import com.example.data.local.dao.EvidenceDao
import com.example.data.local.dao.OfflineQueueDao
import com.example.data.local.dao.TrustedContactDao
import com.example.data.local.entity.AuditLogEntity
import com.example.data.local.entity.ChildProfileEntity
import com.example.data.local.entity.ChildSafetyAlertEntity
import com.example.data.local.entity.EmergencyEventEntity
import com.example.data.local.entity.EvidenceEntity
import com.example.data.local.entity.OfflineQueueEntity
import com.example.data.local.entity.SafetyJourneyEntity
import com.example.data.local.entity.SchoolHandoverEntity
import com.example.data.local.entity.TrustedContactEntity

@Database(
    entities = [
        TrustedContactEntity::class,
        EmergencyEventEntity::class,
        OfflineQueueEntity::class,
        EvidenceEntity::class,
        AuditLogEntity::class,
        ChildProfileEntity::class,
        SafetyJourneyEntity::class,
        SchoolHandoverEntity::class,
        ChildSafetyAlertEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun trustedContactDao(): TrustedContactDao
    abstract fun emergencyEventDao(): EmergencyEventDao
    abstract fun offlineQueueDao(): OfflineQueueDao
    abstract fun evidenceDao(): EvidenceDao
    abstract fun auditLogDao(): AuditLogDao
    abstract fun childSafetyDao(): ChildSafetyDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""CREATE TABLE IF NOT EXISTS `child_profiles` (
                    `id` TEXT NOT NULL, `displayName` TEXT NOT NULL, `ageBand` TEXT NOT NULL,
                    `schoolName` TEXT NOT NULL, `guardianLabel` TEXT NOT NULL, `safePlaceName` TEXT NOT NULL,
                    `safePlaceLatitude` REAL, `safePlaceLongitude` REAL, `geofenceRadiusMeters` INTEGER NOT NULL,
                    `locationSharingEnabled` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))""")
                database.execSQL("""CREATE TABLE IF NOT EXISTS `safety_journeys` (
                    `id` TEXT NOT NULL, `childProfileId` TEXT, `childLabel` TEXT NOT NULL, `originLabel` TEXT NOT NULL,
                    `destinationLabel` TEXT NOT NULL, `startedAt` INTEGER NOT NULL, `expectedArrivalAt` INTEGER NOT NULL,
                    `lastCheckInAt` INTEGER, `lastLatitude` REAL, `lastLongitude` REAL, `status` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))""")
                database.execSQL("""CREATE TABLE IF NOT EXISTS `school_handovers` (
                    `id` TEXT NOT NULL, `childProfileId` TEXT, `childLabel` TEXT NOT NULL, `schoolName` TEXT NOT NULL,
                    `authorizedGuardian` TEXT NOT NULL, `scheduledAt` INTEGER NOT NULL, `status` TEXT NOT NULL,
                    `verificationMethod` TEXT NOT NULL, `oneTimePinHash` TEXT, `pinExpiresAt` INTEGER,
                    `verificationAttempts` INTEGER NOT NULL, `verifiedAt` INTEGER, `createdAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`))""")
                database.execSQL("""CREATE TABLE IF NOT EXISTS `child_safety_alerts` (
                    `id` TEXT NOT NULL, `childProfileId` TEXT, `relatedRecordId` TEXT, `type` TEXT NOT NULL,
                    `severity` TEXT NOT NULL, `title` TEXT NOT NULL, `message` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL, `isRead` INTEGER NOT NULL, PRIMARY KEY(`id`))""")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `child_profiles` ADD COLUMN `locationConsentAt` INTEGER")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `trusted_contacts` ADD COLUMN `isEmailVerified` INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE `trusted_contacts` ADD COLUMN `canShareLocation` INTEGER NOT NULL DEFAULT 1")
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "naari_kavach.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
