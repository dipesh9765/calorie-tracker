package com.calorietracker.app.domain.usecase

import com.calorietracker.app.domain.model.DailyLog
import com.calorietracker.app.domain.model.DailyNutritionSummary
import com.calorietracker.app.domain.model.totals
import com.calorietracker.app.domain.repository.IDailyLogRepository
import com.calorietracker.app.domain.repository.IMealRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Per-day totals against the user's targets, newest data last, for charts and history. */
class GetDailyNutritionSummaryUseCase
@Inject
constructor(
  private val mealRepository: IMealRepository,
  private val dailyLogRepository: IDailyLogRepository
) {
  fun execute(targetCalories: Int, targetProtein: Float): Flow<List<DailyNutritionSummary>> =
    combine(mealRepository.getAllMeals(), dailyLogRepository.observeAll()) { meals, logs ->
      meals
        .groupBy { it.dateIso }
        .map { (date, dayMeals) ->
          val totals = dayMeals.totals()
          val log = logs[date] ?: DailyLog(date)
          DailyNutritionSummary(
            dateIso = date,
            totalCalories = totals.calories,
            targetCalories = targetCalories,
            totalProtein = totals.protein,
            targetProtein = targetProtein,
            totalCarbs = totals.carbs,
            totalFat = totals.fat,
            creatineTaken = log.creatineTaken,
            mealsCount = dayMeals.size,
            waterLiters = log.waterLiters
          )
        }
        .sortedBy { it.dateIso }
    }
}
