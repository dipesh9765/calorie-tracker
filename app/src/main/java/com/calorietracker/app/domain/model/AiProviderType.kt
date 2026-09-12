package com.calorietracker.app.domain.model

/**
 * Enumeration of supported AI provider backends for natural language meal parsing.
 *
 * Each provider corresponds to a distinct REST API integration (Google Gemini, OpenAI GPT-4,
 * Anthropic Claude, or DeepSeek).
 *
 * @property displayName User-facing title displayed in setting screens and UI dropdowns.
 */
enum class AiProviderType(val displayName: String) {
  /** Google Gemini API (`gemini-3.6-flash` / `gemini-3.5-flash`) */
  GEMINI("Gemini"),

  /** OpenAI Chat Completions API (`gpt-4o-mini` / `gpt-4o`) */
  OPENAI("OpenAI"),

  /** Anthropic Messages API (`claude-3-5-sonnet`) */
  CLAUDE("Claude"),

  /** DeepSeek Chat API (`deepseek-chat`) */
  DEEPSEEK("DeepSeek");

  companion object {
    /**
     * Resolves an [AiProviderType] enum from a string name, defaulting to [GEMINI] if unrecognised.
     *
     * @param name Name string to match against [displayName].
     * @return Resolved [AiProviderType].
     */
    fun fromString(name: String): AiProviderType {
      return entries.find { it.displayName.equals(name, ignoreCase = true) } ?: GEMINI
    }
  }
}
