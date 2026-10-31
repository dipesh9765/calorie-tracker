package com.calorietracker.app.support

import com.calorietracker.app.data.model.UserProfile
import com.calorietracker.app.domain.model.AiProviderType
import com.calorietracker.app.domain.model.DailyLog
import com.calorietracker.app.domain.model.Meal
import com.calorietracker.app.domain.repository.IAiNutritionRepository
import com.calorietracker.app.domain.repository.IDailyLogRepository
import com.calorietracker.app.domain.repository.IMealRepository
import com.calorietracker.app.domain.repository.IUserProfileRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

/** 2026-09-12 12:00 UTC, so "today" is stable in every test. */
val FIXED_CLOCK: Clock = Clock.fixed(Instant.parse("2026-09-12T12:00:00Z"), ZoneOffset.UTC)

const val TODAY = "2026-09-12"
const val YESTERDAY = "2026-09-11"

fun meal(
  name: String = "2 Rotis",
  date: String = TODAY,
  calories: Int = 180,
  protein: Float = 6f,
  carbs: Float = 30f,
  fat: Float = 3f,
  id: String = java.util.UUID.randomUUID().toString(),
  action: String = "CREATE"
) =
  Meal(
    id = id,
    foodName = name,
    portionDescription = "1 serving",
    calories = calories,
    proteinGrams = protein,
    carbsGrams = carbs,
    fatGrams = fat,
    dateIso = date,
    action = action
  )

class FakeMealRepository(initial: List<Meal> = emptyList()) : IMealRepository {
  val meals = MutableStateFlow(initial)

  override fun getMealsForDate(dateIso: String): Flow<List<Meal>> =
    meals.map { list -> list.filter { it.dateIso == dateIso } }

  override fun getAllMeals(): Flow<List<Meal>> = meals

  override suspend fun findByDateAndName(dateIso: String, foodName: String): Meal? =
    meals.value.firstOrNull { it.dateIso == dateIso && it.foodName.equals(foodName, true) }

  override suspend fun insertMeal(meal: Meal) {
    meals.value = meals.value.filterNot { it.id == meal.id } + meal
  }

  override suspend fun deleteMeal(mealId: String) {
    meals.value = meals.value.filterNot { it.id == mealId }
  }

  override suspend fun clearAll() {
    meals.value = emptyList()
  }
}

class FakeDailyLogRepository : IDailyLogRepository {
  val logs = MutableStateFlow<Map<String, DailyLog>>(emptyMap())

  override fun observe(dateIso: String): Flow<DailyLog> =
    logs.map { it[dateIso] ?: DailyLog(dateIso) }

  override fun observeAll(): Flow<Map<String, DailyLog>> = logs

  override suspend fun setCreatine(dateIso: String, taken: Boolean) {
    val current = logs.value[dateIso] ?: DailyLog(dateIso)
    logs.value = logs.value + (dateIso to current.copy(creatineTaken = taken))
  }

  override suspend fun addWater(dateIso: String, ml: Int) {
    val current = logs.value[dateIso] ?: DailyLog(dateIso)
    val water = (current.waterMl + ml).coerceAtLeast(0)
    logs.value = logs.value + (dateIso to current.copy(waterMl = water))
  }
}

class FakeUserProfileRepository(initial: UserProfile = UserProfile()) : IUserProfileRepository {
  private val state = MutableStateFlow(initial)
  override val profile: StateFlow<UserProfile> = state

  override suspend fun update(profile: UserProfile) {
    state.value = profile
  }
}

class FakeAiNutritionRepository(var result: Result<Pair<Meal, String>>) : IAiNutritionRepository {
  var calls = 0
  var lastMealText: String? = null
  var lastProvider: AiProviderType? = null
  var lastApiKey: String? = null
  var lastModel: String? = null
  var lastTodayContext: String? = null
  var lastYesterdayContext: String? = null

  override suspend fun parseMealText(
    mealText: String,
    provider: AiProviderType,
    apiKey: String,
    modelName: String,
    todayContext: String,
    yesterdayContext: String,
    memoryContext: String,
    profile: UserProfile
  ): Result<Pair<Meal, String>> {
    calls++
    lastMealText = mealText
    lastProvider = provider
    lastApiKey = apiKey
    lastModel = modelName
    lastTodayContext = todayContext
    lastYesterdayContext = yesterdayContext
    return result
  }

  override suspend fun generateNotificationAdvice(
    provider: AiProviderType,
    apiKey: String,
    modelName: String,
    userName: String,
    todayContext: String,
    yesterdayContext: String,
    targetCalories: Int,
    targetProtein: Float,
    memoryContext: String,
    cuisine: String
  ): Result<String> = Result.success("Sample advice")
}
