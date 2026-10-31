package com.calorietracker.app.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import com.calorietracker.app.domain.model.DailyLog
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "daily_logs")
data class DailyLogEntity(
  @PrimaryKey val dateIso: String,
  val creatineTaken: Boolean = false,
  val waterMl: Int = 0
)

fun DailyLogEntity.toDomain() = DailyLog(dateIso, creatineTaken, waterMl)

@Dao
abstract class DailyLogDao {
  @Query("SELECT * FROM daily_logs WHERE dateIso = :dateIso")
  abstract fun observe(dateIso: String): Flow<DailyLogEntity?>

  @Query("SELECT * FROM daily_logs") abstract fun observeAll(): Flow<List<DailyLogEntity>>

  @Query(
    "INSERT OR IGNORE INTO daily_logs (dateIso, creatineTaken, waterMl) VALUES (:dateIso, 0, 0)"
  )
  protected abstract suspend fun ensureRow(dateIso: String)

  @Query("UPDATE daily_logs SET creatineTaken = :taken WHERE dateIso = :dateIso")
  protected abstract suspend fun updateCreatine(dateIso: String, taken: Boolean)

  @Query("UPDATE daily_logs SET waterMl = MAX(0, waterMl + :ml) WHERE dateIso = :dateIso")
  protected abstract suspend fun updateWater(dateIso: String, ml: Int)

  @Transaction
  open suspend fun setCreatine(dateIso: String, taken: Boolean) {
    ensureRow(dateIso)
    updateCreatine(dateIso, taken)
  }

  @Transaction
  open suspend fun addWater(dateIso: String, ml: Int) {
    ensureRow(dateIso)
    updateWater(dateIso, ml)
  }
}
