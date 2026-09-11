package com.calorietracker.app.domain.usecase

import com.calorietracker.app.domain.model.DailyNutritionSummary
import com.calorietracker.app.domain.model.Meal
import com.calorietracker.app.domain.repository.IMealRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class GetDailyNutritionSummaryUseCase(
    private val mealRepository: IMealRepository
) {
    fun execute(targetCalories: Int = 2300, targetProtein: Float = 180f): Flow<List<DailyNutritionSummary>> {
        return mealRepository.getAllMeals().map { meals ->
            meals.groupBy { it.dateIso }
                .map { (date, dayMeals) ->
                    DailyNutritionSummary(
                        dateIso = date,
                        totalCalories = dayMeals.sumOf { it.calories },
                        targetCalories = targetCalories,
                        totalProtein = dayMeals.sumOf { it.proteinGrams.toDouble() }.toFloat(),
                        targetProtein = targetProtein,
                        totalCarbs = dayMeals.sumOf { it.carbsGrams.toDouble() }.toFloat(),
                        totalFat = dayMeals.sumOf { it.fatGrams.toDouble() }.toFloat(),
                        creatineTaken = true,
                        mealsCount = dayMeals.size
                    )
                }
                .sortedBy { it.dateIso }
        }
    }
}
