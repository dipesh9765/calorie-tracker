package com.calorietracker.app.data.local

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Main Room SQLite Database singleton instance. */
@Database(
  entities = [MealEntity::class, MemoryEntity::class, WeightEntity::class, DailyLogEntity::class],
  version = 4,
  exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

  abstract fun mealDao(): MealDao

  abstract fun memoryDao(): MemoryDao

  abstract fun weightDao(): WeightDao

  abstract fun dailyLogDao(): DailyLogDao

  companion object {
    @Volatile private var INSTANCE: AppDatabase? = null

    /** v3 -> v4: adds per-day creatine and water tracking. */
    val MIGRATION_3_4 =
      object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
          db.execSQL(
            "CREATE TABLE IF NOT EXISTS `daily_logs` (`dateIso` TEXT NOT NULL, " +
              "`creatineTaken` INTEGER NOT NULL, `waterMl` INTEGER NOT NULL, PRIMARY KEY(`dateIso`))"
          )
        }
      }

    /** v2 -> v3: adds the weight history table. */
    val MIGRATION_2_3 =
      object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
          db.execSQL(
            "CREATE TABLE IF NOT EXISTS `weights` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
              "`dateIso` TEXT NOT NULL, `weightKg` REAL NOT NULL, `timestamp` INTEGER NOT NULL)"
          )
        }
      }

    /** v1 -> v2: adds the dietitian memory table; existing meals are untouched. */
    val MIGRATION_1_2 =
      object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
          db.execSQL(
            "CREATE TABLE IF NOT EXISTS `memories` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
              "`category` TEXT NOT NULL, `content` TEXT NOT NULL, " +
              "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)"
          )
        }
      }

    /** Closes and forgets the singleton so each test starts from a fresh database. */
    @VisibleForTesting
    fun resetInstance() {
      synchronized(this) {
        INSTANCE?.close()
        INSTANCE = null
      }
    }

    fun getInstance(context: Context): AppDatabase {
      return INSTANCE
        ?: synchronized(this) {
          val instance =
            Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "calorie_tracker_db"
              )
              .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
              .build()
          INSTANCE = instance
          instance
        }
    }
  }
}
