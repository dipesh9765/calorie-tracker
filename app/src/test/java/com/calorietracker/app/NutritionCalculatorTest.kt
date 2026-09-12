package com.calorietracker.app

import com.calorietracker.app.domain.usecase.NutritionCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Comprehensive Unit Test suite covering [NutritionCalculator] business rules, Mifflin-St Jeor
 * metabolic calculations, TDEE activity multipliers, calorie deficit boundary constraints, and
 * protein target floors.
 */
class NutritionCalculatorTest {

  private lateinit var calculator: NutritionCalculator

  @Before
  fun setUp() {
    calculator = NutritionCalculator()
  }

  // ==========================================
  // BMR (Mifflin-St Jeor) Tests
  // ==========================================

  @Test
  fun `calculateBmr for standard male baseline calculates accurately`() {
    // Dipesh baseline: 105 kg, 188 cm, 25 yrs, Male
    // BMR = (10 * 105) + (6.25 * 188) - (5 * 25) + 5
    //     = 1050 + 1175 - 125 + 5 = 2105
    val bmr = calculator.calculateBmr(weightKg = 105f, heightCm = 188f, age = 25, gender = "Male")
    assertEquals(2105, bmr)
  }

  @Test
  fun `calculateBmr for standard female profile calculates accurately`() {
    // Female: 65 kg, 165 cm, 28 yrs
    // BMR = (10 * 65) + (6.25 * 165) - (5 * 28) - 161
    //     = 650 + 1031.25 - 140 - 161 = 1380.25 -> 1380
    val bmr = calculator.calculateBmr(weightKg = 65f, heightCm = 165f, age = 28, gender = "Female")
    assertEquals(1380, bmr)
  }

  @Test
  fun `calculateBmr handles case variations for gender`() {
    val bmrLower = calculator.calculateBmr(70f, 170f, 30, "female")
    val bmrUpper = calculator.calculateBmr(70f, 170f, 30, "FEMALE")
    val bmrMixed = calculator.calculateBmr(70f, 170f, 30, "FeMaLe")

    assertEquals(bmrLower, bmrUpper)
    assertEquals(bmrLower, bmrMixed)

    val bmrMaleLower = calculator.calculateBmr(70f, 170f, 30, "male")
    val bmrMaleUpper = calculator.calculateBmr(70f, 170f, 30, "MALE")
    assertEquals(bmrMaleLower, bmrMaleUpper)
  }

  @Test
  fun `calculateBmr non-female strings default to male offset`() {
    // Non-female (e.g. "Other", "Prefer not to say", empty) defaults to +5 offset
    val bmrUnknown = calculator.calculateBmr(80f, 180f, 25, "Other")
    val bmrMale = calculator.calculateBmr(80f, 180f, 25, "Male")
    assertEquals(bmrMale, bmrUnknown)
  }

  @Test
  fun `calculateBmr with boundary and extreme values`() {
    // High bodyweight & tall height
    val bmrHeavy = calculator.calculateBmr(180f, 210f, 20, "Male")
    assertTrue(bmrHeavy > 2500)

    // Low bodyweight & short height
    val bmrLight = calculator.calculateBmr(40f, 140f, 60, "Female")
    assertTrue(bmrLight > 0)
    assertTrue(bmrLight < 1000)
  }

  // ==========================================
  // TDEE Activity Multiplier Tests
  // ==========================================

  @Test
  fun `calculateTdee applies correct multipliers across gym day ranges`() {
    val bmr = 2000

    // <= 1 day: 1.2x (Sedentary)
    assertEquals((2000 * 1.2f).toInt(), calculator.calculateTdee(bmr, 0))
    assertEquals((2000 * 1.2f).toInt(), calculator.calculateTdee(bmr, 1))

    // 2..3 days: 1.3x (Light)
    assertEquals((2000 * 1.3f).toInt(), calculator.calculateTdee(bmr, 2))
    assertEquals((2000 * 1.3f).toInt(), calculator.calculateTdee(bmr, 3))

    // 4..5 days: 1.38x (Moderate)
    assertEquals((2000 * 1.38f).toInt(), calculator.calculateTdee(bmr, 4))
    assertEquals((2000 * 1.38f).toInt(), calculator.calculateTdee(bmr, 5))

    // >= 6 days: 1.55x (Heavy)
    assertEquals((2000 * 1.55f).toInt(), calculator.calculateTdee(bmr, 6))
    assertEquals((2000 * 1.55f).toInt(), calculator.calculateTdee(bmr, 7))
  }

  @Test
  fun `calculateTdee handles negative gym days gracefully`() {
    val bmr = 2000
    // gymDays <= 1 covers negative inputs
    assertEquals(2400, calculator.calculateTdee(bmr, -2))
  }

  // ==========================================
  // Calorie Deficit & Target Tests
  // ==========================================

  @Test
  fun `calculateTargetCalories applies default 600 deficit`() {
    val tdee = 2900
    val target = calculator.calculateTargetCalories(tdee)
    assertEquals(2300, target)
  }

  @Test
  fun `calculateTargetCalories applies custom deficit`() {
    val tdee = 2800
    assertEquals(2400, calculator.calculateTargetCalories(tdee, targetDeficit = 400))
    assertEquals(2000, calculator.calculateTargetCalories(tdee, targetDeficit = 800))
  }

  @Test
  fun `calculateTargetCalories enforces 1500 kcal safety floor`() {
    // High deficit would result in 1800 - 600 = 1200, but floor is 1500
    val targetLow = calculator.calculateTargetCalories(tdee = 1800, targetDeficit = 600)
    assertEquals(1500, targetLow)

    // Extremely low TDEE
    val targetExtreme = calculator.calculateTargetCalories(tdee = 1200, targetDeficit = 500)
    assertEquals(1500, targetExtreme)
  }

  @Test
  fun `calculateTargetCalories supports surplus or maintenance`() {
    val tdee = 2500
    // Deficit of 0 = Maintenance
    assertEquals(2500, calculator.calculateTargetCalories(tdee, targetDeficit = 0))
    // Negative deficit = Bulking Surplus
    assertEquals(2800, calculator.calculateTargetCalories(tdee, targetDeficit = -300))
  }

  // ==========================================
  // Protein Target Tests
  // ==========================================

  @Test
  fun `calculateProteinTarget calculates 1_8x bodyweight rounded`() {
    // 105 kg * 1.8 = 189.0 -> 189f
    assertEquals(189f, calculator.calculateProteinTarget(105f), 0.01f)

    // 90 kg * 1.8 = 162.0 -> 162f
    assertEquals(162f, calculator.calculateProteinTarget(90f), 0.01f)
  }

  @Test
  fun `calculateProteinTarget enforces 140g minimum floor`() {
    // 60 kg * 1.8 = 108 -> coerced to 140f
    assertEquals(140f, calculator.calculateProteinTarget(60f), 0.01f)

    // 50 kg * 1.8 = 90 -> coerced to 140f
    assertEquals(140f, calculator.calculateProteinTarget(50f), 0.01f)

    // 77.77 kg * 1.8 = 139.986 -> rounded to 140 -> 140f
    assertEquals(140f, calculator.calculateProteinTarget(77.77f), 0.01f)
  }
}
