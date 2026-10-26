package com.calorietracker.app.domain.repository

import com.calorietracker.app.domain.model.Meal
import kotlinx.coroutines.flow.Flow

/** Local meal log. */
interface IMealRepository {
  fun getMealsForDate(dateIso: String): Flow<List<Meal>>

  fun getAllMeals(): Flow<List<Meal>>

  /** The meal logged on [dateIso] with this name (case-insensitive), used to resolve AI updates. */
  suspend fun findByDateAndName(dateIso: String, foodName: String): Meal?

  suspend fun insertMeal(meal: Meal)

  suspend fun deleteMeal(mealId: String)

  suspend fun clearAll()
}
