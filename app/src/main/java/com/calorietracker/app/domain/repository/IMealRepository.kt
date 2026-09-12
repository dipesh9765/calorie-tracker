package com.calorietracker.app.domain.repository

import com.calorietracker.app.domain.model.Meal
import kotlinx.coroutines.flow.Flow

interface IMealRepository {
  fun getMealsForDate(dateIso: String): Flow<List<Meal>>

  fun getAllMeals(): Flow<List<Meal>>

  suspend fun insertMeal(meal: Meal)

  suspend fun deleteMeal(mealId: String)

  suspend fun clearAll()
}
