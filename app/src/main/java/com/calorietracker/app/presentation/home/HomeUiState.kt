package com.calorietracker.app.presentation.home

import com.calorietracker.app.data.model.UserProfile
import com.calorietracker.app.domain.model.Meal

data class HomeUiState(
  val userProfile: UserProfile = UserProfile(),
  val todayMeals: List<Meal> = emptyList(),
  val todayTotalCalories: Int = 0,
  val todayTotalProtein: Float = 0f,
  val todayTotalCarbs: Float = 0f,
  val todayTotalFat: Float = 0f,
  val creatineTaken: Boolean = true,
  val coachAdvice: String = "",
  val isLoadingAi: Boolean = false,
  val errorMessage: String? = null
)

sealed interface HomeUiEvent {
  data class SubmitMealInput(val mealText: String) : HomeUiEvent

  data object ToggleCreatine : HomeUiEvent

  data object DismissError : HomeUiEvent
}
