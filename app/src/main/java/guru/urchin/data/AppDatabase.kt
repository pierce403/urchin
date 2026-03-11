package guru.urchin.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
  entities = [
    DeviceEntity::class,
    SightingEntity::class,
    AlertRuleEntity::class
  ],
  version = 4,
  exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
  abstract fun deviceDao(): DeviceDao
  abstract fun sightingDao(): SightingDao
  abstract fun alertRuleDao(): AlertRuleDao

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

    fun build(context: Context): AppDatabase {
      return Room.databaseBuilder(
        context.applicationContext,
        AppDatabase::class.java,
        "urchin.db"
      )
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
        .fallbackToDestructiveMigration()
        .build()
    }
  }
}
