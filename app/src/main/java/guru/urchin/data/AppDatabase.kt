package guru.urchin.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
  entities = [
    DeviceEntity::class,
    SightingEntity::class,
    AlertRuleEntity::class,
    AffinityGroupEntity::class,
    AffinityGroupMemberEntity::class,
    AffinityImportLogEntity::class,
    CorrelationEntity::class
  ],
  version = 11,
  exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
  abstract fun deviceDao(): DeviceDao
  abstract fun sightingDao(): SightingDao
  abstract fun alertRuleDao(): AlertRuleDao
  abstract fun affinityGroupDao(): AffinityGroupDao
  abstract fun correlationDao(): CorrelationDao

  companion object {
    val MIGRATION_1_2 = object : Migration(1, 2) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE devices ADD COLUMN protocolType TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE sightings ADD COLUMN protocolType TEXT DEFAULT NULL")
      }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS index_devices_lastSeen ON devices(lastSeen)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_devices_protocolType_lastSeen ON devices(protocolType, lastSeen)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_sightings_protocolType_timestamp ON sightings(protocolType, timestamp)")
      }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
          "CREATE TABLE IF NOT EXISTS alert_rules (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "matchType TEXT NOT NULL, " +
            "matchPattern TEXT NOT NULL, " +
            "displayValue TEXT NOT NULL, " +
            "emoji TEXT NOT NULL, " +
            "soundPreset TEXT NOT NULL, " +
            "enabled INTEGER NOT NULL, " +
            "createdAt INTEGER NOT NULL)"
        )
      }
    }

    private val MIGRATION_4_5 = object : Migration(4, 5) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE devices ADD COLUMN sharedFromGroupIds TEXT DEFAULT NULL")
      }
    }

    private val MIGRATION_5_6 = object : Migration(5, 6) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
          """
          CREATE TABLE IF NOT EXISTS `affinity_groups` (
            `groupId` TEXT NOT NULL,
            `groupName` TEXT NOT NULL,
            `createdAt` INTEGER NOT NULL,
            `myMemberId` TEXT NOT NULL,
            `myDisplayName` TEXT NOT NULL,
            `groupKeyWrapped` TEXT NOT NULL,
            `keyEpoch` INTEGER NOT NULL,
            `sharingConfigJson` TEXT NOT NULL,
            PRIMARY KEY(`groupId`)
          )
          """.trimIndent()
        )
        db.execSQL(
          """
          CREATE TABLE IF NOT EXISTS `affinity_group_members` (
            `groupId` TEXT NOT NULL,
            `memberId` TEXT NOT NULL,
            `displayName` TEXT NOT NULL,
            `joinedAt` INTEGER NOT NULL,
            `lastSeenEpoch` INTEGER NOT NULL,
            `publicKeyBase64` TEXT,
            `revoked` INTEGER NOT NULL,
            PRIMARY KEY(`groupId`, `memberId`)
          )
          """.trimIndent()
        )
        db.execSQL(
          """
          CREATE TABLE IF NOT EXISTS `affinity_import_log` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `groupId` TEXT NOT NULL,
            `senderId` TEXT NOT NULL,
            `exportTimestamp` INTEGER NOT NULL,
            `importedAt` INTEGER NOT NULL,
            `itemCounts` TEXT NOT NULL
          )
          """.trimIndent()
        )
        db.execSQL(
          """
          CREATE UNIQUE INDEX IF NOT EXISTS `index_affinity_import_log_groupId_senderId_exportTimestamp`
          ON `affinity_import_log` (`groupId`, `senderId`, `exportTimestamp`)
          """.trimIndent()
        )
      }
    }

    private val MIGRATION_6_7 = object : Migration(6, 7) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
          "ALTER TABLE `affinity_groups` ADD COLUMN `requireEcdh` INTEGER NOT NULL DEFAULT 0"
        )
      }
    }

    private val MIGRATION_7_8 = object : Migration(7, 8) {
      override fun migrate(db: SupportSQLiteDatabase) {
        // SQLCipher migration is handled externally by DatabaseMigrationHelper;
        // this is a no-op version bump to align with the encrypted schema.
      }
    }

    private val MIGRATION_8_9 = object : Migration(8, 9) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE sightings ADD COLUMN receiverLat REAL DEFAULT NULL")
        db.execSQL("ALTER TABLE sightings ADD COLUMN receiverLon REAL DEFAULT NULL")
      }
    }

    private val MIGRATION_9_10 = object : Migration(9, 10) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
          """
          CREATE TABLE IF NOT EXISTS `correlations` (
            `deviceKeyA` TEXT NOT NULL,
            `deviceKeyB` TEXT NOT NULL,
            `correlationType` TEXT NOT NULL,
            `confidence` REAL NOT NULL,
            `coOccurrences` INTEGER NOT NULL,
            `firstCorrelated` INTEGER NOT NULL,
            `lastCorrelated` INTEGER NOT NULL,
            PRIMARY KEY(`deviceKeyA`, `deviceKeyB`)
          )
          """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_correlations_deviceKeyA ON correlations(deviceKeyA)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_correlations_deviceKeyB ON correlations(deviceKeyB)")
      }
    }

    private val MIGRATION_10_11 = object : Migration(10, 11) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE alert_rules ADD COLUMN rssiThreshold INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE alert_rules ADD COLUMN absenceMinutes INTEGER DEFAULT NULL")
      }
    }

    fun build(context: Context): AppDatabase {
      val passphrase = DatabaseKeyManager.getOrCreatePassphrase(context)
      val openMode = DatabaseMigrationHelper.prepareOpenMode(context, passphrase)
      val builder = Room.databaseBuilder(
        context.applicationContext,
        AppDatabase::class.java,
        "urchin.db"
      )
        .addMigrations(
          MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
          MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9,
          MIGRATION_9_10, MIGRATION_10_11
        )
        .fallbackToDestructiveMigration()
      if (openMode == DatabaseMigrationHelper.OpenMode.ENCRYPTED) {
        System.loadLibrary("sqlcipher")
        builder.openHelperFactory(SupportOpenHelperFactory(passphrase))
      }
      return builder.build()
    }
  }
}
