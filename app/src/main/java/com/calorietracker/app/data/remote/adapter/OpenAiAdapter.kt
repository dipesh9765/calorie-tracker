package com.calorietracker.app.data.remote.adapter

import com.calorietracker.app.domain.model.ChatRole
import com.calorietracker.app.domain.model.ChatTurn
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/** Concrete adapter implementation for OpenAI Chat Completions API (`gpt-4o-mini`). */
class OpenAiAdapter(
  private val client: OkHttpClient =
    OkHttpClient.Builder()
      .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
      .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
      .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
      .build(),
  private val gson: Gson = Gson(),
  private val baseUrl: String = "https://api.openai.com"
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
      val key = requireKey(apiKey, "OpenAI")
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
            val img = turn.image
            if (img == null) {
              addProperty("content", turn.text)
            } else {
              add(
                "content",
                JsonArray().apply {
                  add(
                    JsonObject().apply {
                      addProperty("type", "text")
                      addProperty("text", turn.text)
                    }
                  )
                  add(
                    JsonObject().apply {
                      addProperty("type", "image_url")
                      add(
                        "image_url",
                        JsonObject().apply {
                          addProperty("url", "data:${img.mimeType};base64,${img.base64}")
                        }
                      )
                    }
                  )
                }
              )
            }
          }
        )
      }
      val payload =
        JsonObject().apply {
          addProperty("model", modelName.trim().ifBlank { "gpt-4o-mini" })
          add("response_format", JsonObject().apply { addProperty("type", "json_object") })
          add("messages", messages)
        }
      val res =
        client.postJson(
          "$baseUrl/v1/chat/completions",
          mapOf("Authorization" to "Bearer $key"),
          payload,
          "OpenAI",
          gson
        )
      res
        .getAsJsonArray("choices")
        ?.firstOrNull()
        ?.asJsonObject
        ?.getAsJsonObject("message")
        ?.get("content")
        ?.asString ?: throw ProviderResponseException("OpenAI returned no content.")
    }

  private companion object {
    const val PROVIDER = "OpenAI"
    const val DEFAULT_MODEL = "gpt-4o-mini"
    val RESPONSE_FORMAT = MealResponseSchema.forOpenAi()
  }
}
