package com.calorietracker.app.data.remote.adapter

import com.calorietracker.app.domain.model.ChatRole
import com.calorietracker.app.domain.model.ChatTurn
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

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
  private val gson: Gson = Gson()
) : IAiProviderAdapter {

  override suspend fun parseMeal(
    inputText: String,
    apiKey: String,
    systemInstruction: String,
    modelName: String
  ): String =
    withContext(Dispatchers.IO) {
      val cleanKey = apiKey.trim()
      if (cleanKey.isBlank()) {
        throw IllegalArgumentException(
          "Gemini API Key is blank. Please enter your API key in Profile settings."
        )
      }

      val selectedModel = modelName.trim().ifBlank { "gemini-3.6-flash" }
      val primaryUrl =
        "https://generativelanguage.googleapis.com/v1beta/models/$selectedModel:generateContent"
      val fallbackUrl =
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent"

      // Construct native Gemini JSON schema definition
      val schemaObject =
        JsonObject().apply {
          addProperty("type", "OBJECT")
          add(
            "properties",
            JsonObject().apply {
              add("action", JsonObject().apply { addProperty("type", "STRING") })
              add("targetDateIso", JsonObject().apply { addProperty("type", "STRING") })
              add("foodName", JsonObject().apply { addProperty("type", "STRING") })
              add("portionDescription", JsonObject().apply { addProperty("type", "STRING") })
              add("calories", JsonObject().apply { addProperty("type", "INTEGER") })
              add("proteinGrams", JsonObject().apply { addProperty("type", "NUMBER") })
              add("carbsGrams", JsonObject().apply { addProperty("type", "NUMBER") })
              add("fatGrams", JsonObject().apply { addProperty("type", "NUMBER") })
              add("mealCategory", JsonObject().apply { addProperty("type", "STRING") })
              add("advice", JsonObject().apply { addProperty("type", "STRING") })
            }
          )
          add(
            "required",
            gson.toJsonTree(
              listOf(
                "action",
                "targetDateIso",
                "foodName",
                "portionDescription",
                "calories",
                "proteinGrams",
                "carbsGrams",
                "fatGrams",
                "mealCategory",
                "advice"
              )
            )
          )
        }

      val payload =
        JsonObject().apply {
          // System instruction + user text content payload
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
          // Force strict JSON output & schema enforcement natively in Gemini engine
          add(
            "generationConfig",
            JsonObject().apply {
              addProperty("responseMimeType", "application/json")
              add("responseSchema", schemaObject)
            }
          )
        }

      val requestBody = payload.toString().toRequestBody("application/json".toMediaType())

      // Try primary model (gemini-3.6-flash) first, fallback to gemini-3.5-flash if needed
      try {
        return@withContext executeGeminiCall(primaryUrl, cleanKey, requestBody)
      } catch (e: Exception) {
        val message = e.message ?: ""
        if (
          message.contains("404") || message.contains("not found") || message.contains("NOT_FOUND")
        ) {
          return@withContext executeGeminiCall(fallbackUrl, cleanKey, requestBody)
        } else {
          throw e
        }
      }
    }

  private fun executeGeminiCall(
    url: String,
    apiKey: String,
    requestBody: okhttp3.RequestBody
  ): String {
    // Key goes in a header, not the URL, so it never lands in logs or proxy caches.
    val request =
      Request.Builder().url(url).header("x-goog-api-key", apiKey).post(requestBody).build()

    client.newCall(request).execute().use { response ->
      val responseBody = response.body?.string() ?: ""

      if (!response.isSuccessful) {
        val errorDetails =
          try {
            val errObj = gson.fromJson(responseBody, JsonObject::class.java)
            val errDetails = errObj.getAsJsonObject("error")
            errDetails?.get("message")?.asString ?: responseBody
          } catch (_: Exception) {
            responseBody.ifBlank { "HTTP ${response.code} ${response.message}" }
          }
        throw Exception("Gemini API Error (${response.code}): $errorDetails")
      }

      if (responseBody.isBlank()) {
        throw Exception("Empty response received from Gemini API.")
      }

      val resObj = gson.fromJson(responseBody, JsonObject::class.java)
      val candidates =
        resObj.getAsJsonArray("candidates")
          ?: throw Exception("Invalid Gemini response structure: missing 'candidates' array.")

      if (candidates.size() == 0) {
        throw Exception("Gemini returned zero candidates.")
      }

      val firstCandidate = candidates.get(0).asJsonObject
      val content =
        firstCandidate.getAsJsonObject("content")
          ?: throw Exception("Gemini response missing 'content' in candidate.")

      val parts =
        content.getAsJsonArray("parts")
          ?: throw Exception("Gemini candidate content missing 'parts' array.")

      if (parts.size() == 0) {
        throw Exception("Gemini candidate content has empty 'parts'.")
      }

      val textElement =
        parts.get(0).asJsonObject.get("text")
          ?: throw Exception("Gemini part missing 'text' field.")

      return textElement.asString
    }
  }

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
          "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent",
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
        ?.asString ?: throw Exception("Gemini returned no content (it may have blocked the image).")
    }
}
