package com.calorietracker.app

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.calorietracker.app.data.local.AppDatabase
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Starts the real [MainActivity] with the real Hilt graph (Room, repositories, ViewModels,
 * WorkManager) under Robolectric. If any binding is missing or a ViewModel cannot be created, these
 * fail at launch.
 */
@RunWith(RobolectricTestRunner::class)
// A tall screen so the lazy Today list composes every card.
@Config(sdk = [34], application = CalorieTrackerApp::class, qualifiers = "w411dp-h2400dp")
class AppSmokeTest {
  @get:Rule val compose = createAndroidComposeRule<MainActivity>()

  @After fun resetDatabase() = AppDatabase.resetInstance()

  private fun openTab(label: String) {
    compose.onNode(hasText(label) and hasClickAction()).performClick()
    compose.waitForIdle()
  }

  @Test
  fun `the app starts on the Today screen with the full navigation bar`() {
    compose.onNodeWithText("Log What You Ate").assertExists()
    listOf("Today", "Dietitian", "Analytics", "Logs", "Profile").forEach {
      compose.onNode(hasText(it) and hasClickAction()).assertExists()
    }
  }

  @Test
  fun `a new user sees empty states and the water tracker`() {
    compose.onNodeWithText("Today's Logged Meals (0)").assertExists()
    compose.onNodeWithText("Water").assertExists()
    compose.onNodeWithText("+250 ml").assertExists()
  }

  @Test
  fun `every tab can be opened without crashing`() {
    listOf("Dietitian", "Analytics", "Logs", "Profile", "Today").forEach(::openTab)
    compose.onNodeWithText("Log What You Ate").assertExists()
  }

  @Test
  fun `tapping a water shortcut updates the total`() {
    compose.onNodeWithText("0.00 / 2.5 L").assertExists()
    compose.onNodeWithText("+250 ml").performClick()
    compose.waitUntil(timeoutMillis = 5_000) {
      compose.onAllNodes(hasText("0.25 / 2.5 L")).fetchSemanticsNodes().isNotEmpty()
    }
    compose.onNodeWithText("0.25 / 2.5 L").assertExists()
  }
}
