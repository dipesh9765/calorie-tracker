package com.calorietracker.app.domain.util

import java.time.Clock
import java.time.LocalDate

/**
 * ISO-8601 date (YYYY-MM-DD) for "today" according to this clock; inject a fixed clock in tests.
 */
fun Clock.todayIso(): String = LocalDate.now(this).toString()

/** ISO-8601 date [days] before today according to this clock. */
fun Clock.daysAgoIso(days: Long): String = LocalDate.now(this).minusDays(days).toString()
