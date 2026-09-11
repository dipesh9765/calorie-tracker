package com.calorietracker.app.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Room Data Access Object (DAO) for executing meal CRUD queries against SQLite database.
 */
@Dao
interface MealDao {

    @Query("SELECT * FROM meals WHERE dateIso = :dateIso ORDER BY timestamp DESC")
    fun getMealsForDate(dateIso: String): Flow<List<MealEntity>>

    @Query("SELECT * FROM meals ORDER BY dateIso DESC, timestamp DESC")
    fun getAllMeals(): Flow<List<MealEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMeal(meal: MealEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(meals: List<MealEntity>)

    @Query("DELETE FROM meals WHERE id = :mealId")
    suspend fun deleteMealById(mealId: String)

    @Query("DELETE FROM meals")
    suspend fun clearAll()
}
