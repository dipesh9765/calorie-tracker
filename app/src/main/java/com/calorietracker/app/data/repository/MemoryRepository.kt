package com.calorietracker.app.data.repository

import com.calorietracker.app.data.local.MemoryCategory
import com.calorietracker.app.data.local.MemoryDao
import com.calorietracker.app.data.local.MemoryEntity
import com.calorietracker.app.domain.model.MemoryOp
import com.calorietracker.app.domain.model.MemoryUpdate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/** Stores the facts the dietitian remembers about the user. Edited by the user or by the AI. */
@Singleton
class MemoryRepository @Inject constructor(private val dao: MemoryDao) {

  fun observeAll(): Flow<List<MemoryEntity>> = dao.observeAll()

  suspend fun getAll(): List<MemoryEntity> = dao.getAll()

  suspend fun add(category: String, content: String) {
    val text = content.trim()
    if (text.isEmpty()) return
    val now = System.currentTimeMillis()
    dao.insert(
      MemoryEntity(
        category = MemoryCategory.normalize(category),
        content = text,
        createdAt = now,
        updatedAt = now
      )
    )
  }

  suspend fun edit(id: Long, category: String, content: String) {
    val existing = dao.getById(id) ?: return
    val text = content.trim()
    if (text.isEmpty()) return
    dao.update(
      existing.copy(
        category = MemoryCategory.normalize(category),
        content = text,
        updatedAt = System.currentTimeMillis()
      )
    )
  }

  suspend fun delete(id: Long) = dao.deleteById(id)

  /**
   * Applies changes proposed by the AI and returns a short human-readable line per applied change.
   * Updates/deletes that reference an unknown id are ignored; an UPDATE with no id is stored as a
   * new memory so a fact is never silently dropped.
   */
  suspend fun apply(updates: List<MemoryUpdate>): List<String> {
    val notes = mutableListOf<String>()
    for (u in updates) {
      when (u.op) {
        MemoryOp.ADD -> {
          if (u.content.isBlank()) continue
          add(u.category, u.content)
          notes += "Remembered: ${u.content.trim()}"
        }
        MemoryOp.UPDATE -> {
          if (u.content.isBlank()) continue
          val id = u.id
          if (id != null && dao.getById(id) != null) {
            edit(id, u.category, u.content)
            notes += "Updated memory: ${u.content.trim()}"
          } else if (id == null) {
            add(u.category, u.content)
            notes += "Remembered: ${u.content.trim()}"
          }
        }
        MemoryOp.DELETE -> {
          val id = u.id ?: continue
          val existing = dao.getById(id) ?: continue
          dao.deleteById(id)
          notes += "Forgot: ${existing.content}"
        }
      }
    }
    return notes
  }

  companion object {
    /** Block injected into every AI prompt; ids let the model target updates/deletes. */
    fun formatForPrompt(
      memories: List<MemoryEntity>,
      emptyText: String = "(nothing remembered yet)"
    ): String =
      if (memories.isEmpty()) emptyText
      else memories.joinToString("\n") { "- [id=${it.id}] (${it.category}) ${it.content}" }
  }
}
