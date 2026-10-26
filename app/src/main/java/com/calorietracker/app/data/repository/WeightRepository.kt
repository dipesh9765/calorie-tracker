package com.calorietracker.app.data.repository

import com.calorietracker.app.data.local.WeightDao
import com.calorietracker.app.data.local.WeightEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class WeightRepository(private val dao: WeightDao) {

  fun observeAll() = dao.observeAll()

  /** Logs [weightKg] unless it equals the latest reading. Returns true if a row was added. */
  suspend fun logIfChanged(weightKg: Float): Boolean {
    val last = dao.latest(1).firstOrNull()
    if (last != null && abs(last.weightKg - weightKg) < 0.05f) return false
    val now = System.currentTimeMillis()
    dao.insert(
      WeightEntity(
        dateIso = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(now)),
        weightKg = weightKg,
        timestamp = now
      )
    )
    return true
  }

  suspend fun trendSummary(): String = describeTrend(dao.latest(30))

  companion object {
    /** [readings] newest first. One line the dietitian can use. */
    fun describeTrend(readings: List<WeightEntity>): String {
      if (readings.isEmpty()) return "No weigh-ins logged."
      val newest = readings.first()
      val oldest = readings.last()
      if (readings.size == 1) return "${"%.1f".format(newest.weightKg)} kg on ${newest.dateIso} (only reading)."
      val delta = newest.weightKg - oldest.weightKg
      val sign = if (delta > 0) "+" else ""
      return "${"%.1f".format(newest.weightKg)} kg on ${newest.dateIso}; " +
        "$sign${"%.1f".format(delta)} kg since ${oldest.dateIso} (${readings.size} readings)."
    }
  }
}
