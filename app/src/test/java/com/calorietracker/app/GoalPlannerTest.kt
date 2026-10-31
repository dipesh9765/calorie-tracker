package com.calorietracker.app

import com.calorietracker.app.data.local.WeightEntity
import com.calorietracker.app.data.model.UserProfile
import com.calorietracker.app.data.repository.DietitianReplyParser
import com.calorietracker.app.data.repository.WeightRepository
import com.calorietracker.app.domain.usecase.ActivityLevel
import com.calorietracker.app.domain.usecase.GoalPlanner
import com.calorietracker.app.domain.usecase.GoalType
import com.calorietracker.app.domain.usecase.PlanInput
import com.calorietracker.app.domain.usecase.toPlanInput
import com.calorietracker.app.domain.usecase.withPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GoalPlannerTest {
  private val planner = GoalPlanner()

  private fun input(
    weight: Float = 80f,
    height: Float = 175f,
    age: Int = 30,
    gender: String = "Male",
    activity: ActivityLevel = ActivityLevel.MODERATE,
    goal: GoalType = GoalType.LOSE,
    target: Float = 72f,
    rate: Float = 0.5f
  ) = PlanInput(weight, height, age, gender, activity, goal, target, rate)

  @Test
  fun `lose plan applies deficit from weekly rate and reports weeks to goal`() {
    val plan = planner.plan(input())
    // BMR = 10*80 + 6.25*175 - 5*30 + 5 = 1749 ; TDEE = 1749 * 1.55 = 2711
    assertEquals(1749, plan.bmr)
    assertEquals(2711, plan.tdee)
    // 0.5 kg/week = 550 kcal/day
    assertEquals(2161, plan.targetCalories)
    assertEquals(16, plan.weeksToGoal)
    assertEquals(145f, plan.proteinGrams, 0.01f) // 1.8 g/kg = 144 -> 145
    assertTrue(plan.warnings.isEmpty())
  }

  @Test
  fun `maintain uses TDEE and has no ETA`() {
    val plan = planner.plan(input(goal = GoalType.MAINTAIN))
    assertEquals(plan.tdee, plan.targetCalories)
    assertNull(plan.weeksToGoal)
  }

  @Test
  fun `gain adds a surplus`() {
    val plan = planner.plan(input(goal = GoalType.GAIN, target = 85f, rate = 0.25f))
    assertEquals(plan.tdee + 275, plan.targetCalories)
  }

  @Test
  fun `aggressive pace is capped and explained`() {
    val plan = planner.plan(input(weight = 60f, target = 50f, rate = 1.0f))
    // cap = 1% of 60 kg = 0.6 kg/week
    assertTrue(plan.warnings.any { it.contains("pace was reduced") })
    val uncapped = planner.plan(input(weight = 60f, target = 50f, rate = 0.6f))
    assertEquals(uncapped.targetCalories, plan.targetCalories)
  }

  @Test
  fun `calorie floor protects small users`() {
    val plan =
      planner.plan(
        input(
          weight = 50f,
          height = 155f,
          age = 50,
          gender = "Female",
          activity = ActivityLevel.SEDENTARY,
          target = 45f,
          rate = 0.5f
        )
      )
    assertEquals(1200, plan.targetCalories)
    assertTrue(plan.warnings.any { it.contains("1200") })
  }

  @Test
  fun `lose goal with target above current weight falls back to maintenance`() {
    val plan = planner.plan(input(target = 90f))
    assertEquals(plan.tdee, plan.targetCalories)
    assertNull(plan.weeksToGoal)
    assertTrue(plan.warnings.isNotEmpty())
  }

  @Test
  fun `withPlan copies generated targets into the profile`() {
    val profile =
      UserProfile(name = "A", weightKg = 80f, heightCm = 175f, age = 30, targetWeightKg = 72f)
    val updated = profile.withPlan(planner.plan(profile.toPlanInput()))
    assertTrue(updated.targetDailyCalories != profile.targetDailyCalories)
    assertEquals("A", updated.name)
  }

  @Test
  fun `displayName falls back when name is blank`() {
    assertEquals("there", UserProfile().displayName)
    assertEquals("Asha", UserProfile(name = " Asha ").displayName)
  }

  @Test
  fun `weight trend summary describes change over readings`() {
    val readings =
      listOf(
        WeightEntity(3, "2026-10-26", 81.0f, 3),
        WeightEntity(2, "2026-10-19", 82.0f, 2),
        WeightEntity(1, "2026-10-12", 83.0f, 1)
      )
    val text = WeightRepository.describeTrend(readings)
    assertTrue(text.contains("81.0 kg"))
    assertTrue(text.contains("-2.0 kg"))
    assertEquals("No weigh-ins logged.", WeightRepository.describeTrend(emptyList()))
  }

  @Test
  fun `reply parser reads reported weight and ignores implausible values`() {
    val ok =
      DietitianReplyParser.parse(
        """{"status":"CHAT","message":"Nice","weightKg":82.4}""",
        "2026-10-26"
      )
    assertEquals(82.4f, ok.weightKg!!, 0.001f)
    val bad =
      DietitianReplyParser.parse("""{"status":"CHAT","message":"?","weightKg":5}""", "2026-10-26")
    assertNull(bad.weightKg)
    assertNotNull(ok)
  }
}
