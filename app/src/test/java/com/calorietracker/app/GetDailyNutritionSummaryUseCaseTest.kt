package com.calorietracker.app

import com.calorietracker.app.domain.model.DailyLog
import com.calorietracker.app.domain.usecase.GetDailyNutritionSummaryUseCase
import com.calorietracker.app.support.FakeDailyLogRepository
import com.calorietracker.app.support.FakeMealRepository
import com.calorietracker.app.support.TODAY
import com.calorietracker.app.support.YESTERDAY
import com.calorietracker.app.support.meal
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GetDailyNutritionSummaryUseCaseTest {
  private fun summaries(meals: FakeMealRepository, logs: FakeDailyLogRepository) =
    GetDailyNutritionSummaryUseCase(meals, logs)

  @Test
  fun `no meals gives an empty list`() = runBlocking {
    val result =
      summaries(FakeMealRepository(), FakeDailyLogRepository()).execute(2000, 110f).first()
    assertTrue(result.isEmpty())
  }

  @Test
  fun `meals are grouped by day, summed and sorted oldest first`() = runBlocking {
    val meals =
      FakeMealRepository(
        listOf(
          meal("Poha", TODAY, 300, protein = 8f, carbs = 50f, fat = 6f),
          meal("Dal", TODAY, 250, protein = 14f, carbs = 30f, fat = 5f),
          meal("Biryani", YESTERDAY, 700, protein = 25f, carbs = 90f, fat = 22f)
        )
      )

    val result = summaries(meals, FakeDailyLogRepository()).execute(2000, 110f).first()

    assertEquals(listOf(YESTERDAY, TODAY), result.map { it.dateIso })
    val today = result.last()
    assertEquals(550, today.totalCalories)
    assertEquals(22f, today.totalProtein, 0.001f)
    assertEquals(80f, today.totalCarbs, 0.001f)
    assertEquals(11f, today.totalFat, 0.001f)
    assertEquals(2, today.mealsCount)
  }

  @Test
  fun `targets come from the caller, not hardcoded values`() = runBlocking {
    val meals = FakeMealRepository(listOf(meal()))

    val day = summaries(meals, FakeDailyLogRepository()).execute(1800, 95f).first().single()

    assertEquals(1800, day.targetCalories)
    assertEquals(95f, day.targetProtein, 0.001f)
  }

  @Test
  fun `creatine and water come from the daily log and default to not done`() = runBlocking {
    val meals = FakeMealRepository(listOf(meal(date = TODAY), meal(date = YESTERDAY)))
    val logs = FakeDailyLogRepository()
    logs.logs.value = mapOf(TODAY to DailyLog(TODAY, creatineTaken = true, waterMl = 1500))

    val result = summaries(meals, logs).execute(2000, 110f).first()

    val yesterday = result.first()
    val today = result.last()
    assertFalse(yesterday.creatineTaken)
    assertEquals(0f, yesterday.waterLiters, 0.001f)
    assertTrue(today.creatineTaken)
    assertEquals(1.5f, today.waterLiters, 0.001f)
  }

  @Test
  fun `summaries refresh when a meal changes`() = runBlocking {
    val meals = FakeMealRepository(listOf(meal("Poha", calories = 300, id = "a")))
    val useCase = summaries(meals, FakeDailyLogRepository())
    assertEquals(300, useCase.execute(2000, 110f).first().single().totalCalories)

    meals.insertMeal(meal("Poha", calories = 450, id = "a"))

    assertEquals(450, useCase.execute(2000, 110f).first().single().totalCalories)
  }
}
