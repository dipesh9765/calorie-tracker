package com.calorietracker.app.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.calorietracker.app.data.local.AppDatabase
import com.calorietracker.app.data.repository.PreferencesRepository
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class CoachNotificationWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val calendar = Calendar.getInstance()
            val currentHour = calendar.get(Calendar.HOUR_OF_DAY)

            // Window check: execute only between 5:00 AM (5) and 11:00 PM (23)
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
            val yesterdayIso = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(yesterdayCalendar.time)

            val todayMeals = allMeals.filter { it.dateIso == todayIso }
            val yesterdayMeals = allMeals.filter { it.dateIso == yesterdayIso }

            val todayCalories = todayMeals.sumOf { it.calories }
            val todayProtein = todayMeals.sumOf { it.proteinGrams.toDouble() }.toFloat()
            val todayCarbs = todayMeals.sumOf { it.carbsGrams.toDouble() }.toFloat()
            val todayFat = todayMeals.sumOf { it.fatGrams.toDouble() }.toFloat()

            val userName = if (profile.name.isNotBlank()) profile.name else "Dipesh"

            val todaySummary = if (todayMeals.isNotEmpty()) {
                "$todayCalories kcal, ${todayProtein}g protein, ${todayCarbs}g carbs, ${todayFat}g fat. Foods: " +
                        todayMeals.joinToString("; ") { "${it.foodName} (${it.calories} kcal, ${it.proteinGrams}g P)" }
            } else {
                "No meals logged yet today."
            }

            val yesterdaySummary = if (yesterdayMeals.isNotEmpty()) {
                "${yesterdayMeals.sumOf { it.calories }} kcal, ${yesterdayMeals.sumOf { it.proteinGrams.toDouble() }}g protein. Foods: " +
                        yesterdayMeals.joinToString("; ") { "${it.foodName}" }
            } else {
                "No meals logged yesterday."
            }

            val prompt = """
                You are ${userName}'s personal AI Nutrition Coach.
                User Targets: ${profile.targetDailyCalories} kcal daily target, ${profile.targetDailyProteinGrams}g daily protein target.
                Current Intake Today: $todaySummary
                Yesterday's Intake: $yesterdaySummary
                Current Time: ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())}

                Instructions:
                Generate a 1-2 sentence real-time push notification recommendation addressing him directly as ${userName}.
                - If protein is low relative to the current time, recommend specific foods (e.g. 1 protein shake, 3 eggs, chicken breast, or Greek yogurt).
                - If today's calories are near or over target, suggest a light dinner (e.g. cucumber salad, clear soup) or skipping a heavy meal.
                - Keep it punchy, intelligent, direct, and motivating for push notification display.
            """.trimIndent()

            val notificationMessage = fetchAiCoachRecommendation(
                provider = profile.primaryAiProvider,
                profile = profile,
                prompt = prompt,
                userName = userName,
                todayProtein = todayProtein,
                targetProtein = profile.targetDailyProteinGrams,
                todayCalories = todayCalories,
                targetCalories = profile.targetDailyCalories
            )

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

    private fun fetchAiCoachRecommendation(
        provider: String,
        profile: com.calorietracker.app.data.model.UserProfile,
        prompt: String,
        userName: String,
        todayProtein: Float,
        targetProtein: Float,
        todayCalories: Int,
        targetCalories: Int
    ): String {
        return try {
            val apiKey = when (provider) {
                "OpenAI" -> profile.openAiApiKey
                "Claude" -> profile.claudeApiKey
                "DeepSeek" -> profile.deepSeekApiKey
                else -> profile.geminiApiKey
            }.trim()

            val modelName = when (provider) {
                "OpenAI" -> profile.openAiModel
                "Claude" -> profile.claudeModel
                "DeepSeek" -> profile.deepSeekModel
                else -> profile.geminiModel
            }.trim()

            if (apiKey.isNotBlank() && provider.equals("Gemini", ignoreCase = true)) {
                val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"
                val jsonPayload = JsonObject().apply {
                    add("contents", gson.toJsonTree(listOf(mapOf("parts" to listOf(mapOf("text" to prompt))))))
                }
                val request = Request.Builder()
                    .url(url)
                    .post(jsonPayload.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        val root = gson.fromJson(body, JsonObject::class.java)
                        val text = root.getAsJsonArray("candidates")
                            ?.get(0)?.asJsonObject
                            ?.getAsJsonObject("content")
                            ?.getAsJsonArray("parts")
                            ?.get(0)?.asJsonObject
                            ?.get("text")?.asString

                        if (!text.isNullOrBlank()) return text.trim()
                    }
                }
            }

            // High-intelligence fallback rule engine if API key is not yet filled
            generateFallbackMessage(userName, todayProtein, targetProtein, todayCalories, targetCalories)
        } catch (_: Exception) {
            generateFallbackMessage(userName, todayProtein, targetProtein, todayCalories, targetCalories)
        }
    }

    private fun generateFallbackMessage(
        userName: String,
        todayProtein: Float,
        targetProtein: Float,
        todayCalories: Int,
        targetCalories: Int
    ): String {
        val remainingProtein = (targetProtein - todayProtein).coerceAtLeast(0f)
        val remainingCalories = targetCalories - todayCalories

        return when {
            todayCalories > targetCalories -> {
                "Hey $userName, you've reached your daily calorie target ($todayCalories / $targetCalories kcal). Consider skipping a heavy dinner and having a light cucumber salad or warm green tea!"
            }
            remainingProtein > 50f -> {
                "Hey $userName, you need ${remainingProtein.toInt()}g more protein today! Try taking 1 scoop of Nakpro Whey and 3 boiled eggs for a quick boost."
            }
            remainingProtein > 20f -> {
                "Hey $userName, you are close to your protein target! A quick protein shake or 150g paneer will complete your target."
            }
            else -> {
                "Hey $userName, great job staying on track today! You have $remainingCalories kcal remaining. Keep hydrating with water!"
            }
        }
    }
}
