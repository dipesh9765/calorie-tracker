package com.calorietracker.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.calorietracker.app.data.local.AppDatabase
import com.calorietracker.app.data.local.toEntity
import com.calorietracker.app.data.repository.RoomDailyLogRepository
import com.calorietracker.app.data.repository.RoomMealRepositoryImpl
import com.calorietracker.app.support.TODAY
import com.calorietracker.app.support.YESTERDAY
import com.calorietracker.app.support.meal
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Runs the real Room-generated DAOs against an in-memory SQLite database. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomDaoTest {
  private lateinit var db: AppDatabase
  private lateinit var meals: RoomMealRepositoryImpl
  private lateinit var logs: RoomDailyLogRepository

  @Before
  fun open() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    db =
      Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    meals = RoomMealRepositoryImpl(db.mealDao())
    logs = RoomDailyLogRepository(db.dailyLogDao())
  }

  @After fun close() = db.close()

  @Test
  fun `a meal round trips through SQLite`() = runBlocking {
    val poha = meal("Poha", TODAY, 300, protein = 8f, id = "p1")
    meals.insertMeal(poha)

    assertEquals(poha.copy(action = "CREATE"), meals.getAllMeals().first().single())
  }

  @Test
  fun `meals are filtered by date and ordered newest first`() = runBlocking {
    meals.insertMeal(meal("Old", YESTERDAY, id = "1"))
    meals.insertMeal(meal("New", TODAY, id = "2"))

    assertEquals(listOf("New"), meals.getMealsForDate(TODAY).first().map { it.foodName })
    assertEquals(listOf("New", "Old"), meals.getAllMeals().first().map { it.foodName })
  }

  @Test
  fun `inserting the same id replaces the row`() = runBlocking {
    meals.insertMeal(meal("Poha", calories = 300, id = "p1"))
    meals.insertMeal(meal("Poha", calories = 450, id = "p1"))

    assertEquals(450, meals.getAllMeals().first().single().calories)
  }

  @Test
  fun `findByDateAndName ignores case and is scoped to the day`() = runBlocking {
    meals.insertMeal(meal("Paneer Tikka", TODAY, id = "t"))
    meals.insertMeal(meal("Paneer Tikka", YESTERDAY, id = "y"))

    assertEquals("t", meals.findByDateAndName(TODAY, "paneer tikka")?.id)
    assertEquals("y", meals.findByDateAndName(YESTERDAY, "PANEER TIKKA")?.id)
    assertNull(meals.findByDateAndName(TODAY, "Dosa"))
  }

  @Test
  fun `delete removes one meal and clearAll removes the rest`() = runBlocking {
    meals.insertMeal(meal("A", id = "a"))
    meals.insertMeal(meal("B", id = "b"))

    meals.deleteMeal("a")
    assertEquals(listOf("b"), meals.getAllMeals().first().map { it.id })

    meals.clearAll()
    assertTrue(meals.getAllMeals().first().isEmpty())
  }

  @Test
  fun `queries emit again when the table changes`() = runBlocking {
    meals.getAllMeals().test {
      assertTrue(awaitItem().isEmpty())
      meals.insertMeal(meal("Poha", id = "p1"))
      assertEquals(1, awaitItem().size)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `a day with no log reads as an empty default`() = runBlocking {
    val log = logs.observe(TODAY).first()
    assertFalse(log.creatineTaken)
    assertEquals(0, log.waterMl)
  }

  @Test
  fun `creatine and water are stored per day and accumulate`() = runBlocking {
    logs.setCreatine(TODAY, true)
    logs.addWater(TODAY, 250)
    logs.addWater(TODAY, 500)
    logs.addWater(YESTERDAY, 1000)

    val today = logs.observe(TODAY).first()
    assertTrue(today.creatineTaken)
    assertEquals(750, today.waterMl)
    assertEquals(1000, logs.observe(YESTERDAY).first().waterMl)
    assertEquals(setOf(TODAY, YESTERDAY), logs.observeAll().first().keys)
  }

  @Test
  fun `water never goes below zero and creatine can be turned off`() = runBlocking {
    logs.addWater(TODAY, 300)
    logs.addWater(TODAY, -1000)
    assertEquals(0, logs.observe(TODAY).first().waterMl)

    logs.setCreatine(TODAY, true)
    logs.setCreatine(TODAY, false)
    assertFalse(logs.observe(TODAY).first().creatineTaken)
  }

  @Test
  fun `entity mapping keeps the id used for undo`() = runBlocking {
    val original = meal("Dal", id = "same-id")
    meals.insertMeal(original)
    meals.deleteMeal(original.id)
    assertNull(meals.findByDateAndName(TODAY, "Dal"))

    db.mealDao().insertMeal(original.toEntity())

    assertNotNull(meals.findByDateAndName(TODAY, "Dal"))
    assertEquals("same-id", meals.findByDateAndName(TODAY, "Dal")?.id)
  }
}
