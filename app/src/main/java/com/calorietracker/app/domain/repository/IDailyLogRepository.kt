package com.calorietracker.app.domain.repository

import com.calorietracker.app.domain.model.DailyLog
import kotlinx.coroutines.flow.Flow

interface IDailyLogRepository {
  /** Emits the log for [dateIso]; an empty default when nothing was recorded that day. */
  fun observe(dateIso: String): Flow<DailyLog>

  /** All recorded days keyed by date. */
  fun observeAll(): Flow<Map<String, DailyLog>>

  suspend fun setCreatine(dateIso: String, taken: Boolean)

  /** Adds (or with a negative value removes) water, never going below zero. */
  suspend fun addWater(dateIso: String, ml: Int)
}
