package com.calorietracker.app.data.repository

import com.calorietracker.app.data.remote.adapter.ClaudeAdapter
import com.calorietracker.app.data.remote.adapter.DeepSeekAdapter
import com.calorietracker.app.data.remote.adapter.GeminiAdapter
import com.calorietracker.app.data.remote.adapter.IAiProviderAdapter
import com.calorietracker.app.data.remote.adapter.OpenAiAdapter
import com.calorietracker.app.domain.model.AiProviderType
import com.calorietracker.app.domain.model.ChatTurn
import com.calorietracker.app.domain.model.DietitianReply
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Runs one dietitian conversation turn against the user's chosen provider. */
class DietitianChatRepository(
  private val adapters: Map<AiProviderType, IAiProviderAdapter> =
    mapOf(
      AiProviderType.GEMINI to GeminiAdapter(),
      AiProviderType.OPENAI to OpenAiAdapter(),
      AiProviderType.CLAUDE to ClaudeAdapter(),
      AiProviderType.DEEPSEEK to DeepSeekAdapter()
    )
) {
  suspend fun reply(
    turns: List<ChatTurn>,
    provider: AiProviderType,
    apiKey: String,
    modelName: String,
    context: DietitianContext
  ): Result<DietitianReply> =
    withContext(Dispatchers.IO) {
      try {
        val raw =
          adapters.getValue(provider).chat(turns, apiKey, DietitianPrompt.build(context), modelName)
        Result.success(DietitianReplyParser.parse(raw, context.todayIso))
      } catch (e: java.net.SocketTimeoutException) {
        Result.failure(Exception("The request to ${provider.displayName} timed out. Please retry."))
      } catch (e: java.net.UnknownHostException) {
        Result.failure(Exception("No internet connection. Please check your network and retry."))
      } catch (e: Exception) {
        Result.failure(e)
      }
    }
}
