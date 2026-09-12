package com.calorietracker.app.domain.model

/**
 * Aggregated daily nutrition summary model used by Analytics & Charting UI components.
 *
 * @property dateIso Target date in ISO 8601 format (YYYY-MM-DD).
 * @property totalCalories Aggregated calories consumed on this date.
 * @property targetCalories User's daily calorie deficit target (e.g. 2,300 kcal).
 * @property totalProtein Total protein consumed in grams.
 * @property targetProtein Target protein goal in grams (e.g. 180 g).
 * @property totalCarbs Total carbohydrates consumed in grams.
 * @property totalFat Total fat consumed in grams.
 * @property creatineTaken Flag indicating whether the 5g daily creatine monohydrate dose was
 *   completed.
 * @property mealsCount Total number of individual meals recorded on this date.
 */
data class DailyNutritionSummary(
  val dateIso: String,
  val totalCalories: Int,
  val targetCalories: Int,
  val totalProtein: Float,
  val targetProtein: Float,
  val totalCarbs: Float,
  val totalFat: Float,
  val creatineTaken: Boolean,
  val mealsCount: Int
)
