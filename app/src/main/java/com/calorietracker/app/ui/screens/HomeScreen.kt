package com.calorietracker.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calorietracker.app.data.model.MealEntry
import com.calorietracker.app.data.model.UserProfile
import com.calorietracker.app.ui.components.DeleteConfirmationDialog
import com.calorietracker.app.ui.components.LiquidBubbleProgress
import com.calorietracker.app.ui.components.MacroCard
import com.calorietracker.app.ui.theme.*

@Composable
fun HomeScreen(
  userProfile: UserProfile,
  todayMeals: List<MealEntry>,
  todayTotalCalories: Int,
  todayTotalProtein: Float,
  todayTotalCarbs: Float,
  todayTotalFat: Float,
  creatineTaken: Boolean,
  coachAdvice: String,
  isLoadingAi: Boolean,
  onLogMealSubmitted: (String) -> Unit,
  onToggleCreatine: () -> Unit,
  onDeleteMeal: (MealEntry) -> Unit
) {
  var mealInputText by remember { mutableStateOf("") }
  var mealPendingDelete by remember { mutableStateOf<MealEntry?>(null) }

  // Deletion confirmation dialog
  mealPendingDelete?.let { meal ->
    DeleteConfirmationDialog(
      foodName = meal.foodName,
      dateIso = meal.dateIso,
      onConfirm = {
        onDeleteMeal(meal)
        mealPendingDelete = null
      },
      onDismiss = { mealPendingDelete = null }
    )
  }

  LazyColumn(
    modifier = Modifier.fillMaxSize().background(BackgroundDark).padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    // Top Header
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column {
          Text(
            text = "Hello, ${userProfile.name}",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
          )
          Text(
            text =
              "Goal: ${userProfile.weightKg.toInt()}kg → ${userProfile.targetWeightKg.toInt()}kg (2,300 kcal)",
            color = TextSecondary,
            fontSize = 13.sp
          )
        }

        // Creatine Pill Toggle
        Button(
          onClick = onToggleCreatine,
          colors =
            ButtonDefaults.buttonColors(
              containerColor = if (creatineTaken) AccentEmerald else SurfaceCard
            ),
          shape = RoundedCornerShape(20.dp),
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
        ) {
          Icon(
            imageVector = if (creatineTaken) Icons.Default.Check else Icons.Default.FitnessCenter,
            contentDescription = "Creatine",
            tint = if (creatineTaken) Color.Black else Color.White,
            modifier = Modifier.size(16.dp)
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = if (creatineTaken) "Creatine 5g Done" else "+ 5g Creatine",
            color = if (creatineTaken) Color.Black else Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
          )
        }
      }
    }

    // Circular Liquid Progress View (Reference Design)
    item {
      Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
      ) {
        LiquidBubbleProgress(
          currentCalories = todayTotalCalories,
          targetCalories = userProfile.targetDailyCalories
        )
      }
    }

    // Macro Cards Row (Protein 180g, Carbs, Fat)
    item {
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MacroCard(
          label = "Protein",
          currentGrams = todayTotalProtein,
          targetGrams = userProfile.targetDailyProteinGrams,
          color = PrimaryBlue,
          modifier = Modifier.weight(1f)
        )
        MacroCard(
          label = "Carbs",
          currentGrams = todayTotalCarbs,
          targetGrams = 240f,
          color = AccentOrange,
          modifier = Modifier.weight(1f)
        )
        MacroCard(
          label = "Fat",
          currentGrams = todayTotalFat,
          targetGrams = 65f,
          color = AccentPurple,
          modifier = Modifier.weight(1f)
        )
      }
    }

    // AI Nutritionist Advice Callout Card
    item {
      Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
          Icon(
            imageVector = Icons.Default.Lightbulb,
            contentDescription = "Coach Advice",
            tint = AccentOrange,
            modifier = Modifier.size(24.dp)
          )
          Spacer(modifier = Modifier.width(12.dp))
          Column {
            Text(
              text = "AI Coach Recommendation",
              color = Color.White,
              fontSize = 15.sp,
              fontWeight = FontWeight.Bold
            )
            Text(
              text =
                coachAdvice.ifEmpty {
                  val remainingP =
                    (userProfile.targetDailyProteinGrams - todayTotalProtein).coerceAtLeast(0f)
                  "You need ${remainingP.toInt()}g more protein to hit your 180g target today. Suggest 1.5 scoops Nakpro Whey or 150g dry chicken breast."
                },
              color = TextSecondary,
              fontSize = 13.sp,
              modifier = Modifier.padding(top = 4.dp)
            )
          }
        }
      }
    }

    // Meal Input Field (Natural Language Logging)
    item {
      Column {
        Text(
          text = "Log What You Ate",
          color = Color.White,
          fontSize = 16.sp,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
          OutlinedTextField(
            value = mealInputText,
            onValueChange = { mealInputText = it },
            placeholder = {
              Text(
                "e.g. 2 rotis with 200g chicken curry & 1 scoop Nakpro whey",
                color = TextSecondary,
                fontSize = 13.sp
              )
            },
            modifier = Modifier.weight(1f),
            colors =
              OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PrimaryBlue,
                unfocusedBorderColor = SurfaceCard,
                focusedContainerColor = SurfaceDark,
                unfocusedContainerColor = SurfaceDark,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
              ),
            shape = RoundedCornerShape(14.dp)
          )

          Spacer(modifier = Modifier.width(8.dp))

          IconButton(
            onClick = {
              if (mealInputText.isNotBlank() && !isLoadingAi) {
                onLogMealSubmitted(mealInputText)
                mealInputText = ""
              }
            },
            modifier = Modifier.size(52.dp).clip(CircleShape).background(PrimaryBlue)
          ) {
            if (isLoadingAi) {
              CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp
              )
            } else {
              Icon(
                imageVector = Icons.Default.Send,
                contentDescription = "Log Meal",
                tint = Color.White
              )
            }
          }
        }
      }
    }

    // Today's Meals Section Header
    item {
      Text(
        text = "Today's Logged Meals (${todayMeals.size})",
        color = Color.White,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp)
      )
    }

    if (todayMeals.isEmpty()) {
      item {
        Text(
          text = "No meals logged yet today. Type what you ate above!",
          color = TextSecondary,
          fontSize = 13.sp,
          modifier = Modifier.padding(vertical = 12.dp)
        )
      }
    } else {
      items(todayMeals) { meal ->
        Card(
          colors = CardDefaults.cardColors(containerColor = SurfaceDark),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = meal.foodName,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
              )
              Text(
                text = "${meal.portionDescription} • ${meal.proteinGrams}g Protein",
                color = TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp)
              )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(
                text = "${meal.calories} kcal",
                color = PrimaryBlue,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
              )
              Spacer(modifier = Modifier.width(8.dp))
              IconButton(onClick = { mealPendingDelete = meal }, modifier = Modifier.size(32.dp)) {
                Icon(
                  imageVector = Icons.Default.DeleteOutline,
                  contentDescription = "Delete Log",
                  tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                  modifier = Modifier.size(20.dp)
                )
              }
            }
          }
        }
      }
    }
  }
}
