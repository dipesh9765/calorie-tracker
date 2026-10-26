package com.calorietracker.app.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calorietracker.app.data.local.WeightEntity
import com.calorietracker.app.data.model.UserProfile
import com.calorietracker.app.domain.usecase.ActivityLevel
import com.calorietracker.app.domain.usecase.GoalPlanner
import com.calorietracker.app.domain.usecase.GoalType
import com.calorietracker.app.domain.usecase.PlanInput
import com.calorietracker.app.ui.theme.*

private val DIET_TYPES =
  listOf("No restriction", "Vegetarian", "Eggetarian", "Vegan", "Pescatarian", "Jain")
private val RATES = listOf(0.25f, 0.5f, 0.75f, 1.0f)
private val planner = GoalPlanner()

/**
 * Editable body metrics and goal. Shows the generated calorie, protein and water targets live as
 * the user types; "Save & apply" writes them to the profile (optionally overridden by hand).
 */
@Composable
fun ProfileDetailsCard(
  profile: UserProfile,
  weightHistory: List<WeightEntity>,
  onSave: (UserProfile) -> Unit
) {
  var name by remember(profile) { mutableStateOf(profile.name) }
  var age by remember(profile) { mutableStateOf(profile.age.toString()) }
  var gender by remember(profile) { mutableStateOf(profile.gender) }
  var height by remember(profile) { mutableStateOf(trimZero(profile.heightCm)) }
  var weight by remember(profile) { mutableStateOf(trimZero(profile.weightKg)) }
  var targetWeight by remember(profile) { mutableStateOf(trimZero(profile.targetWeightKg)) }
  var goal by remember(profile) { mutableStateOf(GoalType.fromName(profile.goalType)) }
  var rate by remember(profile) { mutableFloatStateOf(profile.weeklyRateKg) }
  var activity by
    remember(profile) { mutableStateOf(ActivityLevel.fromName(profile.activityLevel)) }
  var cuisine by remember(profile) { mutableStateOf(profile.cuisine) }
  var diet by remember(profile) { mutableStateOf(profile.dietType) }
  var manual by remember(profile) { mutableStateOf(false) }
  var manualCalories by remember(profile) { mutableStateOf(profile.targetDailyCalories.toString()) }
  var manualProtein by
    remember(profile) { mutableStateOf(profile.targetDailyProteinGrams.toInt().toString()) }

  val ageV = age.toIntOrNull()?.takeIf { it in 12..100 }
  val heightV = height.toFloatOrNull()?.takeIf { it in 100f..250f }
  val weightV = weight.toFloatOrNull()?.takeIf { it in 30f..300f }
  val targetV = targetWeight.toFloatOrNull()?.takeIf { it in 30f..300f }
  val valid = ageV != null && heightV != null && weightV != null && targetV != null

  val plan =
    if (valid) {
      planner.plan(PlanInput(weightV!!, heightV!!, ageV!!, gender, activity, goal, targetV!!, rate))
    } else {
      null
    }

  val manualCal = manualCalories.toIntOrNull()?.takeIf { it in 800..6000 }
  val manualPro = manualProtein.toFloatOrNull()?.takeIf { it in 20f..400f }
  val canSave = valid && (!manual || (manualCal != null && manualPro != null))

  Card(
    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
    shape = RoundedCornerShape(16.dp),
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Person, contentDescription = null, tint = PrimaryBlue)
        Spacer(Modifier.width(8.dp))
        Column {
          Text("About you", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
          Text(
            if (profile.profileConfigured)
              "Update these any time and your targets are recalculated."
            else "Fill this in once so your targets and your dietitian fit you.",
            color = TextSecondary,
            fontSize = 12.sp
          )
        }
      }

      OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        label = { Text("Name") },
        placeholder = { Text("What should your dietitian call you?") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
      )

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NumberField(
          "Age",
          age,
          { age = it },
          ageV == null,
          Modifier.weight(1f),
          KeyboardType.Number
        )
        NumberField("Height (cm)", height, { height = it }, heightV == null, Modifier.weight(1f))
      }
      ChipRow {
        listOf("Male", "Female").forEach { g ->
          FilterChip(
            selected = gender.equals(g, true),
            onClick = { gender = g },
            label = { Text(g) }
          )
        }
      }

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NumberField("Weight (kg)", weight, { weight = it }, weightV == null, Modifier.weight(1f))
        NumberField(
          "Target (kg)",
          targetWeight,
          { targetWeight = it },
          targetV == null,
          Modifier.weight(1f)
        )
      }

      SectionLabel("Goal")
      ChipRow {
        GoalType.entries.forEach { g ->
          FilterChip(selected = goal == g, onClick = { goal = g }, label = { Text(g.label) })
        }
      }
      if (goal != GoalType.MAINTAIN) {
        SectionLabel("Pace (kg per week)")
        ChipRow {
          RATES.forEach { r ->
            FilterChip(selected = rate == r, onClick = { rate = r }, label = { Text(trimZero(r)) })
          }
        }
      }

      SectionLabel("Activity level")
      ChipRow {
        ActivityLevel.entries.forEach { a ->
          FilterChip(
            selected = activity == a,
            onClick = { activity = a },
            label = { Text(a.label) }
          )
        }
      }
      Text(activity.description, color = TextSecondary, fontSize = 12.sp)

      SectionLabel("Food")
      OutlinedTextField(
        value = cuisine,
        onValueChange = { cuisine = it },
        label = { Text("Usual cuisine") },
        placeholder = { Text("e.g. North Indian, South Indian, Mediterranean") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
      )
      ChipRow {
        DIET_TYPES.forEach { d ->
          FilterChip(selected = diet == d, onClick = { diet = d }, label = { Text(d) })
        }
      }

      HorizontalDivider(color = SurfaceDark)

      if (plan != null) {
        Text(
          "Your daily targets",
          color = Color.White,
          fontWeight = FontWeight.Bold,
          fontSize = 15.sp
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Stat(
            "Calories",
            "${if (manual && manualCal != null) manualCal else plan.targetCalories}",
            "kcal",
            AccentEmerald
          )
          Stat(
            "Protein",
            "${if (manual && manualPro != null) manualPro.toInt() else plan.proteinGrams.toInt()}",
            "g",
            PrimaryBlue
          )
          Stat("Water", "${plan.waterLiters}", "L", AccentPurple)
        }
        Text(
          "BMR ${plan.bmr} kcal · Maintenance ${plan.tdee} kcal" +
            (plan.weeksToGoal?.let { " · about $it weeks to goal" } ?: ""),
          color = TextSecondary,
          fontSize = 12.sp
        )
        plan.warnings.forEach { Text("• $it", color = AccentOrange, fontSize = 12.sp) }
      } else {
        Text(
          "Enter valid age, height and weights to see your targets.",
          color = AccentOrange,
          fontSize = 13.sp
        )
      }

      Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(checked = manual, onCheckedChange = { manual = it })
        Spacer(Modifier.width(8.dp))
        Text("Set calories and protein myself", color = TextSecondary, fontSize = 13.sp)
      }
      if (manual) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          NumberField(
            "Calories",
            manualCalories,
            { manualCalories = it },
            manualCal == null,
            Modifier.weight(1f),
            KeyboardType.Number
          )
          NumberField(
            "Protein (g)",
            manualProtein,
            { manualProtein = it },
            manualPro == null,
            Modifier.weight(1f),
            KeyboardType.Number
          )
        }
      }

      Button(
        onClick = {
          val p = plan ?: return@Button
          onSave(
            profile.copy(
              name = name.trim(),
              age = ageV!!,
              gender = gender,
              heightCm = heightV!!,
              weightKg = weightV!!,
              targetWeightKg = targetV!!,
              goalType = goal.name,
              weeklyRateKg = rate,
              activityLevel = activity.name,
              cuisine = cuisine.trim().ifBlank { "Indian" },
              dietType = diet,
              bmrCalories = p.bmr,
              tdeeMaintenanceCalories = p.tdee,
              targetDailyCalories = if (manual) manualCal!! else p.targetCalories,
              targetDailyProteinGrams = if (manual) manualPro!! else p.proteinGrams,
              targetWaterLiters = p.waterLiters,
              profileConfigured = true
            )
          )
        },
        enabled = canSave,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
      ) {
        Text("Save & apply new targets", color = Color.White, fontWeight = FontWeight.Bold)
      }

      if (weightHistory.isNotEmpty()) {
        HorizontalDivider(color = SurfaceDark)
        Text("Weight history", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        weightHistory.take(6).forEachIndexed { i, w ->
          val prev = weightHistory.getOrNull(i + 1)
          val delta = prev?.let { w.weightKg - it.weightKg }
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text(w.dateIso, color = TextSecondary, fontSize = 13.sp)
            Text(
              "${"%.1f".format(w.weightKg)} kg" +
                (delta?.let { " (${if (it > 0) "+" else ""}${"%.1f".format(it)})" } ?: ""),
              color = Color.White,
              fontSize = 13.sp
            )
          }
        }
      }
    }
  }
}

private fun trimZero(v: Float): String = if (v % 1f == 0f) v.toInt().toString() else v.toString()

@Composable
private fun SectionLabel(text: String) {
  Text(text, color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun ChipRow(content: @Composable RowScope.() -> Unit) {
  Row(
    modifier = Modifier.horizontalScroll(rememberScrollState()),
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    content = content
  )
}

@Composable
private fun NumberField(
  label: String,
  value: String,
  onChange: (String) -> Unit,
  isError: Boolean,
  modifier: Modifier,
  keyboard: KeyboardType = KeyboardType.Decimal
) {
  OutlinedTextField(
    value = value,
    onValueChange = onChange,
    label = { Text(label) },
    isError = isError,
    modifier = modifier,
    singleLine = true,
    keyboardOptions = KeyboardOptions(keyboardType = keyboard)
  )
}

@Composable
private fun Stat(label: String, value: String, unit: String, color: Color) {
  Column {
    Text(label, color = TextSecondary, fontSize = 12.sp)
    Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = 22.sp)
    Text(unit, color = TextSecondary, fontSize = 11.sp)
  }
}
