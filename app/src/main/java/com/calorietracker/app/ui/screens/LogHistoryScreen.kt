package com.calorietracker.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calorietracker.app.data.model.DailySummary
import com.calorietracker.app.data.model.MealEntry
import com.calorietracker.app.ui.components.DayDetailsDialog
import com.calorietracker.app.ui.components.DeleteConfirmationDialog
import com.calorietracker.app.ui.theme.BackgroundDark
import com.calorietracker.app.ui.theme.PrimaryBlue
import com.calorietracker.app.ui.theme.SurfaceDark
import com.calorietracker.app.ui.theme.TextSecondary

@Composable
fun LogHistoryScreen(allMeals: List<MealEntry>, onDeleteMeal: (String) -> Unit) {
  var selectedSummaryForDialog by remember { mutableStateOf<DailySummary?>(null) }
  var mealPendingDelete by remember { mutableStateOf<MealEntry?>(null) }

  // Deletion confirmation dialog
  mealPendingDelete?.let { meal ->
    DeleteConfirmationDialog(
      foodName = meal.foodName,
      dateIso = meal.dateIso,
      onConfirm = {
        onDeleteMeal(meal.id)
        mealPendingDelete = null
      },
      onDismiss = { mealPendingDelete = null }
    )
  }

  // Day Details Dialog popup
  selectedSummaryForDialog?.let { summary ->
    val dayMeals = allMeals.filter { it.dateIso == summary.dateIso }
    DayDetailsDialog(
      summary = summary,
      dayMeals = dayMeals,
      onDismiss = { selectedSummaryForDialog = null }
    )
  }

  LazyColumn(
    modifier = Modifier.fillMaxSize().background(BackgroundDark).padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    item {
      Text(
        text = "Meal Logs History",
        color = Color.White,
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold
      )
      Text(
        text = "All recorded meals stored locally • Tap date for day summary",
        color = TextSecondary,
        fontSize = 13.sp
      )
    }

    if (allMeals.isEmpty()) {
      item {
        Text(
          text = "No history recorded yet.",
          color = TextSecondary,
          fontSize = 14.sp,
          modifier = Modifier.padding(top = 24.dp)
        )
      }
    } else {
      items(allMeals) { meal ->
        Card(
          colors = CardDefaults.cardColors(containerColor = SurfaceDark),
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                  text = meal.dateIso,
                  color = PrimaryBlue,
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Bold,
                  modifier =
                    Modifier.clickable {
                      val dayMeals = allMeals.filter { it.dateIso == meal.dateIso }
                      selectedSummaryForDialog =
                        DailySummary(
                          dateIso = meal.dateIso,
                          totalCalories = dayMeals.sumOf { it.calories },
                          targetCalories = 2300,
                          totalProtein = dayMeals.sumOf { it.proteinGrams.toDouble() }.toFloat(),
                          targetProtein = 180f,
                          totalCarbs = dayMeals.sumOf { it.carbsGrams.toDouble() }.toFloat(),
                          totalFat = dayMeals.sumOf { it.fatGrams.toDouble() }.toFloat(),
                          creatineTaken = true,
                          mealsCount = dayMeals.size
                        )
                    }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = meal.mealCategory, color = TextSecondary, fontSize = 12.sp)
              }
              Text(
                text = meal.foodName,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 2.dp)
              )
              Text(
                text =
                  "${meal.portionDescription} • P: ${meal.proteinGrams}g, C: ${meal.carbsGrams}g, F: ${meal.fatGrams}g",
                color = TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp)
              )
            }

            Column(horizontalAlignment = Alignment.End) {
              Text(
                text = "${meal.calories} kcal",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
              )
              IconButton(onClick = { mealPendingDelete = meal }, modifier = Modifier.size(32.dp)) {
                Icon(
                  imageVector = Icons.Default.Delete,
                  contentDescription = "Delete",
                  tint = Color.Red.copy(alpha = 0.7f)
                )
              }
            }
          }
        }
      }
    }
  }
}
