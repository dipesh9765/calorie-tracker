package com.calorietracker.app.data.remote.adapter

import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Shared POST + error unwrapping for the chat endpoints of every provider. */
internal fun OkHttpClient.postJson(
  url: String,
  headers: Map<String, String>,
  payload: JsonObject,
  providerName: String,
  gson: Gson
): JsonObject {
  val request =
    Request.Builder()
      .url(url)
      .apply { headers.forEach { (k, v) -> header(k, v) } }
      .post(payload.toString().toRequestBody("application/json".toMediaType()))
      .build()

  newCall(request).execute().use { response ->
    val body = response.body?.string() ?: ""
    if (!response.isSuccessful) {
      val details =
        try {
          gson
            .fromJson(body, JsonObject::class.java)
            .getAsJsonObject("error")
            ?.get("message")
            ?.asString ?: body
        } catch (_: Exception) {
          body.ifBlank { "HTTP ${response.code} ${response.message}" }
        }
      throw Exception("$providerName API Error (${response.code}): $details")
    }
    if (body.isBlank()) throw Exception("Empty response received from $providerName API.")
    return gson.fromJson(body, JsonObject::class.java)
  }
}

internal fun requireKey(apiKey: String, providerName: String): String {
  val key = apiKey.trim()
  if (key.isBlank()) {
    throw IllegalArgumentException(
      "$providerName API Key is blank. Please enter your API key in Profile settings."
    )
  }
  return key
}
