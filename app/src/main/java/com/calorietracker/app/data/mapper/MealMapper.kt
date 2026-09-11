package com.calorietracker.app.data.mapper

import com.calorietracker.app.data.model.MealEntry
import com.calorietracker.app.domain.model.Meal

fun MealEntry.toDomain(): Meal {
    return Meal(
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

fun Meal.toEntity(): MealEntry {
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
