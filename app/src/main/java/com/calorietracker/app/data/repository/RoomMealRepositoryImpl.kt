package com.calorietracker.app.data.repository

import com.calorietracker.app.data.local.MealDao
import com.calorietracker.app.data.local.toDomain
import com.calorietracker.app.data.local.toEntity
import com.calorietracker.app.domain.model.Meal
import com.calorietracker.app.domain.repository.IMealRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Room-backed implementation of [IMealRepository]. */
@Singleton
class RoomMealRepositoryImpl @Inject constructor(private val mealDao: MealDao) : IMealRepository {
  override fun getMealsForDate(dateIso: String): Flow<List<Meal>> =
    mealDao.getMealsForDate(dateIso).map { list -> list.map { it.toDomain() } }

  override fun getAllMeals(): Flow<List<Meal>> =
    mealDao.getAllMeals().map { list -> list.map { it.toDomain() } }

  override suspend fun findByDateAndName(dateIso: String, foodName: String): Meal? =
    mealDao.findByDateAndName(dateIso, foodName)?.toDomain()

  override suspend fun insertMeal(meal: Meal) = mealDao.insertMeal(meal.toEntity())

  override suspend fun deleteMeal(mealId: String) = mealDao.deleteMealById(mealId)

  override suspend fun clearAll() = mealDao.clearAll()
}
