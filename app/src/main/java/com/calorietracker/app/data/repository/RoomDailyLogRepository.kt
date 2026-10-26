package com.calorietracker.app.data.repository

import com.calorietracker.app.data.local.DailyLogDao
import com.calorietracker.app.data.local.toDomain
import com.calorietracker.app.domain.model.DailyLog
import com.calorietracker.app.domain.repository.IDailyLogRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class RoomDailyLogRepository @Inject constructor(private val dao: DailyLogDao) :
  IDailyLogRepository {
  override fun observe(dateIso: String): Flow<DailyLog> =
    dao.observe(dateIso).map { it?.toDomain() ?: DailyLog(dateIso) }

  override fun observeAll(): Flow<Map<String, DailyLog>> =
    dao.observeAll().map { rows -> rows.associate { it.dateIso to it.toDomain() } }

  override suspend fun setCreatine(dateIso: String, taken: Boolean) =
    dao.setCreatine(dateIso, taken)

  override suspend fun addWater(dateIso: String, ml: Int) = dao.addWater(dateIso, ml)
}
