package com.calorietracker.app.data.remote.adapter

import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Concrete adapter implementation for Anthropic Claude API (`claude-3-5-sonnet-20240620`).
 */
class ClaudeAdapter(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build(),
    private val gson: Gson = Gson()
) : IAiProviderAdapter {

    override suspend fun parseMeal(inputText: String, apiKey: String, systemInstruction: String, modelName: String): String = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank()) {
            throw IllegalArgumentException("Claude API Key is blank. Please enter your API key in Profile settings.")
        }

        val selectedModel = modelName.trim().ifBlank { "claude-3-5-sonnet-20240620" }
        val url = "https://api.anthropic.com/v1/messages"
        val payload = JsonObject().apply {
            addProperty("model", selectedModel)
            addProperty("max_tokens", 1000)
            addProperty("system", systemInstruction)
            add("messages", gson.toJsonTree(listOf(
                mapOf("role" to "user", "content" to "Log this meal: $inputText")
            )))
        }

        val request = Request.Builder()
            .url(url)
            .header("x-api-key", cleanKey)
            .header("anthropic-version", "2023-06-01")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val errorDetails = try {
                    val errObj = gson.fromJson(responseBody, JsonObject::class.java)
                    val errObjDetail = errObj.getAsJsonObject("error")
                    errObjDetail?.get("message")?.asString ?: responseBody
                } catch (_: Exception) {
                    responseBody.ifBlank { "HTTP ${response.code} ${response.message}" }
                }
                throw Exception("Claude API Error (${response.code}): $errorDetails")
            }

            if (responseBody.isBlank()) {
                throw Exception("Empty response received from Claude API.")
            }

            val resObj = gson.fromJson(responseBody, JsonObject::class.java)
            val contentArr = resObj.getAsJsonArray("content")
                ?: throw Exception("Invalid Claude response structure: missing 'content' array.")

            if (contentArr.size() == 0) {
                throw Exception("Claude returned empty content array.")
            }

            return@withContext contentArr.get(0).asJsonObject.get("text").asString
        }
    }
}
