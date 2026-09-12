package com.calorietracker.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calorietracker.app.data.model.DailySummary
import com.calorietracker.app.data.model.MealEntry
import com.calorietracker.app.ui.components.DayDetailsDialog
import com.calorietracker.app.ui.theme.*

@Composable
fun AnalyticsScreen(
  weeklySummaries: List<DailySummary>,
  allMeals: List<MealEntry> = emptyList(),
  targetCalories: Int = 2300,
  maintenanceCalories: Int = 2900
) {
  var selectedTab by remember { mutableIntStateOf(0) } // 0 = Week, 1 = Month
  var selectedSummaryForDialog by remember { mutableStateOf<DailySummary?>(null) }

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
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    item {
      Text(
        text = "Intake Analytics",
        color = Color.White,
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold
      )
      Text(
        text = "Weekly & Monthly Calorie Trends • Tap any day for details",
        color = TextSecondary,
        fontSize = 13.sp
      )
    }

    // Tab Selector (Week vs Month)
    item {
      TabRow(
        selectedTabIndex = selectedTab,
        containerColor = SurfaceDark,
        contentColor = PrimaryBlue,
        modifier = Modifier.height(44.dp)
      ) {
        Tab(
          selected = selectedTab == 0,
          onClick = { selectedTab = 0 },
          text = {
            Text("Weekly View", color = if (selectedTab == 0) PrimaryBlue else TextSecondary)
          }
        )
        Tab(
          selected = selectedTab == 1,
          onClick = { selectedTab = 1 },
          text = {
            Text("Monthly View", color = if (selectedTab == 1) PrimaryBlue else TextSecondary)
          }
        )
      }
    }

    // Bar Chart Card
    item {
      Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Text(
            text =
              if (selectedTab == 0) "Daily Calories vs Target (2,300 kcal)"
              else "Monthly Intake Consistency",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
          )

          Spacer(modifier = Modifier.height(16.dp))

          // Canvas Bar Chart
          Box(modifier = Modifier.fillMaxWidth().height(200.dp)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
              val width = size.width
              val height = size.height
              val maxCal = 3200f
              val barWidth = width / (weeklySummaries.size * 2f).coerceAtLeast(1f)

              // Draw Target Deficit Line (2,300 kcal)
              val targetY = height - (targetCalories / maxCal * height)
              drawLine(
                color = AccentEmerald,
                start = Offset(0f, targetY),
                end = Offset(width, targetY),
                strokeWidth = 2.dp.toPx()
              )

              // Draw Maintenance Line (2,900 kcal)
              val maintY = height - (maintenanceCalories / maxCal * height)
              drawLine(
                color = AccentOrange.copy(alpha = 0.5f),
                start = Offset(0f, maintY),
                end = Offset(width, maintY),
                strokeWidth = 1.dp.toPx()
              )

              // Draw Bars for each day
              weeklySummaries.forEachIndexed { index, summary ->
                val x = (index * 2 + 0.5f) * barWidth
                val barHeight = (summary.totalCalories / maxCal * height).coerceAtMost(height)
                val y = height - barHeight

                val barColor =
                  when {
                    summary.totalCalories == 0 -> Color.Gray.copy(alpha = 0.2f)
                    summary.totalCalories <= targetCalories -> PrimaryBlue
                    else -> AccentOrange
                  }

                drawRect(
                  color = barColor,
                  topLeft = Offset(x, y),
                  size = Size(barWidth * 0.8f, barHeight)
                )
              }
            }
          }

          // Days Legend (Clickable to view day details)
          Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround
          ) {
            weeklySummaries.forEach { summary ->
              val dayLabel =
                try {
                  summary.dateIso.substring(8)
                } catch (_: Exception) {
                  ""
                }
              Text(
                text = dayLabel,
                color = TextSecondary,
                fontSize = 11.sp,
                modifier = Modifier.clickable { selectedSummaryForDialog = summary }
              )
            }
          }

          // Legend Labels
          Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(modifier = Modifier.size(10.dp).background(PrimaryBlue))
              Spacer(modifier = Modifier.width(4.dp))
              Text("Logged Cal", color = TextSecondary, fontSize = 11.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(modifier = Modifier.size(10.dp).background(AccentEmerald))
              Spacer(modifier = Modifier.width(4.dp))
              Text("Target (2,300)", color = TextSecondary, fontSize = 11.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(modifier = Modifier.size(10.dp).background(AccentOrange))
              Spacer(modifier = Modifier.width(4.dp))
              Text("Maintenance (2,900)", color = TextSecondary, fontSize = 11.sp)
            }
          }
        }
      }
    }

    // Daily Summaries Breakdown List (Clickable cards to open day details)
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "History Summary",
          color = Color.White,
          fontSize = 16.sp,
          fontWeight = FontWeight.Bold
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = Icons.Default.Info,
            contentDescription = "Info",
            tint = PrimaryBlue,
            modifier = Modifier.size(14.dp)
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(text = "Tap card for food details", color = TextSecondary, fontSize = 11.sp)
        }
      }
    }

    items(weeklySummaries.size) { index ->
      val summary = weeklySummaries[index]
      Card(
        onClick = { selectedSummaryForDialog = summary },
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          modifier = Modifier.padding(14.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column {
            Text(
              text = summary.dateIso,
              color = Color.White,
              fontSize = 14.sp,
              fontWeight = FontWeight.Bold
            )
            Text(
              text = "${summary.totalProtein.toInt()}g Protein • ${summary.mealsCount} meals",
              color = TextSecondary,
              fontSize = 12.sp
            )
          }
          Column(horizontalAlignment = Alignment.End) {
            Text(
              text = "${summary.totalCalories} / 2,300 kcal",
              color = if (summary.totalCalories <= 2300) AccentEmerald else AccentOrange,
              fontSize = 14.sp,
              fontWeight = FontWeight.Bold
            )
            val status = if (summary.totalCalories <= 2300) "In Deficit" else "Over Budget"
            Text(text = status, color = TextSecondary, fontSize = 11.sp)
          }
        }
      }
    }
  }
}
