package com.calorietracker.app.domain.usecase

import com.calorietracker.app.data.model.UserProfile
import com.calorietracker.app.domain.model.Meal
import com.calorietracker.app.domain.repository.IAiNutritionRepository
import com.calorietracker.app.domain.repository.IMealRepository
import com.calorietracker.app.domain.util.daysAgoIso
import com.calorietracker.app.domain.util.todayIso
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/** A meal that was written to the log, with the coach's reply and whether it replaced a meal. */
data class LoggedMeal(val meal: Meal, val coachAdvice: String, val wasUpdate: Boolean)

/**
 * Logs a meal described in natural language.
 * 1. Rejects blank input.
 * 2. Builds today's and yesterday's context so the model can resolve phrases like "yesterday".
 * 3. Asks the configured provider to estimate the meal.
 * 4. Saves it. A new meal is always inserted as a new row; only an explicit AI `UPDATE` replaces an
 *    existing meal with the same name on the same day, so repeating "2 rotis" never overwrites the
 *    earlier entry.
 */
class LogMealWithAiUseCase
@Inject
constructor(
  private val aiRepository: IAiNutritionRepository,
  private val mealRepository: IMealRepository,
  private val clock: Clock
) {
  suspend fun execute(
    inputText: String,
    profile: UserProfile,
    memoryContext: String = ""
  ): Result<LoggedMeal> {
    if (inputText.isBlank()) {
      return Result.failure(IllegalArgumentException("Meal description cannot be empty."))
    }

    val today = mealRepository.getMealsForDate(clock.todayIso()).first()
    val yesterday = mealRepository.getMealsForDate(clock.daysAgoIso(1)).first()

    val parsed =
      aiRepository.parseMealText(
        mealText = inputText,
        provider = profile.provider,
        apiKey = profile.activeApiKey,
        modelName = profile.activeModel,
        todayContext = MealContextFormatter.describe(today, "No meals logged yet today."),
        yesterdayContext =
          MealContextFormatter.describe(yesterday, "No meals logged for yesterday."),
        memoryContext = memoryContext,
        profile = profile
      )

    return parsed.map { (meal, advice) ->
      val isUpdate = meal.action.equals(UPDATE_ACTION, ignoreCase = true)
      val existing =
        if (isUpdate) mealRepository.findByDateAndName(meal.dateIso, meal.foodName) else null
      val toSave = if (existing != null) meal.copy(id = existing.id) else meal
      mealRepository.insertMeal(toSave)
      LoggedMeal(meal = toSave, coachAdvice = advice, wasUpdate = isUpdate)
    }
  }

  private companion object {
    const val UPDATE_ACTION = "UPDATE"
  }
}
