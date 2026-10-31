package com.calorietracker.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.calorietracker.app.notification.NotificationHelper
import com.calorietracker.app.notification.NotificationScheduler
import com.calorietracker.app.ui.AppRoot
import com.calorietracker.app.ui.theme.CalorieTrackerTheme
import dagger.hilt.android.AndroidEntryPoint

/** Hosts the Compose UI; all state lives in ViewModels and all wiring in Hilt. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    NotificationHelper.createNotificationChannel(this)
    NotificationScheduler.schedulePeriodicCoachNotifications(this)
    setContent { CalorieTrackerTheme { AppRoot() } }
  }
}
