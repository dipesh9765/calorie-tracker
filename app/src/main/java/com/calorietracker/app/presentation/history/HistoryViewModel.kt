package com.calorietracker.app.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calorietracker.app.data.model.UserProfile
import com.calorietracker.app.domain.model.DailyNutritionSummary
import com.calorietracker.app.domain.model.Meal
import com.calorietracker.app.domain.repository.IMealRepository
import com.calorietracker.app.domain.repository.IUserProfileRepository
import com.calorietracker.app.domain.usecase.GetDailyNutritionSummaryUseCase
import com.calorietracker.app.presentation.UiEffect
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the Analytics and Logs screens render. */
data class HistoryUiState(
  val profile: UserProfile = UserProfile(),
  val allMeals: List<Meal> = emptyList(),
  val summaries: List<DailyNutritionSummary> = emptyList()
)

/** State holder for the Analytics and Logs screens. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HistoryViewModel
@Inject
constructor(
  private val mealRepository: IMealRepository,
  getSummaries: GetDailyNutritionSummaryUseCase,
  profileRepository: IUserProfileRepository
) : ViewModel() {
  private val effectChannel = Channel<UiEffect>(Channel.BUFFERED)

  val effects: Flow<UiEffect> = effectChannel.receiveAsFlow()

  // Summaries depend on the targets, so re-query only when a target actually changes.
  private val summaries =
    profileRepository.profile
      .map { it.targetDailyCalories to it.targetDailyProteinGrams }
      .distinctUntilChanged()
      .flatMapLatest { (calories, protein) -> getSummaries.execute(calories, protein) }

  val uiState: StateFlow<HistoryUiState> =
    combine(profileRepository.profile, mealRepository.getAllMeals(), summaries) {
        profile,
        meals,
        daily ->
        HistoryUiState(profile = profile, allMeals = meals, summaries = daily)
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HistoryUiState())

  fun deleteMeal(meal: Meal) {
    viewModelScope.launch {
      mealRepository.deleteMeal(meal.id)
      effectChannel.send(UiEffect.MealDeleted(meal))
    }
  }

  fun undoDelete(meal: Meal) {
    viewModelScope.launch { mealRepository.insertMeal(meal) }
  }

  private companion object {
    const val STOP_TIMEOUT_MS = 5_000L
  }
}
