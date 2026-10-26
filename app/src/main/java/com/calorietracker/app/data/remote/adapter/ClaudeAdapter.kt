package com.calorietracker.app.data.remote.adapter

import com.calorietracker.app.domain.model.ChatRole
import com.calorietracker.app.domain.model.ChatTurn
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/** Concrete adapter implementation for Anthropic Claude API (`claude-3-5-sonnet-20240620`). */
class ClaudeAdapter(
  private val client: OkHttpClient =
    OkHttpClient.Builder()
      .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
      .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
      .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
      .build(),
  private val gson: Gson = Gson(),
  private val baseUrl: String = "https://api.anthropic.com"
) : IAiProviderAdapter {

  override suspend fun parseMeal(
    inputText: String,
    apiKey: String,
    systemInstruction: String,
    modelName: String
  ): String =
    withContext(Dispatchers.IO) {
      val key = requireKey(apiKey, PROVIDER)
      val payload =
        JsonObject().apply {
          addProperty("model", modelName.trim().ifBlank { DEFAULT_MODEL })
          addProperty("max_tokens", MEAL_MAX_TOKENS)
          addProperty("system", systemInstruction)
          add(
            "messages",
            gson.toJsonTree(
              listOf(mapOf("role" to "user", "content" to "Log this meal: $inputText"))
            )
          )
        }
      client
        .postJson(
          "$baseUrl/v1/messages",
          mapOf("x-api-key" to key, "anthropic-version" to ANTHROPIC_VERSION),
          payload,
          PROVIDER,
          gson
        )
        .firstContentText(PROVIDER)
    }

  override suspend fun chat(
    turns: List<ChatTurn>,
    apiKey: String,
    systemInstruction: String,
    modelName: String
  ): String =
    withContext(Dispatchers.IO) {
      val key = requireKey(apiKey, "Claude")
      val messages = JsonArray()
      turns.forEach { turn ->
        val content = JsonArray()
        turn.image?.let { img ->
          content.add(
            JsonObject().apply {
              addProperty("type", "image")
              add(
                "source",
                JsonObject().apply {
                  addProperty("type", "base64")
                  addProperty("media_type", img.mimeType)
                  addProperty("data", img.base64)
                }
              )
            }
          )
        }
        content.add(
          JsonObject().apply {
            addProperty("type", "text")
            addProperty("text", turn.text)
          }
        )
        messages.add(
          JsonObject().apply {
            addProperty("role", if (turn.role == ChatRole.USER) "user" else "assistant")
            add("content", content)
          }
        )
      }
      val payload =
        JsonObject().apply {
          addProperty("model", modelName.trim().ifBlank { "claude-3-5-sonnet-20240620" })
          addProperty("max_tokens", 1500)
          addProperty("system", systemInstruction)
          add("messages", messages)
        }
      val res =
        client.postJson(
          "$baseUrl/v1/messages",
          mapOf("x-api-key" to key, "anthropic-version" to "2023-06-01"),
          payload,
          "Claude",
          gson
        )
      res
        .getAsJsonArray("content")
        ?.firstOrNull { it.asJsonObject.get("type")?.asString == "text" }
        ?.asJsonObject
        ?.get("text")
        ?.asString ?: throw ProviderResponseException("Claude returned no text content.")
    }

  private companion object {
    const val PROVIDER = "Claude"
    const val DEFAULT_MODEL = "claude-3-5-sonnet-20240620"
    const val ANTHROPIC_VERSION = "2023-06-01"
    const val MEAL_MAX_TOKENS = 1000
  }
}
