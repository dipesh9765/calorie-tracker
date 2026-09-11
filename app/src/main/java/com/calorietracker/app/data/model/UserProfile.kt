package com.calorietracker.app.data.model

data class UserProfile(
    val name: String = "User",
    val age: Int = 25,
    val gender: String = "Male",
    val heightCm: Float = 188f,
    val weightKg: Float = 105f,
    val targetWeightKg: Float = 90f,
    val gymDaysPerWeek: Int = 5,
    val primaryAiProvider: String = "Gemini", // Gemini, OpenAI, Claude, DeepSeek
    val geminiApiKey: String = "",
    val openAiApiKey: String = "",
    val claudeApiKey: String = "",
    val deepSeekApiKey: String = "",
    val geminiModel: String = "gemini-3.6-flash",
    val openAiModel: String = "gpt-4o-mini",
    val claudeModel: String = "claude-3-5-sonnet-20240620",
    val deepSeekModel: String = "deepseek-chat",
    
    // Auto-calculated nutrition goals
    val bmrCalories: Int = 2105,
    val tdeeMaintenanceCalories: Int = 2900,
    val targetDailyCalories: Int = 2300,
    val targetDailyProteinGrams: Float = 180f,
    val targetDailyCreatineGrams: Float = 5f,
    val targetWaterLiters: Float = 4.0f
)
