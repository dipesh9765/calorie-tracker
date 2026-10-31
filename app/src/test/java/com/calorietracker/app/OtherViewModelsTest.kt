package com.calorietracker.app

import app.cash.turbine.test
import com.calorietracker.app.data.model.UserProfile
import com.calorietracker.app.data.repository.MemoryRepository
import com.calorietracker.app.data.repository.WeightRepository
import com.calorietracker.app.domain.usecase.GetDailyNutritionSummaryUseCase
import com.calorietracker.app.presentation.UiEffect
import com.calorietracker.app.presentation.history.HistoryViewModel
import com.calorietracker.app.presentation.memory.MemoryViewModel
import com.calorietracker.app.presentation.profile.ProfileViewModel
import com.calorietracker.app.support.FakeDailyLogRepository
import com.calorietracker.app.support.FakeMealExporter
import com.calorietracker.app.support.FakeMealRepository
import com.calorietracker.app.support.FakeMemoryDao
import com.calorietracker.app.support.FakeUserProfileRepository
import com.calorietracker.app.support.FakeWeightDao
import com.calorietracker.app.support.MainDispatcherRule
import com.calorietracker.app.support.TODAY
import com.calorietracker.app.support.YESTERDAY
import com.calorietracker.app.support.meal
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HistoryViewModelTest {
  @get:Rule val mainDispatcher = MainDispatcherRule()

  private val meals =
    FakeMealRepository(
      listOf(meal("Poha", TODAY, 300, id = "a"), meal("Dal", YESTERDAY, 500, id = "b"))
    )
  private val profile = FakeUserProfileRepository(UserProfile(targetDailyCalories = 2000))

  private fun viewModel() =
    HistoryViewModel(
      mealRepository = meals,
      getSummaries = GetDailyNutritionSummaryUseCase(meals, FakeDailyLogRepository()),
      profileRepository = profile
    )

  @Test
  fun `exposes every meal and a summary per day`() = runTest {
    viewModel().uiState.test {
      var state = awaitItem()
      while (state.summaries.size < 2) state = awaitItem()
      assertEquals(2, state.allMeals.size)
      assertEquals(listOf(YESTERDAY, TODAY), state.summaries.map { it.dateIso })
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `changing the calorie target recalculates the summaries`() = runTest {
    val vm = viewModel()
    vm.uiState.test {
      var state = awaitItem()
      while (state.summaries.isEmpty()) state = awaitItem()
      assertEquals(2000, state.summaries.first().targetCalories)

      profile.update(UserProfile(targetDailyCalories = 1600))
      while (state.summaries.firstOrNull()?.targetCalories != 1600) state = awaitItem()
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `delete emits an undo effect and undo restores the meal`() = runTest {
    val vm = viewModel()
    val poha = meals.meals.value.first { it.id == "a" }

    vm.effects.test {
      vm.deleteMeal(poha)
      assertEquals(UiEffect.MealDeleted(poha), awaitItem())
      cancelAndIgnoreRemainingEvents()
    }
    assertEquals(listOf("b"), meals.meals.value.map { it.id })

    vm.undoDelete(poha)
    assertEquals(setOf("a", "b"), meals.meals.value.map { it.id }.toSet())
  }
}

class ProfileViewModelTest {
  @get:Rule val mainDispatcher = MainDispatcherRule()

  private val profile = FakeUserProfileRepository()
  private val weightDao = FakeWeightDao()
  private val meals = FakeMealRepository(listOf(meal("Poha"), meal("Dal")))
  private val exporter = FakeMealExporter()

  private fun viewModel() =
    ProfileViewModel(
      profileRepository = profile,
      mealRepository = meals,
      exporter = exporter,
      weights = WeightRepository(weightDao)
    )

  @Test
  fun `saving stores the profile and confirms`() = runTest {
    val vm = viewModel()
    vm.effects.test {
      vm.save(UserProfile(name = "Asha", profileConfigured = true, weightKg = 72f))
      assertEquals("Profile saved", (awaitItem() as UiEffect.Message).text)
      cancelAndIgnoreRemainingEvents()
    }
    assertEquals("Asha", profile.profile.value.name)
  }

  @Test
  fun `a configured profile logs the weigh-in once and an unconfigured one does not`() = runTest {
    val vm = viewModel()

    vm.save(UserProfile(profileConfigured = false, weightKg = 80f))
    assertTrue(weightDao.rows.value.isEmpty())

    vm.save(UserProfile(profileConfigured = true, weightKg = 72f))
    vm.save(UserProfile(profileConfigured = true, weightKg = 72f))
    assertEquals(listOf(72f), weightDao.rows.value.map { it.weightKg })
  }

  @Test
  fun `export reports the file name on success and failure otherwise`() = runTest {
    val vm = viewModel()
    vm.effects.test {
      vm.exportCsv()
      assertEquals("Exported CSV to export.csv", (awaitItem() as UiEffect.Message).text)

      exporter.succeed = false
      vm.exportJson()
      assertEquals("Export failed", (awaitItem() as UiEffect.Message).text)
      cancelAndIgnoreRemainingEvents()
    }
    assertEquals(2, exporter.lastMeals.size)
  }
}

class MemoryViewModelTest {
  @get:Rule val mainDispatcher = MainDispatcherRule()

  private fun viewModel() = MemoryViewModel(MemoryRepository(FakeMemoryDao()))

  @Test
  fun `add, edit and delete are reflected in the list`() = runTest {
    val vm = viewModel()
    vm.memories.test {
      assertTrue(awaitItem().isEmpty())

      vm.add("SUPPLEMENT", "Whey, 33 g scoop")
      var list = awaitItem()
      while (list.isEmpty()) list = awaitItem()
      assertEquals("Whey, 33 g scoop", list.single().content)

      vm.edit(list.single().id, "SUPPLEMENT", "Whey, 40 g scoop")
      while (list.single().content != "Whey, 40 g scoop") list = awaitItem()

      vm.delete(list.single().id)
      while (list.isNotEmpty()) list = awaitItem()
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `blank content is ignored`() = runTest {
    val vm = viewModel()
    vm.memories.test {
      assertTrue(awaitItem().isEmpty())
      vm.add("HABIT", "   ")
      expectNoEvents()
      cancelAndIgnoreRemainingEvents()
    }
  }
}
