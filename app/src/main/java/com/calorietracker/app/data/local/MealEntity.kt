package com.calorietracker.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.calorietracker.app.data.model.MealEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Room Database Entity for local meal persistence.
 */
@Entity(tableName = "meals")
data class MealEntity(
    @PrimaryKey
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

fun MealEntity.toMealEntry(): MealEntry {
    return MealEntry(
        id = id,
        foodName = foodName,
        portionDescription = portionDescription,
        calories = calories,
        proteinGrams = proteinGrams,
        carbsGrams = carbsGrams,
        fatGrams = fatGrams,
        mealCategory = mealCategory,
        dateIso = dateIso,
        timestamp = timestamp
    )
}

fun MealEntry.toMealEntity(): MealEntity {
    return MealEntity(
        id = id,
        foodName = foodName,
        portionDescription = portionDescription,
        calories = calories,
        proteinGrams = proteinGrams,
        carbsGrams = carbsGrams,
        fatGrams = fatGrams,
        mealCategory = mealCategory,
        dateIso = dateIso,
        timestamp = timestamp
    )
}
