package com.calorietracker.app.domain.model

/** Per-day habits that are not meals: the creatine dose and water intake. */
data class DailyLog(val dateIso: String, val creatineTaken: Boolean = false, val waterMl: Int = 0) {
  val waterLiters: Float
    get() = waterMl / ML_PER_LITER

  private companion object {
    const val ML_PER_LITER = 1000f
  }
}
