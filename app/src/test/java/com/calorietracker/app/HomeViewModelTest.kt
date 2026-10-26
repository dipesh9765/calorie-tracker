package com.calorietracker.app

import app.cash.turbine.test
import com.calorietracker.app.data.model.UserProfile
import com.calorietracker.app.data.repository.MemoryRepository
import com.calorietracker.app.domain.usecase.LogMealWithAiUseCase
import com.calorietracker.app.presentation.UiEffect
import com.calorietracker.app.presentation.home.HomeUiEvent
import com.calorietracker.app.presentation.home.HomeViewModel
import com.calorietracker.app.support.FIXED_CLOCK
import com.calorietracker.app.support.FakeAiNutritionRepository
import com.calorietracker.app.support.FakeDailyLogRepository
import com.calorietracker.app.support.FakeMealRepository
import com.calorietracker.app.support.FakeMemoryDao
import com.calorietracker.app.support.FakeUserProfileRepository
import com.calorietracker.app.support.MainDispatcherRule
import com.calorietracker.app.support.TODAY
import com.calorietracker.app.support.YESTERDAY
import com.calorietracker.app.support.meal
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HomeViewModelTest {
  @get:Rule val mainDispatcher = MainDispatcherRule()

  private val meals = FakeMealRepository()
  private val logs = FakeDailyLogRepository()
  private val ai = FakeAiNutritionRepository(Result.success(meal("Poha", calories = 300) to "tip"))
  private val profile =
    FakeUserProfileRepository(UserProfile(geminiApiKey = "k", targetDailyCalories = 2000))

  private fun viewModel() =
    HomeViewModel(
      logMealWithAi = LogMealWithAiUseCase(ai, meals, FIXED_CLOCK),
      mealRepository = meals,
      dailyLogRepository = logs,
      memoryRepository = MemoryRepository(FakeMemoryDao()),
      profileRepository = profile,
      clock = FIXED_CLOCK
    )

  @Test
  fun `state shows only today's meals with their totals`() = runTest {
    meals.insertMeal(meal("Poha", TODAY, 300, protein = 8f))
    meals.insertMeal(meal("Dal", TODAY, 250, protein = 14f))
    meals.insertMeal(meal("Biryani", YESTERDAY, 700))

    viewModel().uiState.test {
      var state = awaitItem()
      while (state.todayMeals.size < 2) state = awaitItem()
      assertEquals(550, state.totals.calories)
      assertEquals(22f, state.totals.protein, 0.001f)
      assertEquals(setOf("Poha", "Dal"), state.todayMeals.map { it.foodName }.toSet())
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `submitting a meal saves it, shows the advice and reports success`() = runTest {
    val vm = viewModel()
    vm.uiState.test {
      vm.effects.test {
        vm.onEvent(HomeUiEvent.SubmitMealInput("poha"))

        val effect = awaitItem() as UiEffect.Message
        assertEquals("Saved ($TODAY): Poha", effect.text)
        cancelAndIgnoreRemainingEvents()
      }
      var state = awaitItem()
      while (state.coachAdvice.isEmpty()) state = awaitItem()
      assertEquals("tip", state.coachAdvice)
      assertFalse(state.isLoadingAi)
      cancelAndIgnoreRemainingEvents()
    }
    assertEquals(listOf("Poha"), meals.meals.value.map { it.foodName })
  }

  @Test
  fun `an AI update is reported as Updated`() = runTest {
    meals.insertMeal(meal("Poha", TODAY, 300, id = "existing"))
    ai.result = Result.success(meal("poha", TODAY, 400, action = "UPDATE") to "bigger")
    val vm = viewModel()

    vm.effects.test {
      vm.onEvent(HomeUiEvent.SubmitMealInput("make that bigger"))
      assertEquals("Updated ($TODAY): poha", (awaitItem() as UiEffect.Message).text)
      cancelAndIgnoreRemainingEvents()
    }
    assertEquals(1, meals.meals.value.size)
    assertEquals(400, meals.meals.value.single().calories)
  }

  @Test
  fun `a failed AI call reports the problem and saves nothing`() = runTest {
    ai.result = Result.failure(RuntimeException("rate limited"))
    val vm = viewModel()

    vm.effects.test {
      vm.onEvent(HomeUiEvent.SubmitMealInput("anything"))
      val effect = awaitItem() as UiEffect.Message
      assertTrue(effect.text.contains("rate limited"))
      assertTrue(effect.long)
      cancelAndIgnoreRemainingEvents()
    }
    assertTrue(meals.meals.value.isEmpty())
  }

  @Test
  fun `deleting a meal removes it and offers undo, which restores it`() = runTest {
    val poha = meal("Poha", TODAY, 300, id = "p1")
    meals.insertMeal(poha)
    val vm = viewModel()

    vm.effects.test {
      vm.onEvent(HomeUiEvent.DeleteMeal(poha))
      assertEquals(UiEffect.MealDeleted(poha), awaitItem())
      cancelAndIgnoreRemainingEvents()
    }
    assertTrue(meals.meals.value.isEmpty())

    vm.onEvent(HomeUiEvent.UndoDelete(poha))
    assertEquals(listOf("p1"), meals.meals.value.map { it.id })
  }

  @Test
  fun `creatine toggles and is saved for today`() = runTest {
    val vm = viewModel()
    vm.uiState.test {
      awaitItem()
      vm.onEvent(HomeUiEvent.ToggleCreatine)
      var state = awaitItem()
      while (!state.dailyLog.creatineTaken) state = awaitItem()
      assertTrue(logs.logs.value.getValue(TODAY).creatineTaken)

      vm.onEvent(HomeUiEvent.ToggleCreatine)
      while (state.dailyLog.creatineTaken) state = awaitItem()
      assertFalse(logs.logs.value.getValue(TODAY).creatineTaken)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `water accumulates and never goes below zero`() = runTest {
    val vm = viewModel()
    vm.uiState.test {
      awaitItem()
      vm.onEvent(HomeUiEvent.AddWater(250))
      vm.onEvent(HomeUiEvent.AddWater(500))
      var state = awaitItem()
      while (state.dailyLog.waterMl < 750) state = awaitItem()
      assertEquals(0.75f, state.dailyLog.waterLiters, 0.001f)

      vm.onEvent(HomeUiEvent.AddWater(-5000))
      while (state.dailyLog.waterMl != 0) state = awaitItem()
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `profile changes flow into the state`() = runTest {
    val vm = viewModel()
    vm.uiState.test {
      awaitItem()
      profile.update(UserProfile(name = "Asha", targetDailyCalories = 1800))
      var state = awaitItem()
      while (state.profile.name != "Asha") state = awaitItem()
      assertEquals(1800, state.profile.targetDailyCalories)
      cancelAndIgnoreRemainingEvents()
    }
  }
}
