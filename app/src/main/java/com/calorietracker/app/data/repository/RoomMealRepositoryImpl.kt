package com.calorietracker.app.data.repository

import com.calorietracker.app.data.local.MealDao
import com.calorietracker.app.data.local.toMealEntity
import com.calorietracker.app.data.local.toMealEntry
import com.calorietracker.app.data.model.MealEntry
import com.calorietracker.app.domain.model.Meal
import com.calorietracker.app.domain.repository.IMealRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Enterprise Room implementation of [IMealRepository] connected to SQLite database DAO.
 */
class RoomMealRepositoryImpl(
    private val mealDao: MealDao
) : IMealRepository {

    override fun getMealsForDate(dateIso: String): Flow<List<Meal>> {
        return mealDao.getMealsForDate(dateIso).map { list ->
            list.map { entity ->
                Meal(
                    id = entity.id,
                    foodName = entity.foodName,
                    portionDescription = entity.portionDescription,
                    calories = entity.calories,
                    proteinGrams = entity.proteinGrams,
                    carbsGrams = entity.carbsGrams,
                    fatGrams = entity.fatGrams,
                    mealCategory = entity.mealCategory,
                    dateIso = entity.dateIso,
                    timestamp = entity.timestamp
                )
            }
        }
    }

    override fun getAllMeals(): Flow<List<Meal>> {
        return mealDao.getAllMeals().map { list ->
            list.map { entity ->
                Meal(
                    id = entity.id,
                    foodName = entity.foodName,
                    portionDescription = entity.portionDescription,
                    calories = entity.calories,
                    proteinGrams = entity.proteinGrams,
                    carbsGrams = entity.carbsGrams,
                    fatGrams = entity.fatGrams,
                    mealCategory = entity.mealCategory,
                    dateIso = entity.dateIso,
                    timestamp = entity.timestamp
                )
            }
        }
    }

    override suspend fun insertMeal(meal: Meal) {
        val entry = MealEntry(
            id = meal.id,
            foodName = meal.foodName,
            portionDescription = meal.portionDescription,
            calories = meal.calories,
            proteinGrams = meal.proteinGrams,
            carbsGrams = meal.carbsGrams,
            fatGrams = meal.fatGrams,
            mealCategory = meal.mealCategory,
            dateIso = meal.dateIso,
            timestamp = meal.timestamp
        )
        mealDao.insertMeal(entry.toMealEntity())
    }

    override suspend fun deleteMeal(mealId: String) {
        mealDao.deleteMealById(mealId)
    }

    override suspend fun clearAll() {
        mealDao.clearAll()
    }
}
