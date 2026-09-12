package com.calorietracker.app

import com.calorietracker.app.data.model.MealEntry
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.io.StringWriter
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit Test suite verifying data export formatting, RFC 4180 CSV serialization, quotes & comma
 * escaping, and JSON roundtrip deserialization fidelity.
 */
class DataExporterTest {

  private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

  private fun formatCsvString(meals: List<MealEntry>): String {
    val writer = StringWriter()
    writer.append(
      "ID,Date,MealCategory,FoodName,PortionDescription,Calories,ProteinGrams,CarbsGrams,FatGrams\n"
    )
    for (meal in meals) {
      writer
        .append("\"${meal.id}\",")
        .append("\"${meal.dateIso}\",")
        .append("\"${meal.mealCategory}\",")
        .append("\"${meal.foodName.replace("\"", "\"\"")}\",")
        .append("\"${meal.portionDescription.replace("\"", "\"\"")}\",")
        .append("${meal.calories},")
        .append("${meal.proteinGrams},")
        .append("${meal.carbsGrams},")
        .append("${meal.fatGrams}\n")
    }
    return writer.toString()
  }

  @Test
  fun `JSON export produces valid schema and maintains roundtrip equality`() {
    val meals =
      listOf(
        MealEntry(
          id = "export-1",
          foodName = "3 Rotis & Chicken Curry",
          portionDescription = "3 rotis + 150g chicken",
          calories = 480,
          proteinGrams = 38.5f,
          carbsGrams = 42.0f,
          fatGrams = 14.0f,
          mealCategory = "Dinner",
          dateIso = "2026-09-12",
          timestamp = 1757664000000L
        ),
        MealEntry(
          id = "export-2",
          foodName = "1 Scoop Nakpro Whey",
          portionDescription = "33g scoop",
          calories = 125,
          proteinGrams = 24.0f,
          carbsGrams = 3.3f,
          fatGrams = 1.8f,
          mealCategory = "Supplement",
          dateIso = "2026-09-12",
          timestamp = 1757668000000L
        )
      )

    val jsonString = gson.toJson(meals)
    assertNotNull(jsonString)
    assertTrue(jsonString.startsWith("["))
    assertTrue(jsonString.endsWith("]"))

    val listType = object : TypeToken<List<MealEntry>>() {}.type
    val deserialized: List<MealEntry> = gson.fromJson(jsonString, listType)

    assertEquals(2, deserialized.size)
    assertEquals(meals[0].id, deserialized[0].id)
    assertEquals(meals[0].foodName, deserialized[0].foodName)
    assertEquals(meals[0].calories, deserialized[0].calories)
    assertEquals(meals[0].proteinGrams, deserialized[0].proteinGrams, 0.001f)
    assertEquals(meals[1].foodName, deserialized[1].foodName)
  }

  @Test
  fun `CSV export generates exact standard header`() {
    val csv = formatCsvString(emptyList())
    val lines = csv.trim().split("\n")
    assertEquals(1, lines.size)
    assertEquals(
      "ID,Date,MealCategory,FoodName,PortionDescription,Calories,ProteinGrams,CarbsGrams,FatGrams",
      lines[0]
    )
  }

  @Test
  fun `CSV export escapes embedded double quotes and commas according to RFC 4180`() {
    val mealWithSpecialChars =
      MealEntry(
        id = "csv-special-1",
        foodName = "Chicken \"Saoji\" Special, Spicy",
        portionDescription = "1 \"Full\" Plate, 250g",
        calories = 550,
        proteinGrams = 44.0f,
        carbsGrams = 30.0f,
        fatGrams = 20.0f,
        mealCategory = "Dinner",
        dateIso = "2026-09-12"
      )

    val csv = formatCsvString(listOf(mealWithSpecialChars))
    val lines = csv.trim().split("\n")
    assertEquals(2, lines.size)

    val dataLine = lines[1]
    // Double quotes inside fields must be escaped as ""
    assertTrue(dataLine.contains("\"Chicken \"\"Saoji\"\" Special, Spicy\""))
    assertTrue(dataLine.contains("\"1 \"\"Full\"\" Plate, 250g\""))
    assertTrue(dataLine.endsWith("550,44.0,30.0,20.0"))
  }

  @Test
  fun `CSV export handles zero values cleanly`() {
    val zeroMeal =
      MealEntry(
        id = "zero-1",
        foodName = "Diet Soda",
        portionDescription = "330ml can",
        calories = 0,
        proteinGrams = 0f,
        carbsGrams = 0f,
        fatGrams = 0f,
        mealCategory = "Snack",
        dateIso = "2026-09-12"
      )

    val csv = formatCsvString(listOf(zeroMeal))
    val lines = csv.trim().split("\n")
    assertEquals(2, lines.size)
    assertTrue(lines[1].endsWith("0,0.0,0.0,0.0"))
  }
}
