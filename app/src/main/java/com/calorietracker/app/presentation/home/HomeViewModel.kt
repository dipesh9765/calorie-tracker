package com.calorietracker.app.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calorietracker.app.data.repository.MemoryRepository
import com.calorietracker.app.domain.model.totals
import com.calorietracker.app.domain.repository.IDailyLogRepository
import com.calorietracker.app.domain.repository.IMealRepository
import com.calorietracker.app.domain.repository.IUserProfileRepository
import com.calorietracker.app.domain.usecase.LogMealWithAiUseCase
import com.calorietracker.app.domain.util.todayIso
import com.calorietracker.app.presentation.UiEffect
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State holder for the Today screen.
 *
 * Exposes one immutable [HomeUiState] built from the profile, today's meals and today's habit log,
 * and turns [HomeUiEvent]s into repository calls. One-shot messages go out through [effects].
 */
@HiltViewModel
class HomeViewModel
@Inject
constructor(
  private val logMealWithAi: LogMealWithAiUseCase,
  private val mealRepository: IMealRepository,
  private val dailyLogRepository: IDailyLogRepository,
  private val memoryRepository: MemoryRepository,
  profileRepository: IUserProfileRepository,
  private val clock: Clock
) : ViewModel() {
  private val profileFlow = profileRepository.profile
  private val aiState = MutableStateFlow(AiState())
  private val effectChannel = Channel<UiEffect>(Channel.BUFFERED)

  val effects: Flow<UiEffect> = effectChannel.receiveAsFlow()

  val uiState: StateFlow<HomeUiState> =
    combine(
        profileFlow,
        mealRepository.getMealsForDate(clock.todayIso()),
        dailyLogRepository.observe(clock.todayIso()),
        aiState
      ) { profile, meals, log, ai ->
        HomeUiState(
          profile = profile,
          todayMeals = meals,
          totals = meals.totals(),
          dailyLog = log,
          coachAdvice = ai.advice,
          isLoadingAi = ai.loading
        )
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HomeUiState())

  fun onEvent(event: HomeUiEvent) {
    when (event) {
      is HomeUiEvent.SubmitMealInput -> submitMeal(event.mealText)
      is HomeUiEvent.DeleteMeal -> deleteMeal(event)
      is HomeUiEvent.UndoDelete -> viewModelScope.launch { mealRepository.insertMeal(event.meal) }
      HomeUiEvent.ToggleCreatine ->
        viewModelScope.launch {
          dailyLogRepository.setCreatine(clock.todayIso(), !uiState.value.dailyLog.creatineTaken)
        }
      is HomeUiEvent.AddWater ->
        viewModelScope.launch { dailyLogRepository.addWater(clock.todayIso(), event.ml) }
    }
  }

  private fun deleteMeal(event: HomeUiEvent.DeleteMeal) {
    viewModelScope.launch {
      mealRepository.deleteMeal(event.meal.id)
      effectChannel.send(UiEffect.MealDeleted(event.meal))
    }
  }

  private fun submitMeal(text: String) {
    if (aiState.value.loading) return
    aiState.update { it.copy(loading = true) }
    viewModelScope.launch {
      val memoryContext =
        MemoryRepository.formatForPrompt(memoryRepository.getAll(), emptyText = "")
      val result = logMealWithAi.execute(text, profileFlow.value, memoryContext)
      aiState.update { it.copy(loading = false) }
      result
        .onSuccess { logged ->
          aiState.update { it.copy(advice = logged.coachAdvice) }
          val verb = if (logged.wasUpdate) "Updated" else "Saved"
          effectChannel.send(
            UiEffect.Message("$verb (${logged.meal.dateIso}): ${logged.meal.foodName}")
          )
        }
        .onFailure { error ->
          effectChannel.send(
            UiEffect.Message("AI Parsing Notice: ${error.localizedMessage}", long = true)
          )
        }
    }
  }

  private data class AiState(val advice: String = "", val loading: Boolean = false)

  private companion object {
    const val STOP_TIMEOUT_MS = 5_000L
  }
}
