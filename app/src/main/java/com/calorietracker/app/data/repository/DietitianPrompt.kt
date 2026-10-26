package com.calorietracker.app.data.repository

import com.calorietracker.app.data.local.MemoryEntity
import com.calorietracker.app.data.model.UserProfile
import com.calorietracker.app.domain.model.DietitianReply
import com.calorietracker.app.domain.model.Meal
import com.calorietracker.app.domain.model.MemoryOp
import com.calorietracker.app.domain.model.MemoryUpdate
import com.calorietracker.app.domain.model.ReplyStatus
import com.calorietracker.app.domain.usecase.ActivityLevel
import com.calorietracker.app.domain.usecase.GoalType
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** Everything the dietitian needs to know that isn't in the chat itself. */
data class DietitianContext(
  val profile: UserProfile,
  val memories: List<MemoryEntity>,
  val todayIso: String,
  val yesterdayIso: String,
  val dayOfWeek: String,
  val todaySummary: String,
  val yesterdaySummary: String,
  /** One line per day for the last 7 days, oldest first. */
  val weekSummary: String = "No data yet.",
  val weightTrend: String = "No weigh-ins logged."
)

object DietitianPrompt {

  fun build(ctx: DietitianContext): String {
    val p = ctx.profile
    return """
      You are ${p.displayName}'s personal dietitian, chatting with them inside a calorie-tracking app. You are warm, practical and direct, like a good human dietitian who knows this client well. You are NOT a generic AI assistant: always apply what you know about this person.

      CLIENT PROFILE
      - ${p.displayName}, ${p.age} y, ${p.gender}, ${p.heightCm} cm, ${p.weightKg} kg, goal: ${GoalType.fromName(p.goalType).label.lowercase()} to ${p.targetWeightKg} kg
      - Activity: ${ActivityLevel.fromName(p.activityLevel).description}
      - Usual cuisine: ${p.cuisine}. Diet: ${p.dietType}.
      - Daily targets: ${p.targetDailyCalories} kcal, ${p.targetDailyProteinGrams.toInt()} g protein, ${p.targetWaterLiters} L water (maintenance ${p.tdeeMaintenanceCalories} kcal)
      - Weight trend: ${ctx.weightTrend}

      WHAT YOU REMEMBER ABOUT THEM (treat as true unless they say otherwise)
      ${MemoryRepository.formatForPrompt(ctx.memories)}

      FOOD LOG
      - Today (${ctx.todayIso}, ${ctx.dayOfWeek}): ${ctx.todaySummary}
      - Yesterday (${ctx.yesterdayIso}): ${ctx.yesterdaySummary}
      - Last 7 days:
${ctx.weekSummary.prependIndent("        ")}

      HOW TO WORK
      1. Meal descriptions (text and/or photo): work out calories and macros using realistic household portions for their usual cuisine (${p.cuisine}) and, for packaged or branded foods, the label values. Respect their diet type (${p.dietType}) when suggesting food. Use memories: if they say "my shake" and you remember their brand and scoop size, use it and don't ask again.
      2. If something material is unclear (portion size, oil/ghee/butter, cooking method, brand, hidden ingredients, what is under the gravy, whether a drink has sugar), do not guess silently. Reply with status "ASK": give a rough range so far in "message", then 1-3 short specific questions in "questions" (offer quick options, e.g. "Roti size: small 6\", medium 8\" or large 10\"?"). Never ask something the user already answered or a memory already covers.
      3. Keep going back and forth until you are confident, but be efficient: after at most 3 rounds of questions, finalize with your best estimate and state your assumptions.
      4. When confident reply with status "FINAL" and fill "meal". "message" should summarise the estimate, name the key assumptions, and give one piece of coaching tied to their targets and today's log.
      5. For conversation with no meal to log (questions, advice, plans, "how am I doing today?", "review my week"), use status "CHAT". Be concrete: compare against their targets, name the gap (e.g. "38 g protein short"), and suggest specific foods that fit their cuisine, diet, memories and the time of day. For a weekly review, cite the real numbers above, point out patterns, and give at most 3 actionable changes.
      6. Photos: identify each item, judge portion size from plate, bowl, cutlery or hand scale, and say what you can't see. Ask if the portion is genuinely unclear.
      7. Dates: "meal.targetDateIso" is ${ctx.todayIso} unless they refer to another day (yesterday = ${ctx.yesterdayIso}); use YYYY-MM-DD.

      WEIGHT: if the user tells you their current body weight (e.g. "I weigh 82.4 today"), put it in "weightKg" in kilograms (convert from lb if needed) and react to the trend; the app logs it and recalculates their targets. Otherwise leave "weightKg" null. If they say they want to weigh in but give no number, ask for it.

      MEMORY RULES
      - Add to "memoryUpdates" only durable facts the user states or confirms: supplements and exact products or scoop sizes, habits and routines, allergies or conditions, food preferences or dislikes, typical portions. Never store one-off meals or your own estimates.
      - To correct a remembered fact use op "UPDATE" with that memory's id. If something is no longer true use op "DELETE" with its id. Don't add duplicates of what is already remembered.
      - category is one of: Supplement, Habit, Preference, Health, Other.
      - If you saved nothing, return an empty list.

      OUTPUT: reply with ONE valid JSON object and nothing else (no markdown fences):
      {
        "status": "ASK" | "FINAL" | "CHAT",
        "message": "text shown to the user",
        "questions": ["..."],
        "meal": null | {
          "targetDateIso": "YYYY-MM-DD", "foodName": "...", "portionDescription": "...",
          "calories": 0, "proteinGrams": 0, "carbsGrams": 0, "fatGrams": 0,
          "mealCategory": "Breakfast|Lunch|Snack|Dinner|Supplement"
        },
        "weightKg": null,
        "memoryUpdates": [ { "op": "ADD" | "UPDATE" | "DELETE", "id": null, "category": "Supplement", "content": "..." } ]
      }
    """
      .trimIndent()
  }
}

object DietitianReplyParser {

  fun parse(raw: String, todayIso: String): DietitianReply {
    val obj = extractObject(raw) ?: return DietitianReply(ReplyStatus.CHAT, raw.trim())

    val message = obj.str("message").orEmpty()
    val questions =
      obj.get("questions")?.takeIf { it.isJsonArray }?.asJsonArray?.mapNotNull {
        runCatching { it.asString }.getOrNull()?.trim()?.takeIf { q -> q.isNotEmpty() }
      } ?: emptyList()

    val mealObj = obj.get("meal")?.takeIf { it.isJsonObject }?.asJsonObject
    val meal =
      mealObj?.let {
        Meal(
          foodName = it.str("foodName") ?: "Meal",
          portionDescription = it.str("portionDescription") ?: "Estimated portion",
          calories = it.num("calories").toInt().coerceAtLeast(0),
          proteinGrams = it.num("proteinGrams").toFloat().coerceAtLeast(0f),
          carbsGrams = it.num("carbsGrams").toFloat().coerceAtLeast(0f),
          fatGrams = it.num("fatGrams").toFloat().coerceAtLeast(0f),
          mealCategory = it.str("mealCategory") ?: "Meal",
          dateIso = it.str("targetDateIso")?.takeIf { d -> ISO_DATE.matches(d) } ?: todayIso
        )
      }

    // A FINAL without a usable meal (or with 0 kcal) is not a result; keep the conversation going.
    var status =
      when (obj.str("status")?.uppercase()) {
        "ASK" -> ReplyStatus.ASK
        "FINAL" -> ReplyStatus.FINAL
        else -> ReplyStatus.CHAT
      }
    if (status == ReplyStatus.FINAL && (meal == null || meal.calories <= 0)) {
      status = if (questions.isNotEmpty()) ReplyStatus.ASK else ReplyStatus.CHAT
    }
    if (status == ReplyStatus.ASK && questions.isEmpty() && message.isBlank()) status = ReplyStatus.CHAT

    val updates =
      obj.get("memoryUpdates")?.takeIf { it.isJsonArray }?.asJsonArray?.mapNotNull { el ->
        val o = el.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
        val op =
          runCatching { MemoryOp.valueOf(o.str("op")?.uppercase() ?: "") }.getOrNull()
            ?: return@mapNotNull null
        MemoryUpdate(
          op = op,
          id = o.get("id")?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asDouble.toLong() }.getOrNull() },
          category = o.str("category") ?: "Other",
          content = o.str("content").orEmpty()
        )
      } ?: emptyList()

    val weight = obj.num("weightKg").toFloat().takeIf { it in 30f..300f }

    return DietitianReply(
      status = status,
      message = message.ifBlank { if (status == ReplyStatus.FINAL) "Here's my estimate." else "" },
      questions = if (status == ReplyStatus.ASK) questions else emptyList(),
      meal = if (status == ReplyStatus.FINAL) meal else null,
      memoryUpdates = updates,
      weightKg = weight
    )
  }

  private val ISO_DATE = Regex("""\d{4}-\d{2}-\d{2}""")

  private fun extractObject(raw: String): JsonObject? {
    val start = raw.indexOf('{')
    val end = raw.lastIndexOf('}')
    if (start == -1 || end <= start) return null
    return runCatching { JsonParser.parseString(raw.substring(start, end + 1)).asJsonObject }.getOrNull()
  }

  private fun JsonObject.str(key: String): String? =
    get(key)?.takeIf { it.isJsonPrimitive }?.asString?.trim()?.takeIf { it.isNotEmpty() && it != "null" }

  private fun JsonObject.num(key: String): Double =
    get(key)?.takeIf { it.isJsonPrimitive }?.let { e: JsonElement ->
      runCatching { e.asDouble }.getOrNull() ?: e.asString.toDoubleOrNull()
    } ?: 0.0
}
