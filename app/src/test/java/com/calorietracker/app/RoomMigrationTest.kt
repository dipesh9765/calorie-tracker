package com.calorietracker.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.calorietracker.app.data.local.AppDatabase
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Builds a database exactly as an older app version left it, then opens it with the current
 * [AppDatabase]. Room validates the migrated tables against the entities, so a wrong migration
 * fails here instead of crashing on a user's phone after an update.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomMigrationTest {
  private lateinit var context: Context
  private lateinit var file: File
  private var db: AppDatabase? = null

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    file = context.getDatabasePath("migration-test.db")
    file.parentFile?.mkdirs()
    file.delete()
  }

  @After
  fun tearDown() {
    db?.close()
    file.delete()
  }

  private fun legacyDatabase(version: Int, block: SQLiteDatabase.() -> Unit) {
    SQLiteDatabase.openOrCreateDatabase(file, null).use { sqlite ->
      sqlite.block()
      sqlite.version = version
    }
  }

  private val mealsTable =
    "CREATE TABLE IF NOT EXISTS `meals` (`id` TEXT NOT NULL, `foodName` TEXT NOT NULL, " +
      "`portionDescription` TEXT NOT NULL, `calories` INTEGER NOT NULL, `proteinGrams` REAL NOT NULL, " +
      "`carbsGrams` REAL NOT NULL, `fatGrams` REAL NOT NULL, `mealCategory` TEXT NOT NULL, " +
      "`dateIso` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, PRIMARY KEY(`id`))"

  private fun open(): AppDatabase =
    Room.databaseBuilder(context, AppDatabase::class.java, "migration-test.db")
      .addMigrations(
        AppDatabase.MIGRATION_1_2,
        AppDatabase.MIGRATION_2_3,
        AppDatabase.MIGRATION_3_4
      )
      .allowMainThreadQueries()
      .build()
      .also { db = it }

  @Test
  fun `v3 to v4 keeps existing data and adds the daily log table`() = runBlocking {
    legacyDatabase(3) {
      execSQL(mealsTable)
      execSQL(
        "CREATE TABLE IF NOT EXISTS `memories` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
          "`category` TEXT NOT NULL, `content` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, " +
          "`updatedAt` INTEGER NOT NULL)"
      )
      execSQL(
        "CREATE TABLE IF NOT EXISTS `weights` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
          "`dateIso` TEXT NOT NULL, `weightKg` REAL NOT NULL, `timestamp` INTEGER NOT NULL)"
      )
      execSQL(
        "INSERT INTO meals VALUES ('m1','Poha','1 plate',300,8.0,50.0,6.0,'Breakfast','2026-09-12',1)"
      )
      execSQL("INSERT INTO weights (dateIso, weightKg, timestamp) VALUES ('2026-09-12', 72.5, 1)")
      execSQL(
        "INSERT INTO memories (category, content, createdAt, updatedAt) VALUES ('HABIT','No sugar',1,1)"
      )
    }

    val database = open()

    val meal = database.mealDao().getAllMeals().first().single()
    assertEquals("Poha", meal.foodName)
    assertEquals(300, meal.calories)
    assertEquals(72.5f, database.weightDao().latest(1).single().weightKg, 0.001f)
    assertEquals("No sugar", database.memoryDao().getAll().single().content)

    database.dailyLogDao().setCreatine("2026-09-12", true)
    database.dailyLogDao().addWater("2026-09-12", 500)
    val log = database.dailyLogDao().observe("2026-09-12").first()!!
    assertTrue(log.creatineTaken)
    assertEquals(500, log.waterMl)
  }

  @Test
  fun `v1 to v4 migrates the whole chain`() = runBlocking {
    legacyDatabase(1) {
      execSQL(mealsTable)
      execSQL(
        "INSERT INTO meals VALUES ('m1','Dal','1 bowl',250,14.0,30.0,5.0,'Lunch','2026-09-11',1)"
      )
    }

    val database = open()

    assertEquals("Dal", database.mealDao().getAllMeals().first().single().foodName)
    assertTrue(database.memoryDao().getAll().isEmpty())
    assertTrue(database.weightDao().latest(5).isEmpty())
    assertFalse(database.dailyLogDao().observe("2026-09-11").first()?.creatineTaken ?: false)
  }
}
