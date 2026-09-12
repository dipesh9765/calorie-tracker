package com.calorietracker.app.data.repository

import com.calorietracker.app.data.model.MealEntry
import com.calorietracker.app.domain.model.AiProviderType
import com.calorietracker.app.domain.repository.IAiNutritionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Enterprise Repository managing AI meal parsing requests. Uses [IAiNutritionRepository] and
 * multi-provider adapters under the hood.
 */
class AiRepository(
  private val aiNutritionRepository: IAiNutritionRepository = AiNutritionRepositoryImpl()
) {

  suspend fun parseMealText(
    inputText: String,
    provider: String,
    apiKey: String,
    modelName: String = "",
    todayContext: String = "",
    yesterdayContext: String = ""
  ): Result<MealParseResult> =
    withContext(Dispatchers.IO) {
      val providerType = AiProviderType.fromString(provider)
      val result =
        aiNutritionRepository.parseMealText(
          mealText = inputText,
          provider = providerType,
          apiKey = apiKey,
          modelName = modelName,
          todayContext = todayContext,
          yesterdayContext = yesterdayContext
        )

      result.fold(
        onSuccess = { (meal, coachAdvice) ->
          val mealEntry =
            MealEntry(
              foodName = meal.foodName,
              portionDescription = meal.portionDescription,
              calories = meal.calories,
              proteinGrams = meal.proteinGrams,
              carbsGrams = meal.carbsGrams,
              fatGrams = meal.fatGrams,
              mealCategory = meal.mealCategory,
              dateIso = meal.dateIso
            )
          Result.success(MealParseResult(mealEntry, coachAdvice, meal.action))
        },
        onFailure = { error -> Result.failure(error) }
      )
    }
}

data class MealParseResult(
  val mealEntry: MealEntry,
  val coachAdvice: String,
  val action: String = "CREATE"
)
