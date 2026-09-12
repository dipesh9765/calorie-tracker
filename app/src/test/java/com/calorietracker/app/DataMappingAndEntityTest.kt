package com.calorietracker.app

import com.calorietracker.app.data.local.MealEntity
import com.calorietracker.app.data.local.toMealEntity
import com.calorietracker.app.data.local.toMealEntry
import com.calorietracker.app.data.mapper.toDomain
import com.calorietracker.app.data.mapper.toEntity
import com.calorietracker.app.data.model.MealEntry
import com.calorietracker.app.domain.model.Meal
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit Test suite verifying bidirectional entity mappings, domain model conversions, field
 * preservation, Unicode/Devanagari text integrity, and default value propagation.
 */
class DataMappingAndEntityTest {

  @Test
  fun `MealEntry to MealEntity preserves all fields exactly`() {
    val entry =
      MealEntry(
        id = "uuid-entry-123",
        foodName = "Saoji Chicken Curry & 3 Chapatis",
        portionDescription = "150g chicken curry + 3 whole wheat rotis",
        calories = 520,
        proteinGrams = 42.5f,
        carbsGrams = 46.0f,
        fatGrams = 18.2f,
        mealCategory = "Dinner",
        dateIso = "2026-09-12",
        timestamp = 1757664000000L
      )

    val entity = entry.toMealEntity()

    assertEquals(entry.id, entity.id)
    assertEquals(entry.foodName, entity.foodName)
    assertEquals(entry.portionDescription, entity.portionDescription)
    assertEquals(entry.calories, entity.calories)
    assertEquals(entry.proteinGrams, entity.proteinGrams, 0.001f)
    assertEquals(entry.carbsGrams, entity.carbsGrams, 0.001f)
    assertEquals(entry.fatGrams, entity.fatGrams, 0.001f)
    assertEquals(entry.mealCategory, entity.mealCategory)
    assertEquals(entry.dateIso, entity.dateIso)
    assertEquals(entry.timestamp, entity.timestamp)
  }

  @Test
  fun `MealEntity to MealEntry preserves all fields cleanly`() {
    val entity =
      MealEntity(
        id = "uuid-entity-456",
        foodName = "1 Scoop Nakpro Gold Whey (Malai Kulfi)",
        portionDescription = "33g scoop in 250ml water",
        calories = 126,
        proteinGrams = 24.5f,
        carbsGrams = 3.2f,
        fatGrams = 1.9f,
        mealCategory = "Supplement",
        dateIso = "2026-09-11",
        timestamp = 1757577600000L
      )

    val entry = entity.toMealEntry()

    assertEquals(entity.id, entry.id)
    assertEquals(entity.foodName, entry.foodName)
    assertEquals(entity.portionDescription, entry.portionDescription)
    assertEquals(entity.calories, entry.calories)
    assertEquals(entity.proteinGrams, entry.proteinGrams, 0.001f)
    assertEquals(entity.carbsGrams, entry.carbsGrams, 0.001f)
    assertEquals(entity.fatGrams, entry.fatGrams, 0.001f)
    assertEquals(entity.mealCategory, entry.mealCategory)
    assertEquals(entity.dateIso, entry.dateIso)
    assertEquals(entity.timestamp, entry.timestamp)
  }

  @Test
  fun `MealEntry to Meal domain and back to MealEntry maintains roundtrip fidelity`() {
    val originalEntry =
      MealEntry(
        id = "roundtrip-789",
        foodName = "4 Boiled Egg Whites & 2 Whole Eggs",
        portionDescription = "6 eggs total",
        calories = 260,
        proteinGrams = 26.0f,
        carbsGrams = 1.5f,
        fatGrams = 14.0f,
        mealCategory = "Breakfast",
        dateIso = "2026-09-12",
        timestamp = 1757665000000L
      )

    val domainMeal: Meal = originalEntry.toDomain()
    assertEquals(originalEntry.id, domainMeal.id)
    assertEquals(originalEntry.foodName, domainMeal.foodName)
    assertEquals(originalEntry.calories, domainMeal.calories)
    assertEquals(originalEntry.proteinGrams, domainMeal.proteinGrams, 0.001f)
    assertEquals(originalEntry.dateIso, domainMeal.dateIso)

    val convertedEntry: MealEntry = domainMeal.toEntity()
    assertEquals(originalEntry, convertedEntry)
  }

  @Test
  fun `mappers preserve Unicode Devanagari Hindi and special characters`() {
    val unicodeEntry =
      MealEntry(
        id = "unicode-id",
        foodName = "तर्री पोहा और सांबर वड़ा 🍛 (Tarri Poha & Sambar Vada)",
        portionDescription = "1 प्लेट पोहा, 2 वड़े, तीखा रस्सा",
        calories = 480,
        proteinGrams = 12.0f,
        carbsGrams = 78.5f,
        fatGrams = 16.0f,
        mealCategory = "नाश्ता (Breakfast)",
        dateIso = "2026-09-12"
      )

    val entity = unicodeEntry.toMealEntity()
    assertEquals(unicodeEntry.foodName, entity.foodName)
    assertEquals(unicodeEntry.portionDescription, entity.portionDescription)
    assertEquals(unicodeEntry.mealCategory, entity.mealCategory)

    val roundtripEntry = entity.toMealEntry()
    assertEquals(unicodeEntry.foodName, roundtripEntry.foodName)
    assertEquals(unicodeEntry.portionDescription, roundtripEntry.portionDescription)
  }

  @Test
  fun `entity default values generate valid non-empty UUID and timestamp`() {
    val entity =
      MealEntity(
        foodName = "Black Coffee",
        portionDescription = "1 mug 250ml",
        calories = 5,
        proteinGrams = 0.3f,
        carbsGrams = 0f,
        fatGrams = 0f
      )

    assertNotNull(entity.id)
    assertTrue(entity.id.isNotBlank())
    assertTrue(entity.timestamp > 0L)
    assertEquals("Meal", entity.mealCategory)
    assertTrue(entity.dateIso.matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
  }
}
