package com.calorietracker.app

import com.calorietracker.app.data.local.MealEntity
import com.calorietracker.app.data.local.toMealEntity
import com.calorietracker.app.data.local.toMealEntry
import com.calorietracker.app.data.model.MealEntry
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit Test verifying production Room database entities, mappers, and data integrity.
 */
class RoomDatabaseTest {

    @Test
    fun `toMealEntity converts MealEntry to MealEntity correctly`() {
        val entry = MealEntry(
            id = "test_uuid_123",
            foodName = "2 Rotis & Chicken Curry",
            portionDescription = "2 chapatis + 150g chicken",
            calories = 450,
            proteinGrams = 32.5f,
            carbsGrams = 30.0f,
            fatGrams = 12.0f,
            mealCategory = "Dinner",
            dateIso = "2026-09-10"
        )

        val entity = entry.toMealEntity()

        assertEquals(entry.id, entity.id)
        assertEquals(entry.foodName, entity.foodName)
        assertEquals(entry.portionDescription, entity.portionDescription)
        assertEquals(entry.calories, entity.calories)
        assertEquals(entry.proteinGrams, entity.proteinGrams, 0.01f)
        assertEquals(entry.dateIso, entity.dateIso)
    }

    @Test
    fun `toMealEntry converts MealEntity to MealEntry cleanly`() {
        val entity = MealEntity(
            id = "entity_id_999",
            foodName = "1 Scoop Nakpro Whey",
            portionDescription = "1 scoop 33g",
            calories = 124,
            proteinGrams = 24.0f,
            carbsGrams = 3.3f,
            fatGrams = 1.8f,
            mealCategory = "Supplement",
            dateIso = "2026-09-11"
        )

        val entry = entity.toMealEntry()

        assertEquals("entity_id_999", entry.id)
        assertEquals("1 Scoop Nakpro Whey", entry.foodName)
        assertEquals(124, entry.calories)
        assertEquals(24.0f, entry.proteinGrams, 0.01f)
        assertEquals("Supplement", entry.mealCategory)
    }
}
