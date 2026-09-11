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
 * Concrete adapter implementation for DeepSeek API (`deepseek-chat`).
 */
class DeepSeekAdapter(
    private val client: OkHttpClient = OkHttpClient(),
    private val gson: Gson = Gson()
) : IAiProviderAdapter {

    override suspend fun parseMeal(inputText: String, apiKey: String, systemInstruction: String, modelName: String): String = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank()) {
            throw IllegalArgumentException("DeepSeek API Key is blank. Please enter your API key in Profile settings.")
        }

        val selectedModel = modelName.trim().ifBlank { "deepseek-chat" }
        val url = "https://api.deepseek.com/v1/chat/completions"
        val payload = JsonObject().apply {
            addProperty("model", selectedModel)
            add("response_format", JsonObject().apply { addProperty("type", "json_object") })
            add("messages", gson.toJsonTree(listOf(
                mapOf("role" to "system", "content" to systemInstruction),
                mapOf("role" to "user", "content" to "Log this meal: $inputText")
            )))
        }

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $cleanKey")
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
                throw Exception("DeepSeek API Error (${response.code}): $errorDetails")
            }

            if (responseBody.isBlank()) {
                throw Exception("Empty response received from DeepSeek API.")
            }

            val resObj = gson.fromJson(responseBody, JsonObject::class.java)
            val choices = resObj.getAsJsonArray("choices")
                ?: throw Exception("Invalid DeepSeek response structure: missing 'choices' array.")

            if (choices.size() == 0) {
                throw Exception("DeepSeek returned zero choices.")
            }

            return@withContext choices.get(0).asJsonObject
                .getAsJsonObject("message")
                .get("content").asString
        }
    }
}
