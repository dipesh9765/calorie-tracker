package com.calorietracker.app.domain.usecase

import com.calorietracker.app.data.model.UserProfile
import kotlin.math.roundToInt

class NutritionCalculator {

    /**
     * Mifflin-St Jeor Equation:
     * Men: BMR = (10 × weight in kg) + (6.25 × height in cm) - (5 × age in years) + 5
     * Women: BMR = (10 × weight in kg) + (6.25 × height in cm) - (5 × age in years) - 161
     */
    fun calculateBmr(weightKg: Float, heightCm: Float, age: Int, gender: String): Int {
        val genderOffset = if (gender.lowercase() == "female") -161 else 5
        return ((10 * weightKg) + (6.25 * heightCm) - (5 * age) + genderOffset).roundToInt()
    }

    /**
     * TDEE Activity Multipliers:
     * Sedentary / Office job + Gym 4-5 days = 1.375 - 1.4
     */
    fun calculateTdee(bmr: Int, gymDaysPerWeek: Int): Int {
        val multiplier = when {
            gymDaysPerWeek <= 1 -> 1.2f
            gymDaysPerWeek in 2..3 -> 1.3f
            gymDaysPerWeek in 4..5 -> 1.38f
            else -> 1.55f
        }
        return (bmr * multiplier).roundToInt()
    }

    fun calculateTargetCalories(tdee: Int, targetDeficit: Int = 600): Int {
        return (tdee - targetDeficit).coerceAtLeast(1500)
    }

    /**
     * Protein: ~1.8g to 2.0g per kg bodyweight
     */
    fun calculateProteinTarget(weightKg: Float): Float {
        return (weightKg * 1.8f).roundToInt().toFloat().coerceAtLeast(140f)
    }
}
