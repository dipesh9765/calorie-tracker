package com.calorietracker.app.domain.usecase

import com.calorietracker.app.domain.model.AiProviderType
import com.calorietracker.app.domain.model.Meal
import com.calorietracker.app.domain.repository.IAiNutritionRepository
import com.calorietracker.app.domain.repository.IMealRepository

/**
 * Domain Use Case encapsulating the business workflow for parsing user natural-language food text
 * using an AI Provider and persisting the structured result into local database storage.
 *
 * Responsibility:
 * 1. Validates input text.
 * 2. Delegates AI parsing to [IAiNutritionRepository] with Nagpur/Indian food prompt context.
 * 3. Persists the parsed [Meal] into local storage via [IMealRepository].
 *
 * @property aiNutritionRepository Remote AI parsing repository interface.
 * @property mealRepository Local meal storage repository interface.
 */
class LogMealWithAiUseCase(
  private val aiNutritionRepository: IAiNutritionRepository,
  private val mealRepository: IMealRepository
) {
  /**
   * Executes the meal logging workflow.
   *
   * @param inputText Natural language text entered by the user (e.g., "2 rotis with chicken
   *   curry").
   * @param provider Configured AI provider enum ([AiProviderType.GEMINI], etc.).
   * @param apiKey User's API key for the chosen provider.
   * @return [Result] containing a [Pair] of the saved [Meal] domain object and AI coach advice
   *   string.
   */
  suspend fun execute(
    inputText: String,
    provider: AiProviderType,
    apiKey: String
  ): Result<Pair<Meal, String>> {
    if (inputText.isBlank()) {
      return Result.failure(IllegalArgumentException("Meal description cannot be empty."))
    }

    val result = aiNutritionRepository.parseMealText(inputText, provider, apiKey)
    return result.onSuccess { (meal, _) -> mealRepository.insertMeal(meal) }
  }
}
