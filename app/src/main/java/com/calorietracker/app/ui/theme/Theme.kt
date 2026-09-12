package com.calorietracker.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun CalorieTrackerTheme(content: @Composable () -> Unit) {
  MaterialTheme(colorScheme = DarkColorScheme, content = content)
}
