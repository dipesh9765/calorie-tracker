package com.calorietracker.app.data.model

data class DailySummary(
  val dateIso: String,
  val totalCalories: Int,
  val targetCalories: Int = 2300,
  val totalProtein: Float,
  val targetProtein: Float = 180f,
  val totalCarbs: Float,
  val totalFat: Float,
  val creatineTaken: Boolean = false,
  val waterLiters: Float = 0f,
  val mealsCount: Int = 0
)
