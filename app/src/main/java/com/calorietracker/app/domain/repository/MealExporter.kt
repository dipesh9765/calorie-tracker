package com.calorietracker.app.domain.repository

import com.calorietracker.app.domain.model.Meal
import java.io.File

/** Writes the meal log to a file the user can share. Both methods return null on failure. */
interface MealExporter {
  fun exportToJson(meals: List<Meal>): File?

  fun exportToCsv(meals: List<Meal>): File?
}
