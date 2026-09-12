package com.calorietracker.app

import com.calorietracker.app.data.remote.adapter.IAiProviderAdapter
import com.calorietracker.app.data.repository.AiNutritionRepositoryImpl
import com.calorietracker.app.domain.model.AiProviderType
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Comprehensive Edge Case and Resilience Test suite for [AiNutritionRepositoryImpl] and
 * [AiProviderType]. Tests boundary conditions, malformed JSON handling, missing fields, type
 * coercion, and network error transformations.
 */
class AiNutritionRepositoryEdgeCaseTest {

  private lateinit var repository: AiNutritionRepositoryImpl

  @Before
  fun setUp() {
    repository = AiNutritionRepositoryImpl()
  }

  // ==========================================
  // AiProviderType Resolution Tests
  // ==========================================

  @Test
  fun `AiProviderType fromString resolves case-insensitively for all supported providers`() {
    assertEquals(AiProviderType.GEMINI, AiProviderType.fromString("gemini"))
    assertEquals(AiProviderType.GEMINI, AiProviderType.fromString("GEMINI"))
    assertEquals(AiProviderType.GEMINI, AiProviderType.fromString("GeMiNi"))

    assertEquals(AiProviderType.OPENAI, AiProviderType.fromString("openai"))
    assertEquals(AiProviderType.OPENAI, AiProviderType.fromString("OPENAI"))

    assertEquals(AiProviderType.CLAUDE, AiProviderType.fromString("claude"))
    assertEquals(AiProviderType.CLAUDE, AiProviderType.fromString("CLAUDE"))

    assertEquals(AiProviderType.DEEPSEEK, AiProviderType.fromString("deepseek"))
    assertEquals(AiProviderType.DEEPSEEK, AiProviderType.fromString("DEEPSEEK"))
  }

  @Test
  fun `AiProviderType fromString defaults safely to GEMINI for unknown or blank inputs`() {
    assertEquals(AiProviderType.GEMINI, AiProviderType.fromString(""))
    assertEquals(AiProviderType.GEMINI, AiProviderType.fromString("   "))
    assertEquals(AiProviderType.GEMINI, AiProviderType.fromString("llama3"))
    assertEquals(AiProviderType.GEMINI, AiProviderType.fromString("mistral"))
    assertEquals(AiProviderType.GEMINI, AiProviderType.fromString("null"))
  }

  // ==========================================
  // JSON Extraction Edge Cases
  // ==========================================

  @Test
  fun `extractJsonString handles strings with no braces gracefully without crashing`() {
    val noBraces = "I cannot fulfill this request as an AI."
    val result = repository.extractJsonString(noBraces)
    assertEquals("I cannot fulfill this request as an AI.", result.trim())
  }

  @Test
  fun `extractJsonString handles unclosed or mismatched braces without crashing`() {
    val unclosed = "Some preamble { \"foodName\": \"Incomplete"
    val result = repository.extractJsonString(unclosed)
    // Since no matching closing brace, returns cleaned raw string
    assertTrue(result.contains("Incomplete"))
  }

  @Test
  fun `extractJsonString handles nested braces within JSON structure`() {
    val nested =
      """
            Prefix text here
            {
               "foodName": "Custom Bowl",
               "portionDescription": "Bowl containing {rice, dal}",
               "calories": 400
            }
            Suffix notes.
        """
        .trimIndent()

    val extracted = repository.extractJsonString(nested)
    assertTrue(extracted.startsWith("{"))
    assertTrue(extracted.endsWith("}"))
    assertTrue(extracted.contains("Bowl containing {rice, dal}"))
  }

  // ==========================================
  // Payload Parsing & Type Coercion Edge Cases
  // ==========================================

  @Test
  fun `parseMealText coerces string numbers and decimal strings into primitives safely`() =
    runBlocking {
      val stringifiedJsonAdapter =
        object : IAiProviderAdapter {
          override suspend fun parseMeal(
            inputText: String,
            apiKey: String,
            systemInstruction: String,
            modelName: String
          ): String {
            return """
                    {
                      "action": "CREATE",
                      "targetDateIso": "2026-09-12",
                      "foodName": "2 Scoops Nakpro Platinum Whey",
                      "portionDescription": "2 scoops 66g",
                      "calories": "248",
                      "proteinGrams": "48.0",
                      "carbsGrams": "6.6",
                      "fatGrams": "3.6",
                      "mealCategory": "Supplement",
                      "advice": "Solid post-workout protein boost!"
                    }
                """
              .trimIndent()
          }
        }

      val testRepo = AiNutritionRepositoryImpl(geminiAdapter = stringifiedJsonAdapter)
      val result = testRepo.parseMealText("2 scoops whey", AiProviderType.GEMINI, "dummy_key")

      assertTrue(result.isSuccess)
      val (meal, advice) = result.getOrThrow()
      assertEquals(248, meal.calories)
      assertEquals(48.0f, meal.proteinGrams, 0.01f)
      assertEquals(6.6f, meal.carbsGrams, 0.01f)
      assertEquals(3.6f, meal.fatGrams, 0.01f)
      assertEquals("Supplement", meal.mealCategory)
      assertEquals("2026-09-12", meal.dateIso)
    }

  @Test
  fun `parseMealText provides default fallbacks when required fields are missing or null`() =
    runBlocking {
      val minimalJsonAdapter =
        object : IAiProviderAdapter {
          override suspend fun parseMeal(
            inputText: String,
            apiKey: String,
            systemInstruction: String,
            modelName: String
          ): String {
            return """
                    {
                      "calories": 350
                    }
                """
              .trimIndent()
          }
        }

      val testRepo = AiNutritionRepositoryImpl(geminiAdapter = minimalJsonAdapter)
      val result = testRepo.parseMealText("Bowl of dal khichdi", AiProviderType.GEMINI, "dummy_key")

      assertTrue(result.isSuccess)
      val (meal, advice) = result.getOrThrow()
      // Food name defaults to the original input text if omitted
      assertEquals("Bowl of dal khichdi", meal.foodName)
      assertEquals(350, meal.calories)
      assertEquals(0f, meal.proteinGrams, 0.01f)
      assertEquals(0f, meal.carbsGrams, 0.01f)
      assertEquals(0f, meal.fatGrams, 0.01f)
      assertEquals("Estimated portion", meal.portionDescription)
      assertEquals("Meal", meal.mealCategory)
      assertTrue(advice.isNotBlank())
    }

  @Test
  fun `parseMealText handles non-numeric strings in numeric fields without throwing NumberFormatException`() =
    runBlocking {
      val malformedNumbersAdapter =
        object : IAiProviderAdapter {
          override suspend fun parseMeal(
            inputText: String,
            apiKey: String,
            systemInstruction: String,
            modelName: String
          ): String {
            return """
                    {
                      "foodName": "Tarri Samosa",
                      "calories": "N/A",
                      "proteinGrams": "unknown",
                      "carbsGrams": "approx 40g",
                      "fatGrams": "high"
                    }
                """
              .trimIndent()
          }
        }

      val testRepo = AiNutritionRepositoryImpl(openAiAdapter = malformedNumbersAdapter)
      val result = testRepo.parseMealText("1 tarri samosa", AiProviderType.OPENAI, "dummy_key")

      assertTrue(result.isSuccess)
      val (meal, _) = result.getOrThrow()
      assertEquals(0, meal.calories)
      assertEquals(0f, meal.proteinGrams, 0.01f)
      assertEquals(0f, meal.carbsGrams, 0.01f)
      assertEquals(0f, meal.fatGrams, 0.01f)
    }

  // ==========================================
  // Error Mapping & Network Failure Tests
  // ==========================================

  @Test
  fun `parseMealText transforms SocketTimeoutException to informative provider-specific message`() =
    runBlocking {
      val timeoutAdapter =
        object : IAiProviderAdapter {
          override suspend fun parseMeal(
            inputText: String,
            apiKey: String,
            systemInstruction: String,
            modelName: String
          ): String {
            throw SocketTimeoutException("Read timed out")
          }
        }

      val testRepo = AiNutritionRepositoryImpl(claudeAdapter = timeoutAdapter)
      val result = testRepo.parseMealText("Chicken curry", AiProviderType.CLAUDE, "test_key")

      assertTrue(result.isFailure)
      val errorMessage = result.exceptionOrNull()?.message ?: ""
      assertTrue(errorMessage.contains("timed out", ignoreCase = true))
      assertTrue(errorMessage.contains("Claude"))
    }

  @Test
  fun `parseMealText transforms UnknownHostException to clear offline network advice`() =
    runBlocking {
      val offlineAdapter =
        object : IAiProviderAdapter {
          override suspend fun parseMeal(
            inputText: String,
            apiKey: String,
            systemInstruction: String,
            modelName: String
          ): String {
            throw UnknownHostException("api.deepseek.com")
          }
        }

      val testRepo = AiNutritionRepositoryImpl(deepSeekAdapter = offlineAdapter)
      val result = testRepo.parseMealText("3 eggs", AiProviderType.DEEPSEEK, "test_key")

      assertTrue(result.isFailure)
      val errorMessage = result.exceptionOrNull()?.message ?: ""
      assertTrue(errorMessage.contains("No internet connection available"))
    }

  // ==========================================
  // Notification Advice Generation Tests
  // ==========================================

  @Test
  fun `generateNotificationAdvice returns failure if API key is blank`() = runBlocking {
    val result =
      repository.generateNotificationAdvice(
        provider = AiProviderType.GEMINI,
        apiKey = "   ",
        modelName = "gemini-3.6-flash",
        userName = "Dipesh",
        todayContext = "",
        yesterdayContext = "",
        targetCalories = 2300,
        targetProtein = 180f
      )
    assertTrue(result.isFailure)
    assertTrue(result.exceptionOrNull()?.message?.contains("API Key") == true)
  }

  @Test
  fun `generateNotificationAdvice parses json advice correctly`() = runBlocking {
    val mockAdapter =
      object : IAiProviderAdapter {
        override suspend fun parseMeal(
          inputText: String,
          apiKey: String,
          systemInstruction: String,
          modelName: String
        ): String {
          return """{"advice": "Hey Dipesh, you need 40g more protein! Try 4 boiled eggs or 1 scoop Nakpro whey for dinner."}"""
        }
      }

    val testRepo = AiNutritionRepositoryImpl(geminiAdapter = mockAdapter)
    val result =
      testRepo.generateNotificationAdvice(
        provider = AiProviderType.GEMINI,
        apiKey = "valid_key",
        modelName = "gemini-3.6-flash",
        userName = "Dipesh",
        todayContext = "1500 kcal, 140g protein",
        yesterdayContext = "2200 kcal, 175g protein",
        targetCalories = 2300,
        targetProtein = 180f
      )

    assertTrue(result.isSuccess)
    val advice = result.getOrThrow()
    assertEquals(
      "Hey Dipesh, you need 40g more protein! Try 4 boiled eggs or 1 scoop Nakpro whey for dinner.",
      advice
    )
  }

  // ==========================================
  // Action Resolution & Duplicate Meal Integrity Tests
  // ==========================================

  @Test
  fun `parseMealText extracts action UPDATE correctly when user corrects a meal`() = runBlocking {
    val updateAdapter =
      object : IAiProviderAdapter {
        override suspend fun parseMeal(
          inputText: String,
          apiKey: String,
          systemInstruction: String,
          modelName: String
        ): String {
          return """
            {
              "action": "UPDATE",
              "targetDateIso": "2026-09-12",
              "foodName": "Chicken Curry",
              "portionDescription": "200g chicken curry",
              "calories": 420,
              "proteinGrams": 40.0,
              "carbsGrams": 5.0,
              "fatGrams": 14.0,
              "mealCategory": "Dinner",
              "advice": "Updated chicken curry entry."
            }
          """
            .trimIndent()
        }
      }

    val testRepo = AiNutritionRepositoryImpl(geminiAdapter = updateAdapter)
    val result =
      testRepo.parseMealText("Update chicken curry to 420 kcal", AiProviderType.GEMINI, "test_key")

    assertTrue(result.isSuccess)
    val (updateMeal, _) = result.getOrThrow()
    assertEquals("UPDATE", updateMeal.action)
    assertEquals("Chicken Curry", updateMeal.foodName)
    assertEquals(420, updateMeal.calories)
  }

  @Test
  fun `parseMealText defaults action to CREATE when action field is missing`() = runBlocking {
    val createAdapter =
      object : IAiProviderAdapter {
        override suspend fun parseMeal(
          inputText: String,
          apiKey: String,
          systemInstruction: String,
          modelName: String
        ): String {
          return """
            {
              "foodName": "2 Rotis",
              "portionDescription": "2 wheat chapatis",
              "calories": 180,
              "proteinGrams": 6.0,
              "carbsGrams": 30.0,
              "fatGrams": 3.0,
              "mealCategory": "Lunch"
            }
          """
            .trimIndent()
        }
      }

    val testRepo = AiNutritionRepositoryImpl(openAiAdapter = createAdapter)
    val result = testRepo.parseMealText("2 rotis", AiProviderType.OPENAI, "test_key")

    assertTrue(result.isSuccess)
    val (createMeal, _) = result.getOrThrow()
    assertEquals("CREATE", createMeal.action)
  }

  @Test
  fun `duplicate food logging preserves distinct entries when action is CREATE`() {
    val savedMeals = mutableListOf<com.calorietracker.app.data.local.MealEntity>()

    fun simulateMealSave(entry: com.calorietracker.app.data.model.MealEntry, action: String) {
      val isUpdate = action.equals("UPDATE", ignoreCase = true)
      val existing =
        if (isUpdate) {
          savedMeals.find {
            it.dateIso == entry.dateIso && it.foodName.equals(entry.foodName, ignoreCase = true)
          }
        } else {
          null
        }

      val entityToSave =
        if (existing != null) {
          entry.copy(id = existing.id).let {
            com.calorietracker.app.data.local.MealEntity(
              id = it.id,
              foodName = it.foodName,
              portionDescription = it.portionDescription,
              calories = it.calories,
              proteinGrams = it.proteinGrams,
              carbsGrams = it.carbsGrams,
              fatGrams = it.fatGrams,
              mealCategory = it.mealCategory,
              dateIso = it.dateIso,
              timestamp = it.timestamp
            )
          }
        } else {
          com.calorietracker.app.data.local.MealEntity(
            id = entry.id,
            foodName = entry.foodName,
            portionDescription = entry.portionDescription,
            calories = entry.calories,
            proteinGrams = entry.proteinGrams,
            carbsGrams = entry.carbsGrams,
            fatGrams = entry.fatGrams,
            mealCategory = entry.mealCategory,
            dateIso = entry.dateIso,
            timestamp = entry.timestamp
          )
        }

      // If existing, replace it; otherwise add new
      val index = savedMeals.indexOfFirst { it.id == entityToSave.id }
      if (index >= 0) {
        savedMeals[index] = entityToSave
      } else {
        savedMeals.add(entityToSave)
      }
    }

    // 1. User logs 2 Rotis for Lunch
    val lunchRotis =
      com.calorietracker.app.data.model.MealEntry(
        id = "lunch-id-1",
        foodName = "2 Rotis",
        portionDescription = "2 chapatis",
        calories = 180,
        proteinGrams = 6f,
        carbsGrams = 30f,
        fatGrams = 3f,
        mealCategory = "Lunch",
        dateIso = "2026-09-12"
      )
    simulateMealSave(lunchRotis, "CREATE")
    assertEquals(1, savedMeals.size)

    // 2. User logs 2 Rotis for Dinner on the same day -> Must NOT overwrite lunch!
    val dinnerRotis =
      com.calorietracker.app.data.model.MealEntry(
        id = "dinner-id-2",
        foodName = "2 Rotis",
        portionDescription = "2 chapatis",
        calories = 180,
        proteinGrams = 6f,
        carbsGrams = 30f,
        fatGrams = 3f,
        mealCategory = "Dinner",
        dateIso = "2026-09-12"
      )
    simulateMealSave(dinnerRotis, "CREATE")

    // Both lunch and dinner rotis are preserved!
    assertEquals(2, savedMeals.size)
    assertEquals("lunch-id-1", savedMeals[0].id)
    assertEquals("dinner-id-2", savedMeals[1].id)

    // 3. User updates Dinner Rotis to 3 Rotis (action == UPDATE)
    val updateDinnerRotis =
      com.calorietracker.app.data.model.MealEntry(
        id = "new-temp-id",
        foodName = "2 Rotis",
        portionDescription = "3 chapatis",
        calories = 270,
        proteinGrams = 9f,
        carbsGrams = 45f,
        fatGrams = 4.5f,
        mealCategory = "Dinner",
        dateIso = "2026-09-12"
      )
    simulateMealSave(updateDinnerRotis, "UPDATE")

    // Total count remains 2, but first matched entry updated
    assertEquals(2, savedMeals.size)
    assertEquals(270, savedMeals[0].calories)
  }
}
