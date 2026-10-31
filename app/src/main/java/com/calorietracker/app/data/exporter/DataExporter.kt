package com.calorietracker.app.data.exporter

import android.content.Context
import android.os.Environment
import android.util.Log
import com.calorietracker.app.domain.model.Meal
import com.calorietracker.app.domain.repository.MealExporter
import com.google.gson.GsonBuilder
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileWriter
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataExporter @Inject constructor(@ApplicationContext private val context: Context) :
  MealExporter {

  private val gson = GsonBuilder().setPrettyPrinting().create()

  override fun exportToJson(meals: List<Meal>): File? {
    return try {
      val exportDir =
        context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
      val file = File(exportDir, "CalorieTracker_Export_${System.currentTimeMillis()}.json")
      val jsonString = gson.toJson(meals)
      FileWriter(file).use { writer -> writer.write(jsonString) }
      file
    } catch (e: IOException) {
      Log.e(TAG, "Export failed", e)
      null
    }
  }

  override fun exportToCsv(meals: List<Meal>): File? {
    return try {
      val exportDir =
        context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
      val file = File(exportDir, "CalorieTracker_Export_${System.currentTimeMillis()}.csv")
      FileWriter(file).use { writer ->
        writer.append(
          "ID,Date,MealCategory,FoodName,PortionDescription,Calories,ProteinGrams,CarbsGrams,FatGrams\n"
        )
        for (meal in meals) {
          writer
            .append("\"${meal.id}\",")
            .append("\"${meal.dateIso}\",")
            .append("\"${meal.mealCategory}\",")
            .append("${csvCell(meal.foodName)},")
            .append("${csvCell(meal.portionDescription)},")
            .append("${meal.calories},")
            .append("${meal.proteinGrams},")
            .append("${meal.carbsGrams},")
            .append("${meal.fatGrams}\n")
        }
      }
      file
    } catch (e: IOException) {
      Log.e(TAG, "Export failed", e)
      null
    }
  }

  /**
   * Quotes a CSV cell. Text that a spreadsheet would run as a formula (starts with `=`, `+`, `-` or
   * `@`) is prefixed with an apostrophe so an AI-generated food name cannot execute anything.
   */
  private fun csvCell(text: String): String {
    val safe = if (text.firstOrNull() in FORMULA_PREFIXES) "'$text" else text
    return "\"" + safe.replace("\"", "\"\"") + "\""
  }

  private companion object {
    const val TAG = "DataExporter"
    val FORMULA_PREFIXES = setOf('=', '+', '-', '@', '\t', '\r')
  }
}
