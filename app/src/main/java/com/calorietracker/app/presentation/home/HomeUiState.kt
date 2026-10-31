package com.calorietracker.app.presentation.home

import com.calorietracker.app.data.model.UserProfile
import com.calorietracker.app.domain.model.DailyLog
import com.calorietracker.app.domain.model.Meal
import com.calorietracker.app.domain.model.NutritionTotals

/** Everything the Today screen renders. */
data class HomeUiState(
  val profile: UserProfile = UserProfile(),
  val todayMeals: List<Meal> = emptyList(),
  val totals: NutritionTotals = NutritionTotals(),
  val dailyLog: DailyLog = DailyLog(dateIso = ""),
  val coachAdvice: String = "",
  val isLoadingAi: Boolean = false
)

/** User actions on the Today screen. */
sealed interface HomeUiEvent {
  data class SubmitMealInput(val mealText: String) : HomeUiEvent

  data class DeleteMeal(val meal: Meal) : HomeUiEvent

  data class UndoDelete(val meal: Meal) : HomeUiEvent

  data object ToggleCreatine : HomeUiEvent

  data class AddWater(val ml: Int) : HomeUiEvent
}
