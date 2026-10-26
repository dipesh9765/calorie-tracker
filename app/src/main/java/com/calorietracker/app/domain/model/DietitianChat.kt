package com.calorietracker.app.domain.model

enum class ChatRole {
  USER,
  ASSISTANT
}

/** JPEG payload, already downscaled and base64-encoded (no line wraps). */
data class ChatImage(val base64: String, val mimeType: String = "image/jpeg")

/** One message in the provider-facing conversation history. */
data class ChatTurn(val role: ChatRole, val text: String, val image: ChatImage? = null)

/** ASK = dietitian needs more info, FINAL = meal estimate is ready, CHAT = no meal involved. */
enum class ReplyStatus {
  ASK,
  FINAL,
  CHAT
}

enum class MemoryOp {
  ADD,
  UPDATE,
  DELETE
}

data class MemoryUpdate(val op: MemoryOp, val id: Long?, val category: String, val content: String)

data class DietitianReply(
  val status: ReplyStatus,
  val message: String,
  val questions: List<String> = emptyList(),
  val meal: Meal? = null,
  val memoryUpdates: List<MemoryUpdate> = emptyList(),
  /** Current body weight if the user reported it in this message. */
  val weightKg: Float? = null
)
