package com.calorietracker.app.data.remote.adapter

import com.calorietracker.app.domain.model.ChatRole
import com.calorietracker.app.domain.model.ChatTurn
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/** Concrete adapter implementation for DeepSeek API (`deepseek-chat`). */
class DeepSeekAdapter(
  private val client: OkHttpClient =
    OkHttpClient.Builder()
      .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
      .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
      .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
      .build(),
  private val gson: Gson = Gson(),
  private val baseUrl: String = "https://api.deepseek.com"
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
          add("response_format", RESPONSE_FORMAT)
          add(
            "messages",
            gson.toJsonTree(
              listOf(
                mapOf("role" to "system", "content" to systemInstruction),
                mapOf("role" to "user", "content" to "Log this meal: $inputText")
              )
            )
          )
        }
      client
        .postJson(
          "$baseUrl/v1/chat/completions",
          mapOf("Authorization" to "Bearer $key"),
          payload,
          PROVIDER,
          gson
        )
        .firstChoiceContent(PROVIDER)
    }

  override suspend fun chat(
    turns: List<ChatTurn>,
    apiKey: String,
    systemInstruction: String,
    modelName: String
  ): String =
    withContext(Dispatchers.IO) {
      val key = requireKey(apiKey, "DeepSeek")
      require(turns.none { it.image != null }) {
        "DeepSeek can't read images. Switch to Gemini, OpenAI or Claude in Profile to send photos."
      }
      val messages = JsonArray()
      messages.add(
        JsonObject().apply {
          addProperty("role", "system")
          addProperty("content", systemInstruction)
        }
      )
      turns.forEach { turn ->
        messages.add(
          JsonObject().apply {
            addProperty("role", if (turn.role == ChatRole.USER) "user" else "assistant")
            addProperty("content", turn.text)
          }
        )
      }
      val payload =
        JsonObject().apply {
          addProperty("model", modelName.trim().ifBlank { "deepseek-chat" })
          add("response_format", JsonObject().apply { addProperty("type", "json_object") })
          add("messages", messages)
        }
      val res =
        client.postJson(
          "$baseUrl/v1/chat/completions",
          mapOf("Authorization" to "Bearer $key"),
          payload,
          "DeepSeek",
          gson
        )
      res
        .getAsJsonArray("choices")
        ?.firstOrNull()
        ?.asJsonObject
        ?.getAsJsonObject("message")
        ?.get("content")
        ?.asString ?: throw ProviderResponseException("DeepSeek returned no content.")
    }

  private companion object {
    const val PROVIDER = "DeepSeek"
    const val DEFAULT_MODEL = "deepseek-chat"
    val RESPONSE_FORMAT = JsonObject().apply { addProperty("type", "json_object") }
  }
}
