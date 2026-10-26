package com.calorietracker.app.domain.usecase

import com.calorietracker.app.data.model.UserProfile
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

enum class ActivityLevel(val label: String, val description: String, val factor: Float) {
  SEDENTARY("Sedentary", "Desk job, little exercise", 1.2f),
  LIGHT("Light", "Exercise 1-3 days/week", 1.375f),
  MODERATE("Moderate", "Exercise 3-5 days/week", 1.55f),
  ACTIVE("Active", "Hard exercise 6-7 days/week", 1.725f),
  VERY_ACTIVE("Very active", "Physical job plus training", 1.9f);

  companion object {
    fun fromName(name: String): ActivityLevel = entries.find { it.name == name } ?: MODERATE
  }
}

enum class GoalType(val label: String) {
  LOSE("Lose weight"),
  MAINTAIN("Maintain"),
  GAIN("Gain weight");

  companion object {
    fun fromName(name: String): GoalType = entries.find { it.name == name } ?: MAINTAIN
  }
}

data class PlanInput(
  val weightKg: Float,
  val heightCm: Float,
  val age: Int,
  val gender: String,
  val activity: ActivityLevel,
  val goal: GoalType,
  val targetWeightKg: Float,
  val weeklyRateKg: Float
)

data class NutritionPlan(
  val bmr: Int,
  val tdee: Int,
  val targetCalories: Int,
  val proteinGrams: Float,
  val waterLiters: Float,
  /** Null for MAINTAIN or when the goal and target weight point in different directions. */
  val weeksToGoal: Int?,
  val warnings: List<String>
)

fun UserProfile.toPlanInput() =
  PlanInput(
    weightKg = weightKg,
    heightCm = heightCm,
    age = age,
    gender = gender,
    activity = ActivityLevel.fromName(activityLevel),
    goal = GoalType.fromName(goalType),
    targetWeightKg = targetWeightKg,
    weeklyRateKg = weeklyRateKg
  )

/** Copies the generated targets into the profile. */
fun UserProfile.withPlan(plan: NutritionPlan) =
  copy(
    bmrCalories = plan.bmr,
    tdeeMaintenanceCalories = plan.tdee,
    targetDailyCalories = plan.targetCalories,
    targetDailyProteinGrams = plan.proteinGrams,
    targetWaterLiters = plan.waterLiters
  )

/**
 * Turns body metrics and a goal into daily calorie, protein and water targets.
 *
 * Calories = TDEE adjusted by the requested weekly rate (7,700 kcal per kg), with a floor and a
 * safe-rate cap so the plan can't recommend crash dieting. Every adjustment is reported in
 * [NutritionPlan.warnings] so the UI can explain it.
 */
class GoalPlanner(private val calculator: NutritionCalculator = NutritionCalculator()) {

  fun plan(input: PlanInput): NutritionPlan {
    val warnings = mutableListOf<String>()
    val bmr = calculator.calculateBmr(input.weightKg, input.heightCm, input.age, input.gender)
    val tdee = (bmr * input.activity.factor).roundToInt()

    var rate = input.weeklyRateKg.coerceAtLeast(0f)
    var effectiveGoal = input.goal
    if (effectiveGoal == GoalType.LOSE && input.targetWeightKg >= input.weightKg) {
      warnings += "Your target weight is not below your current weight, so no deficit was applied."
      effectiveGoal = GoalType.MAINTAIN
    }
    if (effectiveGoal == GoalType.GAIN && input.targetWeightKg <= input.weightKg) {
      warnings += "Your target weight is not above your current weight, so no surplus was applied."
      effectiveGoal = GoalType.MAINTAIN
    }

    if (effectiveGoal == GoalType.LOSE) {
      val maxRate = (input.weightKg * MAX_LOSS_FRACTION_PER_WEEK).coerceAtMost(1.0f)
      if (rate > maxRate) {
        warnings += "Losing more than ${"%.2f".format(maxRate)} kg/week is not recommended at your weight; the pace was reduced."
        rate = maxRate
      }
    }
    if (effectiveGoal == GoalType.GAIN && rate > MAX_GAIN_PER_WEEK) {
      warnings += "Gaining more than $MAX_GAIN_PER_WEEK kg/week mostly adds fat; the pace was reduced."
      rate = MAX_GAIN_PER_WEEK
    }

    val dailyAdjustment = (rate * KCAL_PER_KG / 7f).roundToInt()
    var target =
      when (effectiveGoal) {
        GoalType.LOSE -> tdee - dailyAdjustment
        GoalType.GAIN -> tdee + dailyAdjustment
        GoalType.MAINTAIN -> tdee
      }

    val floor = if (input.gender.equals("female", ignoreCase = true)) FLOOR_FEMALE else FLOOR_MALE
    if (target < floor) {
      warnings += "Calories were raised to $floor kcal, the minimum generally considered safe without medical supervision."
      target = floor
    }

    val proteinPerKg =
      when (effectiveGoal) {
        GoalType.LOSE -> 1.8f
        GoalType.GAIN -> 1.8f
        GoalType.MAINTAIN -> 1.6f
      }
    val protein = ((input.weightKg * proteinPerKg) / 5f).roundToInt() * 5f
    val water = ((input.weightKg * 0.035f) * 10f).roundToInt() / 10f

    val weeks =
      if (effectiveGoal != GoalType.MAINTAIN && rate > 0f) {
        ceil(abs(input.targetWeightKg - input.weightKg) / rate).toInt()
      } else {
        null
      }

    return NutritionPlan(bmr, tdee, target, protein, water, weeks, warnings)
  }

  companion object {
    const val KCAL_PER_KG = 7700f
    private const val MAX_LOSS_FRACTION_PER_WEEK = 0.01f
    private const val MAX_GAIN_PER_WEEK = 0.5f
    private const val FLOOR_FEMALE = 1200
    private const val FLOOR_MALE = 1500
  }
}
