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

/** Concrete adapter implementation for OpenAI Chat Completions API (`gpt-4o-mini`). */
class OpenAiAdapter(
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
          "OpenAI API Key is blank. Please enter your API key in Profile settings."
        )
      }

      val selectedModel = modelName.trim().ifBlank { "gpt-4o-mini" }
      val url = "https://api.openai.com/v1/chat/completions"

      // Build strict JSON schema format for OpenAI
      val jsonSchemaFormat =
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
                  add(
                    "properties",
                    JsonObject().apply {
                      add("action", JsonObject().apply { addProperty("type", "string") })
                      add("targetDateIso", JsonObject().apply { addProperty("type", "string") })
                      add("foodName", JsonObject().apply { addProperty("type", "string") })
                      add(
                        "portionDescription",
                        JsonObject().apply { addProperty("type", "string") }
                      )
                      add("calories", JsonObject().apply { addProperty("type", "integer") })
                      add("proteinGrams", JsonObject().apply { addProperty("type", "number") })
                      add("carbsGrams", JsonObject().apply { addProperty("type", "number") })
                      add("fatGrams", JsonObject().apply { addProperty("type", "number") })
                      add("mealCategory", JsonObject().apply { addProperty("type", "string") })
                      add("advice", JsonObject().apply { addProperty("type", "string") })
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
                  addProperty("additionalProperties", false)
                }
              )
            }
          )
        }

      val payload =
        JsonObject().apply {
          addProperty("model", selectedModel)
          add("response_format", jsonSchemaFormat)
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

      val request =
        Request.Builder()
          .url(url)
          .header("Authorization", "Bearer $cleanKey")
          .post(payload.toString().toRequestBody("application/json".toMediaType()))
          .build()

      client.newCall(request).execute().use { response ->
        val responseBody = response.body?.string() ?: ""

        if (!response.isSuccessful) {
          val errorDetails =
            try {
              val errObj = gson.fromJson(responseBody, JsonObject::class.java)
              val errObjDetail = errObj.getAsJsonObject("error")
              errObjDetail?.get("message")?.asString ?: responseBody
            } catch (_: Exception) {
              responseBody.ifBlank { "HTTP ${response.code} ${response.message}" }
            }
          throw Exception("OpenAI API Error (${response.code}): $errorDetails")
        }

        if (responseBody.isBlank()) {
          throw Exception("Empty response received from OpenAI API.")
        }

        val resObj = gson.fromJson(responseBody, JsonObject::class.java)
        val choices =
          resObj.getAsJsonArray("choices")
            ?: throw Exception("Invalid OpenAI response structure: missing 'choices' array.")

        if (choices.size() == 0) {
          throw Exception("OpenAI returned zero choices.")
        }

        return@withContext choices
          .get(0)
          .asJsonObject
          .getAsJsonObject("message")
          .get("content")
          .asString
      }
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
          "https://api.openai.com/v1/chat/completions",
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
        ?.asString ?: throw Exception("OpenAI returned no content.")
    }
}
