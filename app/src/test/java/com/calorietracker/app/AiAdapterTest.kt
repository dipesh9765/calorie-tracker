package com.calorietracker.app

import com.calorietracker.app.data.remote.adapter.IAiProviderAdapter
import com.calorietracker.app.data.repository.AiNutritionRepositoryImpl
import com.calorietracker.app.domain.model.AiProviderType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Functionality tests for all AI Providers (Gemini, OpenAI, Claude, DeepSeek).
 * Tests JSON extraction, adapter response parsing, error handling, and payload schemas.
 */
class AiAdapterTest {

    private lateinit var repository: AiNutritionRepositoryImpl

    @Before
    fun setUp() {
        repository = AiNutritionRepositoryImpl()
    }

    @Test
    fun `extractJsonString parses pure JSON correctly`() {
        val input = """{"foodName": "2 Rotis", "calories": 180}"""
        val result = repository.extractJsonString(input)
        assertEquals(input, result)
    }

    @Test
    fun `extractJsonString parses markdown wrapped JSON correctly`() {
        val input = """
            ```json
            {
              "foodName": "Tarri Poha",
              "portionDescription": "1 plate",
              "calories": 300
            }
            ```
        """.trimIndent()

        val expected = """{"foodName": "Tarri Poha","portionDescription": "1 plate","calories": 300}""".replace(" ", "")
        val result = repository.extractJsonString(input).replace(" ", "").replace("\n", "")
        assertEquals(expected, result)
    }

    @Test
    fun `extractJsonString handles conversational preamble before and after JSON`() {
        val input = """
            Sure! Here is the nutritional breakdown:
            ```json
            {
              "foodName": "Nakpro Whey",
              "calories": 125,
              "proteinGrams": 24.0
            }
            ```
            Hope this helps with your 180g protein goal!
        """.trimIndent()

        val result = repository.extractJsonString(input)
        assertTrue(result.startsWith("{"))
        assertTrue(result.endsWith("}"))
        assertTrue(result.contains("Nakpro Whey"))
    }

    @Test
    fun `parseMealText returns failure when API key is blank for any provider`() = runBlocking {
        for (provider in AiProviderType.values()) {
            val result = repository.parseMealText("2 rotis with chicken", provider, "   ")
            assertTrue("Expected failure for provider ${provider.name}", result.isFailure)
            val errorMessage = result.exceptionOrNull()?.message ?: ""
            assertTrue(errorMessage.contains("API Key"))
        }
    }

    @Test
    fun `mock adapter parseMealText returns expected meal domain model`() = runBlocking {
        val mockAdapter = object : IAiProviderAdapter {
            override suspend fun parseMeal(inputText: String, apiKey: String, systemInstruction: String, modelName: String): String {
                return """
                    {
                      "foodName": "3 Rotis & Saoji Chicken",
                      "portionDescription": "3 wheat chapatis + 150g saoji chicken curry",
                      "calories": 550,
                      "proteinGrams": 45.0,
                      "carbsGrams": 45.0,
                      "fatGrams": 18.0,
                      "mealCategory": "Dinner",
                      "advice": "Great meal! 45g protein puts you close to your 180g daily target."
                    }
                """.trimIndent()
            }
        }

        val testRepo = AiNutritionRepositoryImpl(
            geminiAdapter = mockAdapter,
            openAiAdapter = mockAdapter,
            claudeAdapter = mockAdapter,
            deepSeekAdapter = mockAdapter
        )

        val result = testRepo.parseMealText("3 rotis with saoji chicken", AiProviderType.GEMINI, "test_api_key")
        assertTrue(result.isSuccess)

        val (meal, advice) = result.getOrThrow()
        assertEquals("3 Rotis & Saoji Chicken", meal.foodName)
        assertEquals(550, meal.calories)
        assertEquals(45.0f, meal.proteinGrams)
        assertEquals("Dinner", meal.mealCategory)
        assertTrue(advice.contains("180g daily target"))
    }

    @Test
    fun `mock adapter parseMealText handles yesterday past date parsing`() = runBlocking {
        val mockAdapter = object : IAiProviderAdapter {
            override suspend fun parseMeal(inputText: String, apiKey: String, systemInstruction: String, modelName: String): String {
                return """
                    {
                      "action": "CREATE",
                      "targetDateIso": "2026-09-10",
                      "foodName": "2 Rotis & Egg Curry",
                      "portionDescription": "2 chapatis + 2 eggs in curry",
                      "calories": 420,
                      "proteinGrams": 22.0,
                      "carbsGrams": 30.0,
                      "fatGrams": 14.0,
                      "mealCategory": "Dinner",
                      "advice": "Logged for yesterday."
                    }
                """.trimIndent()
            }
        }

        val testRepo = AiNutritionRepositoryImpl(geminiAdapter = mockAdapter)
        val result = testRepo.parseMealText("Yesterday night I ate 2 rotis with egg curry", AiProviderType.GEMINI, "test_key")
        assertTrue(result.isSuccess)

        val (meal, _) = result.getOrThrow()
        assertEquals("2026-09-10", meal.dateIso)
        assertEquals("2 Rotis & Egg Curry", meal.foodName)
    }
}
