package com.calorietracker.app.data.repository

import com.calorietracker.app.data.remote.adapter.*
import com.calorietracker.app.domain.model.AiProviderType
import com.calorietracker.app.domain.model.Meal
import com.calorietracker.app.domain.repository.IAiNutritionRepository
import com.google.gson.Gson
import com.google.gson.JsonObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
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
  private val gson: Gson = Gson()
) : IAiNutritionRepository {

  override suspend fun parseMealText(
    mealText: String,
    provider: AiProviderType,
    apiKey: String,
    modelName: String,
    todayContext: String,
    yesterdayContext: String
  ): Result<Pair<Meal, String>> =
    withContext(Dispatchers.IO) {
      try {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank()) {
          return@withContext Result.failure(
            IllegalArgumentException(
              "API Key for ${provider.displayName} is missing. Please configure it in Profile Settings."
            )
          )
        }

        val todayIso = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val yesterdayIso =
          SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            .format(Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }.time)
        val dayOfWeek = SimpleDateFormat("EEEE", Locale.getDefault()).format(Date())

        val dynamicSystemInstruction =
          """
                You are an expert Indian Clinical & Sports Nutritionist specializing in Indian cuisine, specifically central Indian / Nagpur local diet (e.g. rotis, tarri poha, saoji, sabudana khichdi, dahi samosa, chicken breast/curry, Nakpro whey, Beast Life creatine, etc.).
                
                Current System Context:
                - Today's Date: $todayIso ($dayOfWeek)
                - Yesterday's Date: $yesterdayIso
                ${if (yesterdayContext.isNotBlank()) "- Yesterday's ($yesterdayIso) Consumption Log & Totals:\n  $yesterdayContext" else ""}
                ${if (todayContext.isNotBlank()) "- Today's ($todayIso) Current Consumption Log & Totals:\n  $todayContext" else ""}

                Multi-Day AI Coaching Advice Rules for "advice":
                - Always evaluate the combined multi-day context of yesterday's consumption AND today's current totals against the user's daily targets (180g Protein, 2,300 kcal deficit).
                - Compare today's protein/calorie trajectory against yesterday. For example, if yesterday had high protein and today is lagging, acknowledge yesterday's solid intake and recommend specific food items (e.g., Nakpro Whey, 150g chicken breast, eggs) to bridge today's remaining gap.
                - Keep advice highly encouraging, actionable, concise, and grounded in Indian meal options.
                
                Date & Action Parsing Rules:
                - Analyze user input for relative date references (e.g. "yesterday", "last night", "2 days ago", "on Sept 8").
                - If user mentions "yesterday" or "last night", set "targetDateIso" to "$yesterdayIso".
                - If user specifies another past date, compute its ISO date "YYYY-MM-DD".
                - If no past date is mentioned (e.g., "I ate 2 rotis"), default "targetDateIso" to "$todayIso".
                - Identify "action": set to "UPDATE" if user explicitly asks to correct/change an existing meal (e.g. "update yesterday's chicken curry to 400 kcal"), otherwise set to "CREATE".

                Brand & Packaged Product Nutrition Guidelines:
                - If the user specifies a brand, commercial product, or restaurant chain (e.g. "Nakpro Whey", "Amul High Protein Lassi", "Haldiram's Poha", "MuscleBlaze Biozyme Whey", "Epigamia Greek Yogurt", "Subway Chicken Sub"), reference official nutritional label values for that exact product and serving size.
                - For Nakpro Whey (Perform/Impact/Gold Concentrate vs Platinum Isolate): Standard scoop is ~33g–37g (~124-130 kcal, 24g protein, 3.3g carbs, 1.8g fat). If user specifies a larger scoop size (e.g. 40g or 46g), scale calories & macros pro-rata (~155-175 kcal, ~28-30g protein).
                - Format "foodName" to clearly state the brand and item (e.g., "Nakpro Whey (1 Scoop / 33-40g)", "Amul High Protein Lassi (200ml)").
                - For home-cooked meals without a brand specified, use standard Indian household portion estimates.

                Estimate portions by realistic Indian household standards:
                - 1 average Roti/Chapati (wheat) = ~80-90 kcal, 3g protein, 15g carbs, 1.5g fat.
                - 1 katori dal/sabzi = ~150 kcal.
                - 1 scoop Nakpro Whey Protein (33g-40g scoop) = ~125-130 kcal, 24g protein, 3.3g carbs, 1.8g fat.
                - 1 plate Tarri Poha = ~280-320 kcal.
                - 100g Chicken Breast (cooked dry/curry) = ~165-190 kcal, 31g protein.
                - 1 Samosa with Dahi = ~260-300 kcal.

                ALWAYS output strictly VALID JSON only. No markdown ticks, no conversational filler.
                JSON format:
                {
                  "action": "CREATE",
                  "targetDateIso": "$todayIso",
                  "foodName": "Summary title of eaten items",
                  "portionDescription": "Exact portion breakdown",
                  "calories": 450,
                  "proteinGrams": 32.5,
                  "carbsGrams": 40.0,
                  "fatGrams": 12.0,
                  "mealCategory": "Lunch",
                  "advice": "Short nutritional tip for achieving 180g daily protein within 2300 kcal deficit."
                }
            """
            .trimIndent()

        val adapter =
          when (provider) {
            AiProviderType.GEMINI -> geminiAdapter
            AiProviderType.OPENAI -> openAiAdapter
            AiProviderType.CLAUDE -> claudeAdapter
            AiProviderType.DEEPSEEK -> deepSeekAdapter
          }

        val rawResponse = adapter.parseMeal(mealText, cleanKey, dynamicSystemInstruction, modelName)
        val jsonString = extractJsonString(rawResponse)
        val jsonObject = gson.fromJson(jsonString, JsonObject::class.java)

        val action = jsonObject.get("action")?.asStringSafely() ?: "CREATE"
        val foodName = jsonObject.get("foodName")?.asStringSafely() ?: mealText
        val portionDescription =
          jsonObject.get("portionDescription")?.asStringSafely() ?: "Estimated portion"
        val calories = jsonObject.get("calories")?.asIntSafely() ?: 0
        val proteinGrams = jsonObject.get("proteinGrams")?.asFloatSafely() ?: 0f
        val carbsGrams = jsonObject.get("carbsGrams")?.asFloatSafely() ?: 0f
        val fatGrams = jsonObject.get("fatGrams")?.asFloatSafely() ?: 0f
        val mealCategory = jsonObject.get("mealCategory")?.asStringSafely() ?: "Meal"
        val targetDateIso = jsonObject.get("targetDateIso")?.asStringSafely() ?: todayIso
        val advice =
          jsonObject.get("advice")?.asStringSafely() ?: "Keep hitting your daily protein target!"

        val meal =
          Meal(
            foodName = foodName,
            portionDescription = portionDescription,
            calories = calories,
            proteinGrams = proteinGrams,
            carbsGrams = carbsGrams,
            fatGrams = fatGrams,
            mealCategory = mealCategory,
            dateIso = targetDateIso,
            action = action
          )

        Result.success(Pair(meal, advice))
      } catch (e: Exception) {
        val userFriendlyError =
          when {
            e is java.net.SocketTimeoutException ||
              e.message?.contains("timeout", ignoreCase = true) == true ->
              Exception(
                "Network request timed out while contacting ${provider.displayName}. Please check your connection and retry."
              )
            e is java.net.UnknownHostException ->
              Exception("No internet connection available. Please check your network and retry.")
            else -> e
          }
        Result.failure(userFriendlyError)
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
    targetProtein: Float
  ): Result<String> =
    withContext(Dispatchers.IO) {
      try {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank()) {
          return@withContext Result.failure(
            IllegalArgumentException("API Key for ${provider.displayName} is missing.")
          )
        }

        val todayIso = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

        val systemInstruction =
          """
                You are an expert Indian Clinical & Sports Nutritionist giving real-time push notification advice to $userName.
                Current Time: $currentTime on $todayIso.
                User Target Goals: $targetCalories kcal daily target, ${targetProtein}g daily protein target.
                
                System Intake Context:
                - Yesterday's Intake: ${if (yesterdayContext.isNotBlank()) yesterdayContext else "No meals logged."}
                - Today's Current Intake: ${if (todayContext.isNotBlank()) todayContext else "No meals logged yet."}

                Instructions:
                Write a 1-2 sentence real-time push notification recommendation addressed directly to $userName.
                - Analyze today's trajectory vs target and yesterday's log.
                - If protein is low relative to the time of day, recommend specific food options (e.g. 1 protein shake, 3 eggs, chicken breast, paneer, sprouts, etc.).
                - If today's calories are near or over target, suggest light alternatives (e.g. cucumber salad, clear soup, roasted makhana) or lighter dinner options.
                - Output strictly a JSON object with key "advice". No extra conversational filler.
                Example JSON: {"advice": "Hey $userName, you need 35g more protein today! Try having 1 scoop of protein shake or 4 eggs for dinner."}
            """
            .trimIndent()

        val adapter =
          when (provider) {
            AiProviderType.GEMINI -> geminiAdapter
            AiProviderType.OPENAI -> openAiAdapter
            AiProviderType.CLAUDE -> claudeAdapter
            AiProviderType.DEEPSEEK -> deepSeekAdapter
          }

        val rawResponse =
          adapter.parseMeal(
            inputText = "Generate push notification recommendation for $userName",
            apiKey = cleanKey,
            systemInstruction = systemInstruction,
            modelName = modelName
          )

        val jsonString = extractJsonString(rawResponse)
        val jsonObject = gson.fromJson(jsonString, JsonObject::class.java)
        val advice =
          jsonObject.get("advice")?.asStringSafely()
            ?: rawResponse.replace(Regex("```.*```"), "").trim()

        if (advice.isNotBlank()) {
          Result.success(advice)
        } else {
          Result.failure(
            IllegalStateException("Empty recommendation generated by ${provider.displayName}")
          )
        }
      } catch (e: Exception) {
        Result.failure(e)
      }
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

private fun com.google.gson.JsonElement.asStringSafely(): String {
  return try {
    if (this.isJsonPrimitive) this.asString else this.toString()
  } catch (_: Exception) {
    this.toString()
  }
}

private fun com.google.gson.JsonElement.asIntSafely(): Int {
  return try {
    if (this.isJsonPrimitive) {
      val prim = this.asJsonPrimitive
      if (prim.isNumber) prim.asInt else prim.asString.toDoubleOrNull()?.toInt() ?: 0
    } else 0
  } catch (_: Exception) {
    0
  }
}

private fun com.google.gson.JsonElement.asFloatSafely(): Float {
  return try {
    if (this.isJsonPrimitive) {
      val prim = this.asJsonPrimitive
      if (prim.isNumber) prim.asFloat else prim.asString.toFloatOrNull() ?: 0f
    } else 0f
  } catch (_: Exception) {
    0f
  }
}
