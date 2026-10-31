package com.calorietracker.app.data.remote.adapter

import com.google.gson.JsonArray
import com.google.gson.JsonObject

/** The JSON object every provider is asked to return for a logged meal. */
internal object MealResponseSchema {
  private enum class FieldType {
    STRING,
    INTEGER,
    NUMBER
  }

  private val FIELDS =
    listOf(
      "action" to FieldType.STRING,
      "targetDateIso" to FieldType.STRING,
      "foodName" to FieldType.STRING,
      "portionDescription" to FieldType.STRING,
      "calories" to FieldType.INTEGER,
      "proteinGrams" to FieldType.NUMBER,
      "carbsGrams" to FieldType.NUMBER,
      "fatGrams" to FieldType.NUMBER,
      "mealCategory" to FieldType.STRING,
      "advice" to FieldType.STRING
    )

  /** Schema in the shape Gemini's `responseSchema` expects (upper-case type names). */
  fun forGemini(): JsonObject =
    JsonObject().apply {
      addProperty("type", "OBJECT")
      add("properties", properties { it.name })
      add("required", requiredFields())
    }

  /** The `response_format` block for OpenAI's strict structured outputs. */
  fun forOpenAi(): JsonObject =
    JsonObject().apply {
      addProperty("type", "json_schema")
      add(
        "json_schema",
        JsonObject().apply {
          addProperty("name", "meal_response")
          addProperty("strict", true)
          add(
            "schema",
            JsonObject().apply {
              addProperty("type", "object")
              add("properties", properties { it.name.lowercase() })
              add("required", requiredFields())
              addProperty("additionalProperties", false)
            }
          )
        }
      )
    }

  private fun properties(typeName: (FieldType) -> String): JsonObject =
    JsonObject().apply {
      FIELDS.forEach { (name, type) ->
        add(name, JsonObject().apply { addProperty("type", typeName(type)) })
      }
    }

  private fun requiredFields(): JsonArray =
    JsonArray().apply { FIELDS.forEach { (name, _) -> add(name) } }
}

/** Reads `choices[0].message.content`, the response shape shared by OpenAI-compatible APIs. */
internal fun JsonObject.firstChoiceContent(provider: String): String {
  val choices =
    getAsJsonArray("choices")
      ?: throw ProviderResponseException(
        "Invalid $provider response structure: missing 'choices' array."
      )
  if (choices.size() == 0) throw ProviderResponseException("$provider returned zero choices.")
  return choices[0].asJsonObject.getAsJsonObject("message").get("content").asString
}

/** Reads the first `content[].text`, the shape of Anthropic's Messages API. */
internal fun JsonObject.firstContentText(provider: String): String {
  val content =
    getAsJsonArray("content")
      ?: throw ProviderResponseException(
        "Invalid $provider response structure: missing 'content' array."
      )
  if (content.size() == 0)
    throw ProviderResponseException("$provider returned empty content array.")
  return content[0].asJsonObject.get("text").asString
}

/** Reads `candidates[0].content.parts[0].text` from a Gemini response, naming what is missing. */
internal fun JsonObject.firstCandidateText(provider: String): String {
  val candidates =
    getAsJsonArray("candidates")
      ?: throw ProviderResponseException(
        "Invalid $provider response structure: missing 'candidates' array."
      )
  if (candidates.size() == 0) throw ProviderResponseException("$provider returned zero candidates.")
  val content =
    candidates[0].asJsonObject.getAsJsonObject("content")
      ?: throw ProviderResponseException("$provider response missing 'content' in candidate.")
  val parts =
    content.getAsJsonArray("parts")
      ?: throw ProviderResponseException("$provider candidate content missing 'parts' array.")
  if (parts.size() == 0)
    throw ProviderResponseException("$provider candidate content has empty 'parts'.")
  return parts[0].asJsonObject.get("text")?.asString
    ?: throw ProviderResponseException("$provider part missing 'text' field.")
}

/** The provider answered, but not with the structure we expect. */
class ProviderResponseException(message: String) : Exception(message)
