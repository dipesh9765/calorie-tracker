package com.calorietracker.app.data.exporter

import android.content.Context
import android.os.Environment
import com.calorietracker.app.data.model.MealEntry
import com.google.gson.GsonBuilder
import java.io.File
import java.io.FileWriter

class DataExporter(private val context: Context) {

    private val gson = GsonBuilder().setPrettyPrinting().create()

    fun exportToJson(meals: List<MealEntry>): File? {
        return try {
            val exportDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
            val file = File(exportDir, "CalorieTracker_Export_${System.currentTimeMillis()}.json")
            val jsonString = gson.toJson(meals)
            FileWriter(file).use { writer -> writer.write(jsonString) }
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun exportToCsv(meals: List<MealEntry>): File? {
        return try {
            val exportDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
            val file = File(exportDir, "CalorieTracker_Export_${System.currentTimeMillis()}.csv")
            FileWriter(file).use { writer ->
                writer.append("ID,Date,MealCategory,FoodName,PortionDescription,Calories,ProteinGrams,CarbsGrams,FatGrams\n")
                for (meal in meals) {
                    writer.append("\"${meal.id}\",")
                        .append("\"${meal.dateIso}\",")
                        .append("\"${meal.mealCategory}\",")
                        .append("\"${meal.foodName.replace("\"", "\"\"")}\",")
                        .append("\"${meal.portionDescription.replace("\"", "\"\"")}\",")
                        .append("${meal.calories},")
                        .append("${meal.proteinGrams},")
                        .append("${meal.carbsGrams},")
                        .append("${meal.fatGrams}\n")
                }
            }
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
