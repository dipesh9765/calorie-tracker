package com.calorietracker.app.data.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class MealEntry(
  val id: String = java.util.UUID.randomUUID().toString(),
  val foodName: String,
  val portionDescription: String,
  val calories: Int,
  val proteinGrams: Float,
  val carbsGrams: Float,
  val fatGrams: Float,
  val mealCategory: String = "Meal", // Breakfast, Lunch, Snack, Dinner, Supplement
  val dateIso: String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()),
  val timestamp: Long = System.currentTimeMillis()
)
