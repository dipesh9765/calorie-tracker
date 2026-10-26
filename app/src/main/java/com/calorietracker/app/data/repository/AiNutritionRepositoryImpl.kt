package com.calorietracker.app.data.repository

import com.calorietracker.app.data.model.UserProfile
import com.calorietracker.app.data.remote.adapter.ClaudeAdapter
import com.calorietracker.app.data.remote.adapter.DeepSeekAdapter
import com.calorietracker.app.data.remote.adapter.GeminiAdapter
import com.calorietracker.app.data.remote.adapter.IAiProviderAdapter
import com.calorietracker.app.data.remote.adapter.OpenAiAdapter
import com.calorietracker.app.domain.model.AiProviderType
import com.calorietracker.app.domain.model.Meal
import com.calorietracker.app.domain.repository.IAiNutritionRepository
import com.calorietracker.app.domain.util.daysAgoIso
import com.calorietracker.app.domain.util.todayIso
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Concrete implementation of [IAiNutritionRepository]. Delegates multi-LLM meal text parsing to
 * specific provider adapters (Gemini, OpenAI, Claude, DeepSeek) with temporal context awareness
 * (today vs yesterday vs relative dates) and action resolution.
 */
class AiNutritionRepositoryImpl(
  private val geminiAdapter: IAiProviderAdapter = GeminiAdapter(),
  private val openAiAdapter: IAiProviderAdapter = OpenAiAdapter(),
  private val claudeAdapter: IAiProviderAdapter = ClaudeAdapter(),
  private val deepSeekAdapter: IAiProviderAdapter = DeepSeekAdapter(),
  private val gson: Gson = Gson(),
  private val clock: Clock = Clock.systemDefaultZone()
) : IAiNutritionRepository {

  override suspend fun parseMealText(
    mealText: String,
    provider: AiProviderType,
    apiKey: String,
    modelName: String,
    todayContext: String,
    yesterdayContext: String,
    memoryContext: String,
    profile: UserProfile
  ): Result<Pair<Meal, String>> =
    withContext(Dispatchers.IO) {
      val key = apiKey.trim()
      if (key.isBlank()) {
        return@withContext Result.failure(
          IllegalArgumentException(
            "API Key for ${provider.displayName} is missing. Please configure it in Profile Settings."
          )
        )
      }
      val todayIso = clock.todayIso()
      val prompt =
        MealPrompts.mealParsing(
          profile = profile,
          todayIso = todayIso,
          yesterdayIso = clock.daysAgoIso(1),
          dayOfWeek =
            LocalDate.now(clock).dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()),
          todayContext = todayContext,
          yesterdayContext = yesterdayContext,
          memoryContext = memoryContext
        )
      try {
        val raw = adapterFor(provider).parseMeal(mealText, key, prompt, modelName)
        Result.success(toMealAndAdvice(raw, fallbackName = mealText, todayIso = todayIso))
      } catch (e: IOException) {
        Result.failure(friendly(e, provider))
      } catch (e: JsonParseException) {
        Result.failure(e)
      } catch (e: IllegalStateException) {
        // Gson throws this for a response that is not the expected JSON shape.
        Result.failure(e)
      } catch (e: RuntimeException) {
        // Provider adapters report API errors as runtime exceptions carrying the provider message.
        Result.failure(friendly(e, provider))
      }
    }

  override suspend fun generateNotificationAdvice(
    provider: AiProviderType,
    apiKey: String,
    modelName: String,
    userName: String,
    todayContext: String,
    yesterdayContext: String,
    targetCalories: Int,
    targetProtein: Float,
    memoryContext: String,
    cuisine: String
  ): Result<String> =
    withContext(Dispatchers.IO) {
      val key = apiKey.trim()
      if (key.isBlank()) {
        return@withContext Result.failure(
          IllegalArgumentException("API Key for ${provider.displayName} is missing.")
        )
      }
      val prompt =
        MealPrompts.notification(
          userName = userName,
          cuisine = cuisine,
          todayIso = clock.todayIso(),
          currentTime = LocalTime.now(clock).format(DateTimeFormatter.ofPattern("HH:mm")),
          targetCalories = targetCalories,
          targetProtein = targetProtein,
          todayContext = todayContext,
          yesterdayContext = yesterdayContext,
          memoryContext = memoryContext
        )
      try {
        val raw =
          adapterFor(provider)
            .parseMeal(
              inputText = "Generate push notification recommendation for $userName",
              apiKey = key,
              systemInstruction = prompt,
              modelName = modelName
            )
        val advice =
          gson.fromJson(extractJsonString(raw), JsonObject::class.java).get("advice")?.safeString()
            ?: raw.replace(Regex("```.*```"), "").trim()
        if (advice.isNotBlank()) {
          Result.success(advice)
        } else {
          Result.failure(
            IllegalStateException("Empty recommendation generated by ${provider.displayName}")
          )
        }
      } catch (e: IOException) {
        Result.failure(e)
      } catch (e: JsonParseException) {
        Result.failure(e)
      } catch (e: RuntimeException) {
        Result.failure(e)
      }
    }

  private fun adapterFor(provider: AiProviderType): IAiProviderAdapter =
    when (provider) {
      AiProviderType.GEMINI -> geminiAdapter
      AiProviderType.OPENAI -> openAiAdapter
      AiProviderType.CLAUDE -> claudeAdapter
      AiProviderType.DEEPSEEK -> deepSeekAdapter
    }

  private fun toMealAndAdvice(
    raw: String,
    fallbackName: String,
    todayIso: String
  ): Pair<Meal, String> {
    val json = gson.fromJson(extractJsonString(raw), JsonObject::class.java)
    val meal =
      Meal(
        foodName = json.get("foodName")?.safeString() ?: fallbackName,
        portionDescription = json.get("portionDescription")?.safeString() ?: "Estimated portion",
        calories = json.get("calories")?.safeInt() ?: 0,
        proteinGrams = json.get("proteinGrams")?.safeFloat() ?: 0f,
        carbsGrams = json.get("carbsGrams")?.safeFloat() ?: 0f,
        fatGrams = json.get("fatGrams")?.safeFloat() ?: 0f,
        mealCategory = json.get("mealCategory")?.safeString() ?: "Meal",
        dateIso = json.get("targetDateIso")?.safeString() ?: todayIso,
        action = json.get("action")?.safeString() ?: "CREATE"
      )
    val advice = json.get("advice")?.safeString() ?: "Keep hitting your daily protein target!"
    return meal to advice
  }

  /** Replaces low-level network failures with a message the user can act on. */
  private fun friendly(e: Exception, provider: AiProviderType): Exception =
    when {
      e is SocketTimeoutException || e.message?.contains("timeout", ignoreCase = true) == true ->
        IOException(
          "Network request timed out while contacting ${provider.displayName}. " +
            "Please check your connection and retry.",
          e
        )
      e is UnknownHostException ->
        IOException("No internet connection available. Please check your network and retry.", e)
      else -> e
    }

  /**
   * Extracts pure JSON string from raw text response, removing any markdown code blocks or
   * surrounding text.
   */
  fun extractJsonString(rawResponse: String): String {
    val start = rawResponse.indexOf('{')
    val end = rawResponse.lastIndexOf('}')
    if (start != -1 && end != -1 && end > start) {
      return rawResponse.substring(start, end + 1)
    }
    return rawResponse.replace("```json", "").replace("```", "").trim()
  }
}

private fun JsonElement.safeString(): String = if (isJsonPrimitive) asString else toString()

private fun JsonElement.safeInt(): Int {
  if (!isJsonPrimitive) return 0
  val primitive = asJsonPrimitive
  return if (primitive.isNumber) primitive.asInt
  else primitive.asString.toDoubleOrNull()?.toInt() ?: 0
}

private fun JsonElement.safeFloat(): Float {
  if (!isJsonPrimitive) return 0f
  val primitive = asJsonPrimitive
  return if (primitive.isNumber) primitive.asFloat else primitive.asString.toFloatOrNull() ?: 0f
}
