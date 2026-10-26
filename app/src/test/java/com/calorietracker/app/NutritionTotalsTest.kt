package com.calorietracker.app

import com.calorietracker.app.domain.model.MacroTargets
import com.calorietracker.app.domain.model.Meal
import com.calorietracker.app.domain.model.totals
import com.calorietracker.app.support.meal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NutritionTotalsTest {
  @Test
  fun `totals of nothing are zero`() {
    val totals = emptyList<Meal>().totals()
    assertEquals(0, totals.calories)
    assertEquals(0f, totals.protein, 0.0001f)
  }

  @Test
  fun `totals sum calories and macros`() {
    val totals =
      listOf(
          meal(calories = 200, protein = 10f, carbs = 20f, fat = 5f),
          meal(calories = 300, protein = 15f, carbs = 40f, fat = 10f)
        )
        .totals()
    assertEquals(500, totals.calories)
    assertEquals(25f, totals.protein, 0.001f)
    assertEquals(60f, totals.carbs, 0.001f)
    assertEquals(15f, totals.fat, 0.001f)
  }

  @Test
  fun `macro targets split the calories that protein leaves behind`() {
    val targets = MacroTargets.from(calories = 2000, proteinGrams = 110f)
    // fat 25% of 2000 kcal = 500 kcal = 55.6 g; carbs = (2000 - 440 - 500) / 4 = 265 g
    assertEquals(55.6f, targets.fatGrams, 0.1f)
    assertEquals(265f, targets.carbsGrams, 0.1f)
  }

  @Test
  fun `macro targets never go negative when protein alone exceeds the calories`() {
    val targets = MacroTargets.from(calories = 800, proteinGrams = 250f)
    assertTrue(targets.carbsGrams >= 0f)
  }
}
