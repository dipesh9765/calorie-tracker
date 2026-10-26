package com.calorietracker.app.presentation

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.calorietracker.app.data.local.AppDatabase
import com.calorietracker.app.data.local.MealEntity
import com.calorietracker.app.data.model.UserProfile
import com.calorietracker.app.data.repository.DietitianChatRepository
import com.calorietracker.app.data.repository.DietitianContext
import com.calorietracker.app.data.repository.MemoryRepository
import com.calorietracker.app.data.repository.PreferencesRepository
import com.calorietracker.app.data.repository.WeightRepository
import com.calorietracker.app.domain.model.AiProviderType
import com.calorietracker.app.domain.model.ChatImage
import com.calorietracker.app.domain.model.ChatRole
import com.calorietracker.app.domain.model.ChatTurn
import com.calorietracker.app.domain.model.Meal
import com.calorietracker.app.domain.model.ReplyStatus
import com.calorietracker.app.domain.usecase.GoalPlanner
import com.calorietracker.app.domain.usecase.toPlanInput
import com.calorietracker.app.domain.usecase.withPlan
import com.calorietracker.app.util.ImageEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class MealCardState {
  PENDING,
  SAVED,
  SUPERSEDED
}

data class ChatMessageUi(
  val id: Long,
  val role: ChatRole,
  val text: String,
  val image: ChatImage? = null,
  val questions: List<String> = emptyList(),
  val meal: Meal? = null,
  val mealState: MealCardState = MealCardState.PENDING,
  val memoryNotes: List<String> = emptyList(),
  val isError: Boolean = false,
  /** What is replayed to the provider on later turns. */
  val historyText: String = text
)

/**
 * Holds one dietitian conversation: user turns (text + optional photo), the dietitian's clarifying
 * questions, and finally a meal estimate the user can save. Kept in memory; "New chat" resets it.
 */
class DietitianChatViewModel(app: Application) : AndroidViewModel(app) {

  private val db = AppDatabase.getInstance(app)
  private val mealDao = db.mealDao()
  private val memoryRepository = MemoryRepository(db.memoryDao())
  private val weightRepository = WeightRepository(db.weightDao())
  private val prefs = PreferencesRepository(app)
  private val chatRepository = DietitianChatRepository()

  private val _messages = MutableStateFlow<List<ChatMessageUi>>(emptyList())
  val messages: StateFlow<List<ChatMessageUi>> = _messages.asStateFlow()

  private val _isLoading = MutableStateFlow(false)
  val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

  private val _pendingImage = MutableStateFlow<ChatImage?>(null)
  val pendingImage: StateFlow<ChatImage?> = _pendingImage.asStateFlow()

  /** Bumped when the chat changes the stored profile so the UI can reload it. */
  private val _profileVersion = MutableStateFlow(0)
  val profileVersion: StateFlow<Int> = _profileVersion.asStateFlow()

  private val planner = GoalPlanner()
  private var nextId = 1L

  fun attachImage(uri: Uri) {
    viewModelScope.launch {
      val resolver = getApplication<Application>().contentResolver
      val encoded = withContext(Dispatchers.Default) { ImageEncoder.encode(resolver, uri) }
      if (encoded != null) {
        _pendingImage.value = encoded
      } else {
        appendError("Couldn't read that image. Please try another one.")
      }
    }
  }

  fun clearImage() {
    _pendingImage.value = null
  }

  fun newChat() {
    if (_isLoading.value) return
    _messages.value = emptyList()
    _pendingImage.value = null
  }

  fun send(rawText: String) {
    val image = _pendingImage.value
    val text =
      rawText.trim().ifEmpty { if (image != null) "What are the calories in this meal?" else "" }
    if (text.isEmpty() || _isLoading.value) return

    _messages.update { it + ChatMessageUi(nextId++, ChatRole.USER, text, image) }
    _pendingImage.value = null
    _isLoading.value = true

    viewModelScope.launch {
      val profile = prefs.getUserProfile()
      val provider = AiProviderType.fromString(profile.primaryAiProvider)
      val reply =
        chatRepository.reply(
          turns = historyForProvider(),
          provider = provider,
          apiKey = apiKeyFor(profile, provider),
          modelName = modelFor(profile, provider),
          context = buildContext(profile)
        )

      reply
        .onSuccess { r ->
          val notes =
            runCatching { memoryRepository.apply(r.memoryUpdates) }.getOrDefault(emptyList()) +
              listOfNotNull(r.weightKg?.let { recordWeight(it, profile) })
          _messages.update { list ->
            // A new estimate replaces any earlier unsaved one.
            val base =
              if (r.status == ReplyStatus.FINAL) {
                list.map {
                  if (it.meal != null && it.mealState == MealCardState.PENDING) {
                    it.copy(mealState = MealCardState.SUPERSEDED)
                  } else {
                    it
                  }
                }
              } else {
                list
              }
            base +
              ChatMessageUi(
                id = nextId++,
                role = ChatRole.ASSISTANT,
                text = r.message,
                questions = r.questions,
                meal = r.meal,
                memoryNotes = notes,
                historyText = historyTextFor(r.message, r.questions, r.meal)
              )
          }
        }
        .onFailure { appendError(it.localizedMessage ?: "Something went wrong. Please retry.") }
      _isLoading.value = false
    }
  }

  /** Logs a weight reported in chat, refreshes the targets, and returns a note for the chat. */
  private suspend fun recordWeight(weightKg: Float, profile: UserProfile): String? =
    withContext(Dispatchers.IO) {
      runCatching {
          val added = weightRepository.logIfChanged(weightKg)
          if (!added) return@runCatching null
          var updated = profile.copy(weightKg = weightKg)
          if (profile.profileConfigured) {
            updated = updated.withPlan(planner.plan(updated.toPlanInput()))
          }
          prefs.saveUserProfile(updated)
          _profileVersion.update { it + 1 }
          if (
            profile.profileConfigured && updated.targetDailyCalories != profile.targetDailyCalories
          ) {
            "Logged ${"%.1f".format(weightKg)} kg. New targets: ${updated.targetDailyCalories} kcal, " +
              "${updated.targetDailyProteinGrams.toInt()} g protein"
          } else {
            "Logged ${"%.1f".format(weightKg)} kg"
          }
        }
        .getOrNull()
    }

  fun saveMeal(messageId: Long) {
    val msg = _messages.value.find { it.id == messageId } ?: return
    val meal = msg.meal ?: return
    if (msg.mealState != MealCardState.PENDING) return
    // Flip the state first so a double tap can't insert the meal twice.
    _messages.update { list ->
      list.map { if (it.id == messageId) it.copy(mealState = MealCardState.SAVED) else it }
    }
    viewModelScope.launch {
      withContext(Dispatchers.IO) {
        mealDao.insertMeal(
          MealEntity(
            foodName = meal.foodName,
            portionDescription = meal.portionDescription,
            calories = meal.calories,
            proteinGrams = meal.proteinGrams,
            carbsGrams = meal.carbsGrams,
            fatGrams = meal.fatGrams,
            mealCategory = meal.mealCategory,
            dateIso = meal.dateIso
          )
        )
      }
    }
  }

  private fun appendError(text: String) {
    _messages.update { it + ChatMessageUi(nextId++, ChatRole.ASSISTANT, text, isError = true) }
  }

  /** Error bubbles stay out of the history; only the 2 most recent photos are re-sent. */
  private fun historyForProvider(): List<ChatTurn> {
    // Providers require the conversation to open with a user turn.
    val turns =
      _messages.value
        .filterNot { it.isError }
        .takeLast(MAX_HISTORY)
        .dropWhile { it.role != ChatRole.USER }
    val imageIdsToKeep =
      turns.filter { it.image != null }.takeLast(MAX_IMAGES_IN_HISTORY).map { it.id }.toSet()
    return turns.map {
      ChatTurn(it.role, it.historyText, if (it.id in imageIdsToKeep) it.image else null)
    }
  }

  /**
   * Plain-text replay of an assistant turn, including the estimate so later turns can refine it.
   */
  private fun historyTextFor(message: String, questions: List<String>, meal: Meal?): String {
    val parts = mutableListOf(message)
    if (questions.isNotEmpty()) {
      parts += questions.mapIndexed { i, q -> "${i + 1}. $q" }.joinToString("\n")
    }
    if (meal != null) {
      parts +=
        "[Estimate: ${meal.foodName}, ${meal.portionDescription}: ${meal.calories} kcal, " +
          "${meal.proteinGrams} g protein, ${meal.carbsGrams} g carbs, ${meal.fatGrams} g fat]"
    }
    return parts.joinToString("\n")
  }

  private suspend fun buildContext(profile: UserProfile): DietitianContext =
    withContext(Dispatchers.IO) {
      val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
      val today = fmt.format(Date())
      val yesterday =
        fmt.format(Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }.time)
      val meals = mealDao.getAllMeals().first()
      DietitianContext(
        profile = profile,
        memories = memoryRepository.getAll(),
        todayIso = today,
        yesterdayIso = yesterday,
        dayOfWeek = SimpleDateFormat("EEEE", Locale.getDefault()).format(Date()),
        todaySummary = summarize(meals.filter { it.dateIso == today }, "Nothing logged yet."),
        yesterdaySummary = summarize(meals.filter { it.dateIso == yesterday }, "Nothing logged."),
        weekSummary = weekSummary(meals, fmt),
        weightTrend = weightRepository.trendSummary()
      )
    }

  private fun summarize(meals: List<MealEntity>, empty: String): String {
    if (meals.isEmpty()) return empty
    val protein = meals.sumOf { it.proteinGrams.toDouble() }
    val items =
      meals.joinToString("; ") {
        "${it.foodName} (${it.calories} kcal, ${"%.0f".format(it.proteinGrams)} g protein)"
      }
    return "${meals.sumOf { it.calories }} kcal, ${"%.0f".format(protein)} g protein. $items"
  }

  private fun weekSummary(meals: List<MealEntity>, fmt: SimpleDateFormat): String =
    (6 downTo 0).joinToString("\n") { daysAgo ->
      val day =
        fmt.format(Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -daysAgo) }.time)
      val dayMeals = meals.filter { it.dateIso == day }
      if (dayMeals.isEmpty()) {
        "$day: nothing logged"
      } else {
        "$day: ${dayMeals.sumOf { it.calories }} kcal, " +
          "${"%.0f".format(dayMeals.sumOf { it.proteinGrams.toDouble() })} g protein"
      }
    }

  private fun apiKeyFor(p: UserProfile, t: AiProviderType) =
    when (t) {
      AiProviderType.OPENAI -> p.openAiApiKey
      AiProviderType.CLAUDE -> p.claudeApiKey
      AiProviderType.DEEPSEEK -> p.deepSeekApiKey
      AiProviderType.GEMINI -> p.geminiApiKey
    }.trim()

  private fun modelFor(p: UserProfile, t: AiProviderType) =
    when (t) {
      AiProviderType.OPENAI -> p.openAiModel
      AiProviderType.CLAUDE -> p.claudeModel
      AiProviderType.DEEPSEEK -> p.deepSeekModel
      AiProviderType.GEMINI -> p.geminiModel
    }.trim()

  private companion object {
    const val MAX_HISTORY = 24
    const val MAX_IMAGES_IN_HISTORY = 2
  }
}
