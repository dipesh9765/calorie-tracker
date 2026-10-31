package com.calorietracker.app.support

import com.calorietracker.app.data.local.MemoryDao
import com.calorietracker.app.data.local.MemoryEntity
import com.calorietracker.app.data.local.WeightDao
import com.calorietracker.app.data.local.WeightEntity
import com.calorietracker.app.domain.model.Meal
import com.calorietracker.app.domain.repository.MealExporter
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/** Runs `viewModelScope` work eagerly on the test thread. */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule : TestWatcher() {
  override fun starting(description: Description) = Dispatchers.setMain(UnconfinedTestDispatcher())

  override fun finished(description: Description) = Dispatchers.resetMain()
}

class FakeMemoryDao : MemoryDao {
  private val rows = MutableStateFlow<List<MemoryEntity>>(emptyList())
  private var nextId = 1L

  override fun observeAll(): Flow<List<MemoryEntity>> = rows

  override suspend fun getAll(): List<MemoryEntity> = rows.value

  override suspend fun getById(id: Long): MemoryEntity? = rows.value.firstOrNull { it.id == id }

  override suspend fun insert(memory: MemoryEntity): Long {
    val id = nextId++
    rows.value = rows.value + memory.copy(id = id)
    return id
  }

  override suspend fun update(memory: MemoryEntity) {
    rows.value = rows.value.map { if (it.id == memory.id) memory else it }
  }

  override suspend fun deleteById(id: Long) {
    rows.value = rows.value.filterNot { it.id == id }
  }
}

class FakeWeightDao : WeightDao {
  val rows = MutableStateFlow<List<WeightEntity>>(emptyList())

  override fun observeAll(): Flow<List<WeightEntity>> = rows

  override suspend fun latest(limit: Int): List<WeightEntity> =
    rows.value.sortedByDescending { it.timestamp }.take(limit)

  override suspend fun insert(entry: WeightEntity): Long {
    rows.value = rows.value + entry
    return rows.value.size.toLong()
  }
}

class FakeMealExporter(var succeed: Boolean = true) : MealExporter {
  var jsonCalls = 0
  var csvCalls = 0
  var lastMeals: List<Meal> = emptyList()

  override fun exportToJson(meals: List<Meal>): File? {
    jsonCalls++
    lastMeals = meals
    return if (succeed) File("export.json") else null
  }

  override fun exportToCsv(meals: List<Meal>): File? {
    csvCalls++
    lastMeals = meals
    return if (succeed) File("export.csv") else null
  }
}
