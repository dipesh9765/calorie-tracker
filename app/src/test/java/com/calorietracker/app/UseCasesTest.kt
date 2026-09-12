package com.calorietracker.app

import com.calorietracker.app.domain.model.AiProviderType
import com.calorietracker.app.domain.model.Meal
import com.calorietracker.app.domain.repository.IAiNutritionRepository
import com.calorietracker.app.domain.repository.IMealRepository
import com.calorietracker.app.domain.usecase.GetDailyNutritionSummaryUseCase
import com.calorietracker.app.domain.usecase.LogMealWithAiUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit Test suite covering Domain Use Cases:
 * 1. [LogMealWithAiUseCase] - Input validation, AI parsing delegation, persistence triggering, and
 *    error propagation.
 * 2. [GetDailyNutritionSummaryUseCase] - Multi-meal aggregation, macro precision, sorting, and
 *    empty state handling.
 */
class UseCasesTest {

  // ==========================================
  // Fake Repositories
  // ==========================================

  private class FakeMealRepository(private val initialMeals: List<Meal> = emptyList()) :
    IMealRepository {
    val insertedMeals = mutableListOf<Meal>()
    var shouldThrowOnInsert = false

    override fun getMealsForDate(dateIso: String): Flow<List<Meal>> {
      return flowOf(initialMeals.filter { it.dateIso == dateIso })
    }

    override fun getAllMeals(): Flow<List<Meal>> {
      return flowOf(initialMeals)
    }

    override suspend fun insertMeal(meal: Meal) {
      if (shouldThrowOnInsert) {
        throw IllegalStateException("Database write error: disk full")
      }
      insertedMeals.add(meal)
    }

    override suspend fun deleteMeal(mealId: String) {
      insertedMeals.removeAll { it.id == mealId }
    }

    override suspend fun clearAll() {
      insertedMeals.clear()
    }
  }

  private class FakeAiNutritionRepository(private val cannedResult: Result<Pair<Meal, String>>) :
    IAiNutritionRepository {
    var capturedInputText: String? = null
    var capturedProvider: AiProviderType? = null
    var capturedApiKey: String? = null

    override suspend fun parseMealText(
      mealText: String,
      provider: AiProviderType,
      apiKey: String,
      modelName: String,
      todayContext: String,
      yesterdayContext: String
    ): Result<Pair<Meal, String>> {
      capturedInputText = mealText
      capturedProvider = provider
      capturedApiKey = apiKey
      return cannedResult
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
    ): Result<String> {
      return Result.success("Sample advice")
    }
  }

  // ==========================================
  // LogMealWithAiUseCase Tests
  // ==========================================

  @Test
  fun `LogMealWithAiUseCase returns failure when input text is blank or whitespace`() =
    runBlocking {
      val fakeAiRepo = FakeAiNutritionRepository(Result.failure(Exception("Should not be called")))
      val fakeMealRepo = FakeMealRepository()
      val useCase = LogMealWithAiUseCase(fakeAiRepo, fakeMealRepo)

      val blankInputs = listOf("", "   ", "\t", "\n\r  \n")
      for (input in blankInputs) {
        val result = useCase.execute(input, AiProviderType.GEMINI, "dummy_key")
        assertTrue("Expected failure for blank input: '$input'", result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception is IllegalArgumentException)
        assertTrue(exception?.message?.contains("cannot be empty") == true)
      }

      // Verify repository was never contacted
      assertEquals(0, fakeMealRepo.insertedMeals.size)
      assertNull(fakeAiRepo.capturedInputText)
    }

  @Test
  fun `LogMealWithAiUseCase successfully parses meal and persists into repository`() = runBlocking {
    val expectedMeal =
      Meal(
        id = "test-uuid-1",
        foodName = "3 Rotis & 200g Chicken Curry",
        portionDescription = "3 rotis + 200g chicken",
        calories = 580,
        proteinGrams = 48.0f,
        carbsGrams = 45.0f,
        fatGrams = 16.0f,
        mealCategory = "Dinner",
        dateIso = "2026-09-12"
      )
    val expectedAdvice = "Great high-protein dinner! 48g protein hits 27% of your daily goal."

    val fakeAiRepo = FakeAiNutritionRepository(Result.success(Pair(expectedMeal, expectedAdvice)))
    val fakeMealRepo = FakeMealRepository()
    val useCase = LogMealWithAiUseCase(fakeAiRepo, fakeMealRepo)

    val result =
      useCase.execute(
        inputText = "3 rotis with chicken curry",
        provider = AiProviderType.OPENAI,
        apiKey = "sk-test-key-123"
      )

    assertTrue(result.isSuccess)
    val (meal, advice) = result.getOrThrow()
    assertEquals("3 Rotis & 200g Chicken Curry", meal.foodName)
    assertEquals(580, meal.calories)
    assertEquals(48.0f, meal.proteinGrams, 0.01f)
    assertEquals(expectedAdvice, advice)

    // Verify arguments passed to AI repository
    assertEquals("3 rotis with chicken curry", fakeAiRepo.capturedInputText)
    assertEquals(AiProviderType.OPENAI, fakeAiRepo.capturedProvider)
    assertEquals("sk-test-key-123", fakeAiRepo.capturedApiKey)

    // Verify meal was persisted into local repository
    assertEquals(1, fakeMealRepo.insertedMeals.size)
    assertEquals(expectedMeal.id, fakeMealRepo.insertedMeals[0].id)
    assertEquals("3 Rotis & 200g Chicken Curry", fakeMealRepo.insertedMeals[0].foodName)
  }

  @Test
  fun `LogMealWithAiUseCase propagates AI failure and skips local persistence`() = runBlocking {
    val aiError = RuntimeException("Gemini API 503 Service Unavailable")
    val fakeAiRepo = FakeAiNutritionRepository(Result.failure(aiError))
    val fakeMealRepo = FakeMealRepository()
    val useCase = LogMealWithAiUseCase(fakeAiRepo, fakeMealRepo)

    val result =
      useCase.execute(
        inputText = "1 scoop Nakpro whey",
        provider = AiProviderType.GEMINI,
        apiKey = "valid-gemini-key"
      )

    assertTrue(result.isFailure)
    assertEquals(aiError.message, result.exceptionOrNull()?.message)
    // Ensure no partial or corrupted meal was inserted
    assertEquals(0, fakeMealRepo.insertedMeals.size)
  }

  // ==========================================
  // GetDailyNutritionSummaryUseCase Tests
  // ==========================================

  @Test
  fun `GetDailyNutritionSummaryUseCase handles empty meal repository cleanly`() = runBlocking {
    val fakeRepo = FakeMealRepository(initialMeals = emptyList())
    val useCase = GetDailyNutritionSummaryUseCase(fakeRepo)

    val summaries = useCase.execute().first()
    assertTrue(summaries.isEmpty())
  }

  @Test
  fun `GetDailyNutritionSummaryUseCase aggregates multiple meals on the same date accurately`() =
    runBlocking {
      val meals =
        listOf(
          Meal(
            id = "1",
            foodName = "Tarri Poha",
            portionDescription = "1 plate",
            calories = 300,
            proteinGrams = 7.5f,
            carbsGrams = 48.0f,
            fatGrams = 9.0f,
            dateIso = "2026-09-12"
          ),
          Meal(
            id = "2",
            foodName = "Nakpro Whey",
            portionDescription = "1 scoop",
            calories = 125,
            proteinGrams = 24.0f,
            carbsGrams = 3.3f,
            fatGrams = 1.8f,
            dateIso = "2026-09-12"
          ),
          Meal(
            id = "3",
            foodName = "Chicken Curry & 3 Rotis",
            portionDescription = "Lunch",
            calories = 650,
            proteinGrams = 52.0f,
            carbsGrams = 55.0f,
            fatGrams = 20.0f,
            dateIso = "2026-09-12"
          )
        )

      val fakeRepo = FakeMealRepository(initialMeals = meals)
      val useCase = GetDailyNutritionSummaryUseCase(fakeRepo)

      val summaries = useCase.execute(targetCalories = 2300, targetProtein = 180f).first()
      assertEquals(1, summaries.size)

      val daySummary = summaries[0]
      assertEquals("2026-09-12", daySummary.dateIso)
      assertEquals(300 + 125 + 650, daySummary.totalCalories)
      assertEquals(7.5f + 24.0f + 52.0f, daySummary.totalProtein, 0.01f)
      assertEquals(48.0f + 3.3f + 55.0f, daySummary.totalCarbs, 0.01f)
      assertEquals(9.0f + 1.8f + 20.0f, daySummary.totalFat, 0.01f)
      assertEquals(3, daySummary.mealsCount)
      assertEquals(2300, daySummary.targetCalories)
      assertEquals(180f, daySummary.targetProtein, 0.01f)
      assertTrue(daySummary.creatineTaken)
    }

  @Test
  fun `GetDailyNutritionSummaryUseCase groups and chronologically sorts multiple days`() =
    runBlocking {
      val meals =
        listOf(
          Meal(
            id = "1",
            foodName = "Meal Sept 11",
            portionDescription = "Portion",
            calories = 500,
            proteinGrams = 30f,
            carbsGrams = 50f,
            fatGrams = 15f,
            dateIso = "2026-09-11"
          ),
          Meal(
            id = "2",
            foodName = "Meal Sept 09",
            portionDescription = "Portion",
            calories = 400,
            proteinGrams = 25f,
            carbsGrams = 40f,
            fatGrams = 10f,
            dateIso = "2026-09-09"
          ),
          Meal(
            id = "3",
            foodName = "Meal Sept 10",
            portionDescription = "Portion",
            calories = 600,
            proteinGrams = 40f,
            carbsGrams = 60f,
            fatGrams = 18f,
            dateIso = "2026-09-10"
          ),
          Meal(
            id = "4",
            foodName = "Second Meal Sept 09",
            portionDescription = "Portion",
            calories = 300,
            proteinGrams = 20f,
            carbsGrams = 30f,
            fatGrams = 8f,
            dateIso = "2026-09-09"
          )
        )

      val fakeRepo = FakeMealRepository(initialMeals = meals)
      val useCase = GetDailyNutritionSummaryUseCase(fakeRepo)

      val summaries = useCase.execute().first()
      assertEquals(3, summaries.size)

      // Verifies chronological sorting
      assertEquals("2026-09-09", summaries[0].dateIso)
      assertEquals(700, summaries[0].totalCalories) // 400 + 300
      assertEquals(2, summaries[0].mealsCount)

      assertEquals("2026-09-10", summaries[1].dateIso)
      assertEquals(600, summaries[1].totalCalories)
      assertEquals(1, summaries[1].mealsCount)

      assertEquals("2026-09-11", summaries[2].dateIso)
      assertEquals(500, summaries[2].totalCalories)
      assertEquals(1, summaries[2].mealsCount)
    }
}
