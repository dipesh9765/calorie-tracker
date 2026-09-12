package com.calorietracker.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calorietracker.app.data.model.DailySummary
import com.calorietracker.app.data.model.MealEntry
import com.calorietracker.app.ui.theme.*

/**
 * Enterprise Modal Dialog displaying complete breakdown of meals eaten on a specific day, along
 * with combined total calories and macronutrient statistics.
 */
@Composable
fun DayDetailsDialog(summary: DailySummary, dayMeals: List<MealEntry>, onDismiss: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = SurfaceDark,
    shape = RoundedCornerShape(20.dp),
    confirmButton = {
      TextButton(onClick = onDismiss) {
        Text("Close", color = PrimaryBlue, fontWeight = FontWeight.Bold)
      }
    },
    title = {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column {
          Text(
            text = "Day Details",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
          )
          Text(
            text = summary.dateIso,
            color = PrimaryBlue,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
          )
        }
        IconButton(onClick = onDismiss) {
          Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Close",
            tint = TextSecondary
          )
        }
      }
    },
    text = {
      LazyColumn(
        modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        // Header Card - Combined Daily Totals
        item {
          Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(modifier = Modifier.padding(14.dp)) {
              Text(
                text = "Combined Daily Summary",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
              )
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Column {
                  Text("Calories", color = TextSecondary, fontSize = 11.sp)
                  Text(
                    text = "${summary.totalCalories} kcal",
                    color =
                      if (summary.totalCalories <= summary.targetCalories) AccentEmerald
                      else AccentOrange,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                  )
                }
                Column {
                  Text("Protein", color = TextSecondary, fontSize = 11.sp)
                  Text(
                    text = "${summary.totalProtein.toInt()}g / ${summary.targetProtein.toInt()}g",
                    color = PrimaryBlue,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                  )
                }
                Column {
                  Text("Carbs", color = TextSecondary, fontSize = 11.sp)
                  Text(
                    text = "${summary.totalCarbs.toInt()}g",
                    color = AccentOrange,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                  )
                }
                Column {
                  Text("Fat", color = TextSecondary, fontSize = 11.sp)
                  Text(
                    text = "${summary.totalFat.toInt()}g",
                    color = AccentPurple,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                  )
                }
              }
            }
          }
        }

        // Food Items Header
        item {
          Text(
            text = "Food Logs (${dayMeals.size} items)",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 4.dp)
          )
        }

        if (dayMeals.isEmpty()) {
          item {
            Text(
              text = "No detailed meal items recorded for this date.",
              color = TextSecondary,
              fontSize = 13.sp,
              modifier = Modifier.padding(vertical = 8.dp)
            )
          }
        } else {
          items(dayMeals) { meal ->
            Card(
              colors = CardDefaults.cardColors(containerColor = BackgroundDark),
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier.fillMaxWidth()
            ) {
              Column(modifier = Modifier.padding(12.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                  ) {
                    Icon(
                      imageVector = Icons.Default.Restaurant,
                      contentDescription = "Meal",
                      tint = PrimaryBlue,
                      modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                      text = meal.foodName,
                      color = Color.White,
                      fontSize = 14.sp,
                      fontWeight = FontWeight.SemiBold
                    )
                  }
                  Text(
                    text = "${meal.calories} kcal",
                    color = AccentEmerald,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                  )
                }
                Text(
                  text = meal.portionDescription,
                  color = TextSecondary,
                  fontSize = 12.sp,
                  modifier = Modifier.padding(top = 2.dp)
                )
                Row(
                  modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                  horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                  Text(
                    "P: ${meal.proteinGrams}g",
                    color = PrimaryBlue,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                  )
                  Text(
                    "C: ${meal.carbsGrams}g",
                    color = AccentOrange,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                  )
                  Text(
                    "F: ${meal.fatGrams}g",
                    color = AccentPurple,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                  )
                }
              }
            }
          }
        }
      }
    }
  )
}
