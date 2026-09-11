package com.calorietracker.app.domain.repository

import com.calorietracker.app.domain.model.AiProviderType
import com.calorietracker.app.domain.model.Meal

interface IAiNutritionRepository {
    suspend fun parseMealText(
        mealText: String,
        provider: AiProviderType,
        apiKey: String,
        modelName: String = "",
        todayContext: String = "",
        yesterdayContext: String = ""
    ): Result<Pair<Meal, String>>
}
