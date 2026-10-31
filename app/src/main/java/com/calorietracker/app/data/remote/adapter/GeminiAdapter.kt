package com.calorietracker.app.data.remote.adapter

import com.calorietracker.app.domain.model.ChatRole
import com.calorietracker.app.domain.model.ChatTurn
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/**
 * Concrete adapter implementation for Google Generative AI REST API (`gemini-3.6-flash` /
 * `gemini-3.5-flash`).
 *
 * Responsibilities:
 * - Constructs HTTP POST requests targeting `generativelanguage.googleapis.com`.
 * - Configures `responseMimeType = "application/json"` to enforce valid JSON payload output.
 * - Handles single-read stream parsing and detailed HTTP error extraction.
 * - Uses `gemini-3.6-flash` (latest recommended Gemini model) with `gemini-3.5-flash` fallback.
 */
class GeminiAdapter(
  private val client: OkHttpClient =
    OkHttpClient.Builder()
      .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
      .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
      .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
      .build(),
  private val gson: Gson = Gson(),
  private val baseUrl: String = "https://generativelanguage.googleapis.com"
) : IAiProviderAdapter {

  override suspend fun parseMeal(
    inputText: String,
    apiKey: String,
    systemInstruction: String,
    modelName: String
  ): String =
    withContext(Dispatchers.IO) {
      val key = requireKey(apiKey, PROVIDER)
      val model = modelName.trim().ifBlank { DEFAULT_MODEL }
      val payload =
        JsonObject().apply {
          add(
            "contents",
            gson.toJsonTree(
              listOf(
                mapOf(
                  "parts" to listOf(mapOf("text" to "$systemInstruction\n\nUser Ate: $inputText"))
                )
              )
            )
          )
          // Native structured output: Gemini enforces the schema itself.
          add(
            "generationConfig",
            JsonObject().apply {
              addProperty("responseMimeType", "application/json")
              add("responseSchema", MealResponseSchema.forGemini())
            }
          )
        }

      try {
        generate(model, key, payload)
      } catch (e: ProviderResponseException) {
        // The requested model may not exist (yet) for this key; retry once on the stable one.
        val missing = NOT_FOUND_MARKERS.any { e.message.orEmpty().contains(it) }
        if (!missing || model == FALLBACK_MODEL) throw e
        generate(FALLBACK_MODEL, key, payload)
      }
    }

  /** Key goes in a header, not the URL, so it never lands in logs or proxy caches. */
  private fun generate(model: String, key: String, payload: JsonObject): String =
    client
      .postJson(
        "$baseUrl/v1beta/models/$model:generateContent",
        mapOf("x-goog-api-key" to key),
        payload,
        PROVIDER,
        gson
      )
      .firstCandidateText(PROVIDER)

  override suspend fun chat(
    turns: List<ChatTurn>,
    apiKey: String,
    systemInstruction: String,
    modelName: String
  ): String =
    withContext(Dispatchers.IO) {
      val key = requireKey(apiKey, "Gemini")
      val model = modelName.trim().ifBlank { "gemini-3.6-flash" }
      val contents = JsonArray()
      turns.forEach { turn ->
        val parts = JsonArray()
        parts.add(JsonObject().apply { addProperty("text", turn.text) })
        turn.image?.let { img ->
          parts.add(
            JsonObject().apply {
              add(
                "inlineData",
                JsonObject().apply {
                  addProperty("mimeType", img.mimeType)
                  addProperty("data", img.base64)
                }
              )
            }
          )
        }
        contents.add(
          JsonObject().apply {
            addProperty("role", if (turn.role == ChatRole.USER) "user" else "model")
            add("parts", parts)
          }
        )
      }
      val payload =
        JsonObject().apply {
          add(
            "systemInstruction",
            JsonObject().apply {
              add(
                "parts",
                JsonArray().apply {
                  add(JsonObject().apply { addProperty("text", systemInstruction) })
                }
              )
            }
          )
          add("contents", contents)
          add(
            "generationConfig",
            JsonObject().apply { addProperty("responseMimeType", "application/json") }
          )
        }
      val res =
        client.postJson(
          "$baseUrl/v1beta/models/$model:generateContent",
          mapOf("x-goog-api-key" to key),
          payload,
          "Gemini",
          gson
        )
      res
        .getAsJsonArray("candidates")
        ?.firstOrNull()
        ?.asJsonObject
        ?.getAsJsonObject("content")
        ?.getAsJsonArray("parts")
        ?.firstOrNull()
        ?.asJsonObject
        ?.get("text")
        ?.asString
        ?: throw ProviderResponseException(
          "Gemini returned no content (it may have blocked the image)."
        )
    }

  private companion object {
    const val PROVIDER = "Gemini"
    const val DEFAULT_MODEL = "gemini-3.6-flash"
    const val FALLBACK_MODEL = "gemini-3.5-flash"
    val NOT_FOUND_MARKERS = listOf("404", "not found", "NOT_FOUND")
  }
}
