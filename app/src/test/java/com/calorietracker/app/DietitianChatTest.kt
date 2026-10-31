package com.calorietracker.app

import com.calorietracker.app.data.local.MemoryDao
import com.calorietracker.app.data.local.MemoryEntity
import com.calorietracker.app.data.model.UserProfile
import com.calorietracker.app.data.repository.DietitianContext
import com.calorietracker.app.data.repository.DietitianPrompt
import com.calorietracker.app.data.repository.DietitianReplyParser
import com.calorietracker.app.data.repository.MemoryRepository
import com.calorietracker.app.domain.model.MemoryOp
import com.calorietracker.app.domain.model.MemoryUpdate
import com.calorietracker.app.domain.model.ReplyStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DietitianChatTest {

  private val today = "2026-10-26"

  // ---------- reply parsing ----------

  @Test
  fun `ASK reply keeps questions and has no meal`() {
    val raw =
      """{"status":"ASK","message":"Roughly 400-550 kcal so far.","questions":["Roti size?","Any ghee?"],"meal":null,"memoryUpdates":[]}"""
    val r = DietitianReplyParser.parse(raw, today)
    assertEquals(ReplyStatus.ASK, r.status)
    assertEquals(listOf("Roti size?", "Any ghee?"), r.questions)
    assertNull(r.meal)
  }

  @Test
  fun `FINAL reply parses meal and defaults to today when date is invalid`() {
    val raw =
      """```json
      {"status":"FINAL","message":"Done","meal":{"targetDateIso":"yesterday","foodName":"Dal rice","portionDescription":"1 plate","calories":"620","proteinGrams":21.5,"carbsGrams":90,"fatGrams":"14","mealCategory":"Lunch"}}
      ```"""
    val r = DietitianReplyParser.parse(raw, today)
    assertEquals(ReplyStatus.FINAL, r.status)
    val meal = r.meal!!
    assertEquals(620, meal.calories)
    assertEquals(21.5f, meal.proteinGrams, 0.001f)
    assertEquals(14f, meal.fatGrams, 0.001f)
    assertEquals(today, meal.dateIso)
  }

  @Test
  fun `FINAL with zero calories is not accepted as a result`() {
    val raw =
      """{"status":"FINAL","message":"?","questions":["How big was it?"],"meal":{"foodName":"x","calories":0}}"""
    val r = DietitianReplyParser.parse(raw, today)
    assertEquals(ReplyStatus.ASK, r.status)
    assertNull(r.meal)
  }

  @Test
  fun `non JSON reply degrades to plain chat message`() {
    val r = DietitianReplyParser.parse("Sure, eat more protein.", today)
    assertEquals(ReplyStatus.CHAT, r.status)
    assertEquals("Sure, eat more protein.", r.message)
  }

  @Test
  fun `memory updates are parsed and unknown ops dropped`() {
    val raw =
      """{"status":"CHAT","message":"ok","memoryUpdates":[
        {"op":"ADD","id":null,"category":"Supplement","content":"Nakpro whey, 33 g scoop"},
        {"op":"update","id":4,"category":"Habit","content":"Gym at 6am"},
        {"op":"EXPLODE","id":1,"content":"x"}]}"""
    val r = DietitianReplyParser.parse(raw, today)
    assertEquals(2, r.memoryUpdates.size)
    assertEquals(MemoryOp.ADD, r.memoryUpdates[0].op)
    assertNull(r.memoryUpdates[0].id)
    assertEquals(MemoryOp.UPDATE, r.memoryUpdates[1].op)
    assertEquals(4L, r.memoryUpdates[1].id)
  }

  // ---------- memory repository ----------

  private class FakeMemoryDao : MemoryDao {
    val rows = mutableListOf<MemoryEntity>()
    private var seq = 1L
    private val flow = MutableStateFlow<List<MemoryEntity>>(emptyList())

    override fun observeAll(): Flow<List<MemoryEntity>> = flow

    override suspend fun getAll() = rows.toList()

    override suspend fun getById(id: Long) = rows.find { it.id == id }

    override suspend fun insert(memory: MemoryEntity): Long {
      val saved = memory.copy(id = seq++)
      rows += saved
      return saved.id
    }

    override suspend fun update(memory: MemoryEntity) {
      rows.replaceAll { if (it.id == memory.id) memory else it }
    }

    override suspend fun deleteById(id: Long) {
      rows.removeAll { it.id == id }
    }
  }

  @Test
  fun `apply adds updates and deletes memories and reports each change`() = runBlocking {
    val dao = FakeMemoryDao()
    val repo = MemoryRepository(dao)

    val notes =
      repo.apply(listOf(MemoryUpdate(MemoryOp.ADD, null, "supplement", "Whey 33 g scoop")))
    assertEquals(1, dao.rows.size)
    assertEquals("Supplement", dao.rows[0].category)
    assertEquals(listOf("Remembered: Whey 33 g scoop"), notes)

    val id = dao.rows[0].id
    repo.apply(listOf(MemoryUpdate(MemoryOp.UPDATE, id, "Supplement", "Whey 40 g scoop")))
    assertEquals("Whey 40 g scoop", dao.rows[0].content)

    repo.apply(listOf(MemoryUpdate(MemoryOp.DELETE, id, "Supplement", "")))
    assertTrue(dao.rows.isEmpty())
  }

  @Test
  fun `apply ignores unknown ids and blank content`() = runBlocking {
    val dao = FakeMemoryDao()
    val repo = MemoryRepository(dao)
    val notes =
      repo.apply(
        listOf(
          MemoryUpdate(MemoryOp.UPDATE, 99, "Habit", "ghost"),
          MemoryUpdate(MemoryOp.DELETE, 99, "Habit", ""),
          MemoryUpdate(MemoryOp.ADD, null, "Habit", "   ")
        )
      )
    assertTrue(dao.rows.isEmpty())
    assertTrue(notes.isEmpty())
  }

  @Test
  fun `UPDATE without id is stored as a new memory instead of being lost`() = runBlocking {
    val dao = FakeMemoryDao()
    MemoryRepository(dao)
      .apply(listOf(MemoryUpdate(MemoryOp.UPDATE, null, "Habit", "No sugar in tea")))
    assertEquals(1, dao.rows.size)
  }

  // ---------- prompt ----------

  @Test
  fun `prompt contains memories with ids profile and log`() {
    val memories = listOf(MemoryEntity(7, "Supplement", "Nakpro whey, 33 g scoop", 0, 0))
    val prompt =
      DietitianPrompt.build(
        DietitianContext(
          profile = UserProfile(name = "Asha", targetDailyCalories = 1800),
          memories = memories,
          todayIso = today,
          yesterdayIso = "2026-10-25",
          dayOfWeek = "Monday",
          todaySummary = "Nothing logged yet.",
          yesterdaySummary = "Nothing logged."
        )
      )
    assertTrue(prompt.contains("[id=7] (Supplement) Nakpro whey, 33 g scoop"))
    assertTrue(prompt.contains("Asha"))
    assertTrue(prompt.contains("1800 kcal"))
    assertNotNull(Regex("\"status\"").find(prompt))
  }
}
