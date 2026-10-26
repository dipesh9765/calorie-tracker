package com.calorietracker.app

import com.calorietracker.app.data.model.UserProfile
import com.calorietracker.app.domain.model.AiProviderType
import com.calorietracker.app.domain.usecase.LogMealWithAiUseCase
import com.calorietracker.app.support.FIXED_CLOCK
import com.calorietracker.app.support.FakeAiNutritionRepository
import com.calorietracker.app.support.FakeMealRepository
import com.calorietracker.app.support.TODAY
import com.calorietracker.app.support.YESTERDAY
import com.calorietracker.app.support.meal
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogMealWithAiUseCaseTest {
  private val profile =
    UserProfile(
      primaryAiProvider = "OpenAI",
      openAiApiKey = "  sk-test  ",
      openAiModel = " gpt-test ",
      geminiApiKey = "other-key"
    )

  private fun useCase(ai: FakeAiNutritionRepository, meals: FakeMealRepository) =
    LogMealWithAiUseCase(ai, meals, FIXED_CLOCK)

  @Test
  fun `blank input fails without calling the AI or touching storage`() = runBlocking {
    val ai = FakeAiNutritionRepository(Result.success(meal() to "ok"))
    val meals = FakeMealRepository()

    val result = useCase(ai, meals).execute("   ", profile)

    assertTrue(result.isFailure)
    assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    assertEquals(0, ai.calls)
    assertTrue(meals.meals.value.isEmpty())
  }

  @Test
  fun `uses the selected provider key and model from the profile`() = runBlocking {
    val ai = FakeAiNutritionRepository(Result.success(meal() to "ok"))

    useCase(ai, FakeMealRepository()).execute("2 rotis", profile)

    assertEquals(AiProviderType.OPENAI, ai.lastProvider)
    assertEquals("sk-test", ai.lastApiKey)
    assertEquals("gpt-test", ai.lastModel)
    assertEquals("2 rotis", ai.lastMealText)
  }

  @Test
  fun `today and yesterday meals are passed to the AI as context`() = runBlocking {
    val meals =
      FakeMealRepository(listOf(meal("Poha", TODAY, 300), meal("Dal rice", YESTERDAY, 550)))
    val ai = FakeAiNutritionRepository(Result.success(meal() to "ok"))

    useCase(ai, meals).execute("tea", profile)

    assertTrue(ai.lastTodayContext!!.contains("Poha"))
    assertTrue(ai.lastYesterdayContext!!.contains("Dal rice"))
  }

  @Test
  fun `logging the same food twice keeps both entries`() = runBlocking {
    val meals = FakeMealRepository()
    val ai = FakeAiNutritionRepository(Result.success(meal("2 Rotis", id = "lunch") to "ok"))
    val useCase = useCase(ai, meals)

    useCase.execute("2 rotis for lunch", profile)
    ai.result = Result.success(meal("2 Rotis", id = "dinner") to "ok")
    val second = useCase.execute("2 rotis for dinner", profile)

    assertFalse(second.getOrThrow().wasUpdate)
    assertEquals(setOf("lunch", "dinner"), meals.meals.value.map { it.id }.toSet())
  }

  @Test
  fun `an AI update replaces the matching meal on that day`() = runBlocking {
    val meals = FakeMealRepository(listOf(meal("2 Rotis", TODAY, 180, id = "existing")))
    val updated = meal("2 rotis", TODAY, 270, id = "temp", action = "UPDATE")
    val ai = FakeAiNutritionRepository(Result.success(updated to "bigger portion"))

    val logged = useCase(ai, meals).execute("actually 3 rotis", profile).getOrThrow()

    assertTrue(logged.wasUpdate)
    assertEquals("existing", logged.meal.id)
    assertEquals(1, meals.meals.value.size)
    assertEquals(270, meals.meals.value.single().calories)
  }

  @Test
  fun `an AI update with no match is saved as a new meal`() = runBlocking {
    val meals = FakeMealRepository()
    val updated = meal("Paneer", TODAY, 400, id = "new", action = "UPDATE")
    val ai = FakeAiNutritionRepository(Result.success(updated to "ok"))

    useCase(ai, meals).execute("paneer", profile)

    assertEquals(listOf("new"), meals.meals.value.map { it.id })
  }

  @Test
  fun `an AI failure is returned and nothing is saved`() = runBlocking {
    val meals = FakeMealRepository()
    val ai = FakeAiNutritionRepository(Result.failure(RuntimeException("rate limited")))

    val result = useCase(ai, meals).execute("anything", profile)

    assertTrue(result.isFailure)
    assertEquals("rate limited", result.exceptionOrNull()?.message)
    assertTrue(meals.meals.value.isEmpty())
  }

  @Test
  fun `the coach advice is returned with the saved meal`() = runBlocking {
    val ai = FakeAiNutritionRepository(Result.success(meal() to "Add more protein"))

    val logged = useCase(ai, FakeMealRepository()).execute("rotis", profile).getOrThrow()

    assertEquals("Add more protein", logged.coachAdvice)
  }
}
