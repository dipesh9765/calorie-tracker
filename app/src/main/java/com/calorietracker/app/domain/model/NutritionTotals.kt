package com.calorietracker.app.domain.model

/** Summed calories and macros for a group of meals. */
data class NutritionTotals(
  val calories: Int = 0,
  val protein: Float = 0f,
  val carbs: Float = 0f,
  val fat: Float = 0f
)

fun List<Meal>.totals(): NutritionTotals =
  NutritionTotals(
    calories = sumOf { it.calories },
    protein = sumOf { it.proteinGrams.toDouble() }.toFloat(),
    carbs = sumOf { it.carbsGrams.toDouble() }.toFloat(),
    fat = sumOf { it.fatGrams.toDouble() }.toFloat()
  )

/** Daily carb and fat goals derived from the calorie and protein targets. */
data class MacroTargets(val carbsGrams: Float, val fatGrams: Float) {
  companion object {
    private const val KCAL_PER_G_PROTEIN = 4f
    private const val KCAL_PER_G_CARB = 4f
    private const val KCAL_PER_G_FAT = 9f
    private const val FAT_SHARE_OF_CALORIES = 0.25f

    /** Fat takes 25% of calories; carbs get whatever is left after protein and fat. */
    fun from(calories: Int, proteinGrams: Float): MacroTargets {
      val fat = calories * FAT_SHARE_OF_CALORIES / KCAL_PER_G_FAT
      val carbCalories = calories - proteinGrams * KCAL_PER_G_PROTEIN - fat * KCAL_PER_G_FAT
      return MacroTargets(
        carbsGrams = (carbCalories / KCAL_PER_G_CARB).coerceAtLeast(0f),
        fatGrams = fat
      )
    }
  }
}
