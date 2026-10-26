package com.calorietracker.app.notification

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.calorietracker.app.data.repository.MemoryRepository
import com.calorietracker.app.domain.model.totals
import com.calorietracker.app.domain.repository.IAiNutritionRepository
import com.calorietracker.app.domain.repository.IMealRepository
import com.calorietracker.app.domain.repository.IUserProfileRepository
import com.calorietracker.app.domain.usecase.MealContextFormatter
import com.calorietracker.app.domain.util.daysAgoIso
import com.calorietracker.app.domain.util.todayIso
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.IOException
import java.time.Clock
import java.time.LocalTime
import kotlinx.coroutines.flow.first

/** Posts a personalised nutrition nudge every couple of hours during the day. */
@HiltWorker
class CoachNotificationWorker
@AssistedInject
constructor(
  @Assisted private val context: Context,
  @Assisted params: WorkerParameters,
  private val aiRepository: IAiNutritionRepository,
  private val mealRepository: IMealRepository,
  private val memoryRepository: MemoryRepository,
  private val profileRepository: IUserProfileRepository,
  private val clock: Clock
) : CoroutineWorker(context, params) {

  override suspend fun doWork(): Result {
    val hour = LocalTime.now(clock).hour
    if (hour !in ACTIVE_HOURS) return Result.success()

    val profile = profileRepository.profile.value
    if (!profile.notificationsEnabled) return Result.success()

    return try {
      val today = mealRepository.getMealsForDate(clock.todayIso()).first()
      val yesterday = mealRepository.getMealsForDate(clock.daysAgoIso(1)).first()
      val todayTotals = today.totals()

      val advice =
        aiRepository.generateNotificationAdvice(
          provider = profile.provider,
          apiKey = profile.activeApiKey,
          modelName = profile.activeModel,
          userName = profile.displayName,
          todayContext = MealContextFormatter.describe(today, "No meals logged yet today."),
          yesterdayContext = MealContextFormatter.describe(yesterday, "No meals logged yesterday."),
          targetCalories = profile.targetDailyCalories,
          targetProtein = profile.targetDailyProteinGrams,
          memoryContext =
            MemoryRepository.formatForPrompt(memoryRepository.getAll(), emptyText = ""),
          cuisine = profile.cuisine
        )

      // Offline or no key: fall back to the plain numbers rather than inventing food advice.
      val message =
        advice.getOrElse {
          "${profile.displayName}, you have logged ${todayTotals.calories} / " +
            "${profile.targetDailyCalories} kcal and ${todayTotals.protein.toInt()} / " +
            "${profile.targetDailyProteinGrams.toInt()}g protein today. " +
            "Open CalorieTracker AI to log your next meal."
        }

      NotificationHelper.showCoachNotification(
        context = context,
        title = "AI Coach for ${profile.displayName}",
        message = message
      )
      Result.success()
    } catch (_: IOException) {
      Result.retry()
    }
  }

  private companion object {
    /** Active window: 05:00 to 23:59. */
    val ACTIVE_HOURS = 5..23
  }
}
