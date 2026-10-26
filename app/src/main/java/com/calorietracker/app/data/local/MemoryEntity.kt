package com.calorietracker.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A durable fact about the user that the AI dietitian should remember (supplements, habits,
 * allergies, typical portions). Ids are small integers so the LLM can refer to them when it
 * proposes an update or delete.
 */
@Entity(tableName = "memories")
data class MemoryEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val category: String,
  val content: String,
  val createdAt: Long,
  val updatedAt: Long
)

object MemoryCategory {
  const val SUPPLEMENT = "Supplement"
  const val HABIT = "Habit"
  const val PREFERENCE = "Preference"
  const val HEALTH = "Health"
  const val OTHER = "Other"

  val all = listOf(SUPPLEMENT, HABIT, PREFERENCE, HEALTH, OTHER)

  fun normalize(raw: String?): String = all.firstOrNull { it.equals(raw?.trim(), true) } ?: OTHER
}
