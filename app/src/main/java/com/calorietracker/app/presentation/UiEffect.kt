package com.calorietracker.app.presentation

import com.calorietracker.app.domain.model.Meal

/** One-shot events a ViewModel asks the UI to show (toasts, snackbars). */
sealed interface UiEffect {
  data class Message(val text: String, val long: Boolean = false) : UiEffect

  /** A meal was deleted; the UI offers an undo that calls back into the ViewModel. */
  data class MealDeleted(val meal: Meal) : UiEffect
}
