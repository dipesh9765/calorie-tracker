package com.calorietracker.app.domain.usecase

import com.calorietracker.app.domain.model.Meal
import com.calorietracker.app.domain.model.totals

/** Turns a day's meals into the one-line context the AI prompts expect. */
object MealContextFormatter {
  fun describe(meals: List<Meal>, empty: String): String {
    if (meals.isEmpty()) return empty
    val totals = meals.totals()
    val foods =
      meals.joinToString("; ") {
        "${it.foodName} (${it.calories} kcal, ${it.proteinGrams}g protein)"
      }
    return "Total: ${totals.calories} kcal, ${totals.protein}g protein, ${totals.carbs}g carbs, " +
      "${totals.fat}g fat. Foods logged: $foods"
  }
}
