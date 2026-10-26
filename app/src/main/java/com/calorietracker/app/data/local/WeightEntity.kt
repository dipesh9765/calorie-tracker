package com.calorietracker.app.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** One body-weight reading, logged whenever the user saves a different weight in their profile. */
@Entity(tableName = "weights")
data class WeightEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val dateIso: String,
  val weightKg: Float,
  val timestamp: Long
)

@Dao
interface WeightDao {
  @Query("SELECT * FROM weights ORDER BY timestamp DESC")
  fun observeAll(): Flow<List<WeightEntity>>

  @Query("SELECT * FROM weights ORDER BY timestamp DESC LIMIT :limit")
  suspend fun latest(limit: Int): List<WeightEntity>

  @Insert suspend fun insert(entry: WeightEntity): Long
}
