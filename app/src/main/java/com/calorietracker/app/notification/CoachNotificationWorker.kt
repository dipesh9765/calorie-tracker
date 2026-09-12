package com.calorietracker.app.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.calorietracker.app.data.local.AppDatabase
import com.calorietracker.app.data.repository.AiNutritionRepositoryImpl
import com.calorietracker.app.data.repository.PreferencesRepository
import com.calorietracker.app.domain.model.AiProviderType
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class CoachNotificationWorker(private val context: Context, params: WorkerParameters) :
  CoroutineWorker(context, params) {

  private val aiRepository = AiNutritionRepositoryImpl()

  override suspend fun doWork(): Result =
    withContext(Dispatchers.IO) {
      try {
        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)

        // Active window: execute only between 5:00 AM (5) and 11:00 PM (23)
        if (currentHour < 5 || currentHour > 23) {
          return@withContext Result.success()
        }

        val prefsRepo = PreferencesRepository(context)
        val profile = prefsRepo.getUserProfile()

        if (!profile.notificationsEnabled) {
          return@withContext Result.success()
        }

        val database = AppDatabase.getInstance(context)
        val allMeals = database.mealDao().getAllMeals().first()

        val todayIso = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val yesterdayCalendar = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        val yesterdayIso =
          SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(yesterdayCalendar.time)

        val todayMeals = allMeals.filter { it.dateIso == todayIso }
        val yesterdayMeals = allMeals.filter { it.dateIso == yesterdayIso }

        val todayCalories = todayMeals.sumOf { it.calories }
        val todayProtein = todayMeals.sumOf { it.proteinGrams.toDouble() }.toFloat()
        val todayCarbs = todayMeals.sumOf { it.carbsGrams.toDouble() }.toFloat()
        val todayFat = todayMeals.sumOf { it.fatGrams.toDouble() }.toFloat()

        val userName = if (profile.name.isNotBlank()) profile.name else "Dipesh"

        val todaySummary =
          if (todayMeals.isNotEmpty()) {
            "$todayCalories kcal, ${todayProtein}g protein, ${todayCarbs}g carbs, ${todayFat}g fat. Foods logged: " +
              todayMeals.joinToString("; ") {
                "${it.foodName} (${it.calories} kcal, ${it.proteinGrams}g P)"
              }
          } else {
            "No meals logged yet today."
          }

        val yesterdaySummary =
          if (yesterdayMeals.isNotEmpty()) {
            "${yesterdayMeals.sumOf { it.calories }} kcal, ${yesterdayMeals.sumOf { it.proteinGrams.toDouble() }}g protein. Foods logged: " +
              yesterdayMeals.joinToString("; ") { "${it.foodName}" }
          } else {
            "No meals logged yesterday."
          }

        val providerType = AiProviderType.fromString(profile.primaryAiProvider)
        val apiKey =
          when (providerType) {
            AiProviderType.OPENAI -> profile.openAiApiKey
            AiProviderType.CLAUDE -> profile.claudeApiKey
            AiProviderType.DEEPSEEK -> profile.deepSeekApiKey
            else -> profile.geminiApiKey
          }.trim()

        val modelName =
          when (providerType) {
            AiProviderType.OPENAI -> profile.openAiModel
            AiProviderType.CLAUDE -> profile.claudeModel
            AiProviderType.DEEPSEEK -> profile.deepSeekModel
            else -> profile.geminiModel
          }.trim()

        val aiResult =
          aiRepository.generateNotificationAdvice(
            provider = providerType,
            apiKey = apiKey,
            modelName = modelName,
            userName = userName,
            todayContext = todaySummary,
            yesterdayContext = yesterdaySummary,
            targetCalories = profile.targetDailyCalories,
            targetProtein = profile.targetDailyProteinGrams
          )

        val notificationMessage =
          aiResult.getOrElse {
            // Objective data fallback when offline or missing key—zero hardcoded food suggestions!
            "$userName, you have logged $todayCalories / ${profile.targetDailyCalories} kcal and ${todayProtein.toInt()} / ${profile.targetDailyProteinGrams.toInt()}g protein today. Open CalorieTracker AI to log your next meal."
          }

        NotificationHelper.showCoachNotification(
          context = context,
          title = "AI Coach for $userName",
          message = notificationMessage
        )

        Result.success()
      } catch (_: Exception) {
        Result.retry()
      }
    }
}
