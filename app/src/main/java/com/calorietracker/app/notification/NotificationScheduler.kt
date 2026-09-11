package com.calorietracker.app.notification

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object NotificationScheduler {
    private const val WORK_TAG = "ai_coach_periodic_work"

    fun schedulePeriodicCoachNotifications(context: Context) {
        val workRequest = PeriodicWorkRequestBuilder<CoachNotificationWorker>(2, TimeUnit.HOURS)
            .addTag(WORK_TAG)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_TAG,
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    fun cancelCoachNotifications(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_TAG)
    }
}
