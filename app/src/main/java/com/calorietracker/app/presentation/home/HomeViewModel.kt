package com.calorietracker.app.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calorietracker.app.domain.model.AiProviderType
import com.calorietracker.app.domain.repository.IMealRepository
import com.calorietracker.app.domain.repository.IUserProfileRepository
import com.calorietracker.app.domain.usecase.LogMealWithAiUseCase
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Enterprise ViewModel for the Home / Today Dashboard screen.
 *
 * Responsibilities:
 * - Manages Unidirectional Data Flow (UDF) via immutable [StateFlow] emitting [HomeUiState].
 * - Handles UI events via [onEvent].
 * - Observes reactive meal streams from [IMealRepository].
 * - Triggers [LogMealWithAiUseCase] for natural language meal parsing.
 *
 * @property logMealWithAiUseCase Domain Use Case executing AI meal logging.
 * @property mealRepository Local meal data repository.
 * @property userProfileRepository User settings & API key repository.
 */
class HomeViewModel(
    private val logMealWithAiUseCase: LogMealWithAiUseCase,
    private val mealRepository: IMealRepository,
    private val userProfileRepository: IUserProfileRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    
    /** Public read-only StateFlow emitting UI state updates to Compose views. */
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val todayIso = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    init {
        observeMeals()
        observeProfile()
    }

    /** Observes today's meal list and updates calorie & macro totals. */
    private fun observeMeals() {
        viewModelScope.launch {
            mealRepository.getMealsForDate(todayIso).collect { meals ->
                _uiState.update { current ->
                    current.copy(
                        todayMeals = meals,
                        todayTotalCalories = meals.sumOf { it.calories },
                        todayTotalProtein = meals.sumOf { it.proteinGrams.toDouble() }.toFloat(),
                        todayTotalCarbs = meals.sumOf { it.carbsGrams.toDouble() }.toFloat(),
                        todayTotalFat = meals.sumOf { it.fatGrams.toDouble() }.toFloat()
                    )
                }
            }
        }
    }

    /** Observes user profile changes (targets, API keys, provider settings). */
    private fun observeProfile() {
        viewModelScope.launch {
            userProfileRepository.getUserProfile().collect { profile ->
                _uiState.update { current -> current.copy(userProfile = profile) }
            }
        }
    }

    /**
     * Single entry point for user interaction events sent from the Compose UI.
     *
     * @param event The [HomeUiEvent] action dispatched by the user.
     */
    fun onEvent(event: HomeUiEvent) {
        when (event) {
            is HomeUiEvent.SubmitMealInput -> submitMeal(event.mealText)
            HomeUiEvent.ToggleCreatine -> {
                _uiState.update { it.copy(creatineTaken = !it.creatineTaken) }
            }
            HomeUiEvent.DismissError -> {
                _uiState.update { it.copy(errorMessage = null) }
            }
        }
    }

    /** Submits a natural language meal text for AI parsing & persistence. */
    private fun submitMeal(mealText: String) {
        val currentState = _uiState.value
        val provider = AiProviderType.fromString(currentState.userProfile.primaryAiProvider)
        val apiKey = when (provider) {
            AiProviderType.GEMINI -> currentState.userProfile.geminiApiKey
            AiProviderType.OPENAI -> currentState.userProfile.openAiApiKey
            AiProviderType.CLAUDE -> currentState.userProfile.claudeApiKey
            AiProviderType.DEEPSEEK -> currentState.userProfile.deepSeekApiKey
        }

        _uiState.update { it.copy(isLoadingAi = true, errorMessage = null) }

        viewModelScope.launch {
            val result = logMealWithAiUseCase.execute(mealText, provider, apiKey)
            _uiState.update { current ->
                result.fold(
                    onSuccess = { (_, advice) ->
                        current.copy(isLoadingAi = false, coachAdvice = advice)
                    },
                    onFailure = { error ->
                        current.copy(isLoadingAi = false, errorMessage = error.localizedMessage)
                    }
                )
            }
        }
    }
}
