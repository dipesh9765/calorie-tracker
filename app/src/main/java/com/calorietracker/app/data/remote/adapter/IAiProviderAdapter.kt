package com.calorietracker.app.data.remote.adapter

/**
 * Adapter interface defining the technical API contract for multi-LLM provider backends.
 *
 * Concrete implementations adapt different REST API schemas (Gemini, OpenAI, Claude, DeepSeek) into
 * a single unified JSON string format for downstream domain mappers.
 */
interface IAiProviderAdapter {
  /**
   * Sends a meal parsing request to the remote LLM API.
   *
   * @param inputText Natural language description of eaten food.
   * @param apiKey User's authentication API key.
   * @param systemInstruction Prompt instructions defining Nagpur/Indian portion context & JSON
   *   schema requirements.
   * @return Raw JSON response string emitted by the LLM model.
   * @throws Exception If network errors, HTTP 4xx/5xx failures, or API key issues occur.
   */
  suspend fun parseMeal(
    inputText: String,
    apiKey: String,
    systemInstruction: String,
    modelName: String = ""
  ): String
}
