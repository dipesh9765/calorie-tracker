package com.calorietracker.app.domain.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Domain model representing an individual food/meal log entry.
 *
 * @property id Unique identifier for the meal entry.
 * @property foodName Name or summary title of the consumed food items.
 * @property portionDescription Human-readable portion size (e.g., "2 rotis + 200g chicken").
 * @property calories Total estimated energy content in kilocalories (kcal).
 * @property proteinGrams Total protein content in grams.
 * @property carbsGrams Total carbohydrate content in grams.
 * @property fatGrams Total fat content in grams.
 * @property mealCategory Classification of meal (Breakfast, Lunch, Snack, Dinner, Supplement).
 * @property dateIso Date of consumption in ISO 8601 format (YYYY-MM-DD).
 * @property timestamp Unix epoch timestamp in milliseconds when the meal was logged.
 */
data class Meal(
    val id: String = java.util.UUID.randomUUID().toString(),
    val foodName: String,
    val portionDescription: String,
    val calories: Int,
    val proteinGrams: Float,
    val carbsGrams: Float,
    val fatGrams: Float,
    val mealCategory: String = "Meal",
    val dateIso: String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()),
    val timestamp: Long = System.currentTimeMillis()
)
