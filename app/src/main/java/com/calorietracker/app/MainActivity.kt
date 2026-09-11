package com.calorietracker.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.calorietracker.app.data.exporter.DataExporter
import com.calorietracker.app.data.model.DailySummary
import com.calorietracker.app.data.model.MealEntry
import com.calorietracker.app.data.model.UserProfile
import com.calorietracker.app.data.repository.AiRepository
import com.calorietracker.app.ui.screens.*
import com.calorietracker.app.ui.theme.BackgroundDark
import com.calorietracker.app.ui.theme.CalorieTrackerTheme
import com.calorietracker.app.ui.theme.PrimaryBlue
import com.calorietracker.app.ui.theme.SurfaceDark
import com.calorietracker.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import com.calorietracker.app.data.repository.PreferencesRepository

class MainActivity : ComponentActivity() {

    private val aiRepository = AiRepository()
    private lateinit var dataExporter: DataExporter
    private lateinit var preferencesRepository: PreferencesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dataExporter = DataExporter(this)
        preferencesRepository = PreferencesRepository(this)

        setContent {
            CalorieTrackerTheme {
                MainAppContainer()
            }
        }
    }

    @Composable
    fun MainAppContainer() {
        var selectedScreenIndex by remember { mutableIntStateOf(0) }
        var userProfile by remember { mutableStateOf(preferencesRepository.getUserProfile()) }
        val snackbarHostState = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()

        // Pre-seeded chat logs from Sept 7 to Sept 10
        val allMeals = remember {
            mutableStateListOf(
                MealEntry(
                    dateIso = "2026-09-07",
                    foodName = "Haldiram Bread, Saoji Gravy, Eggs, Tarri Poha",
                    portionDescription = "Daily food log",
                    calories = 1685,
                    proteinGrams = 110.5f,
                    carbsGrams = 175f,
                    fatGrams = 48f,
                    mealCategory = "Day Log"
                ),
                MealEntry(
                    dateIso = "2026-09-08",
                    foodName = "Rotis, Chicken Dry/Curry, Nakpro Whey, Sabudana Khichdi",
                    portionDescription = "Daily food log",
                    calories = 2302,
                    proteinGrams = 154.5f,
                    carbsGrams = 220f,
                    fatGrams = 62f,
                    mealCategory = "Day Log"
                ),
                MealEntry(
                    dateIso = "2026-09-09",
                    foodName = "3 Rotis, Dahi Samosa, 200g Chicken, Nakpro Whey",
                    portionDescription = "Daily food log",
                    calories = 1945,
                    proteinGrams = 147.5f,
                    carbsGrams = 195f,
                    fatGrams = 55f,
                    mealCategory = "Day Log"
                ),
                MealEntry(
                    dateIso = "2026-09-10",
                    foodName = "3 Rotis, Chicken Curry, 2 scoops Nakpro Whey, Eggs",
                    portionDescription = "Daily food log",
                    calories = 2615,
                    proteinGrams = 192.0f,
                    carbsGrams = 230f,
                    fatGrams = 70f,
                    mealCategory = "Day Log"
                )
            )
        }

        fun deleteMealWithUndo(meal: MealEntry) {
            val removedIndex = allMeals.indexOfFirst { it.id == meal.id }
            if (removedIndex != -1) {
                val removedMeal = allMeals.removeAt(removedIndex)
                scope.launch {
                    val result = snackbarHostState.showSnackbar(
                        message = "Deleted: ${removedMeal.foodName}",
                        actionLabel = "UNDO",
                        duration = SnackbarDuration.Short
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        val restoreIndex = removedIndex.coerceIn(0, allMeals.size)
                        allMeals.add(restoreIndex, removedMeal)
                    }
                }
            }
        }

        val todayIso = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val todayMeals = allMeals.filter { it.dateIso == todayIso }

        val todayTotalCalories = todayMeals.sumOf { it.calories }
        val todayTotalProtein = todayMeals.sumOf { it.proteinGrams.toDouble() }.toFloat()
        val todayTotalCarbs = todayMeals.sumOf { it.carbsGrams.toDouble() }.toFloat()
        val todayTotalFat = todayMeals.sumOf { it.fatGrams.toDouble() }.toFloat()

        var creatineTaken by remember { mutableStateOf(true) }
        var coachAdvice by remember { mutableStateOf("") }
        var isLoadingAi by remember { mutableStateOf(false) }

        // Aggregate Daily Summaries for Analytics
        val weeklySummaries = remember(allMeals.size) {
            val grouped = allMeals.groupBy { it.dateIso }
            grouped.map { (date, meals) ->
                DailySummary(
                    dateIso = date,
                    totalCalories = meals.sumOf { it.calories },
                    targetCalories = userProfile.targetDailyCalories,
                    totalProtein = meals.sumOf { it.proteinGrams.toDouble() }.toFloat(),
                    targetProtein = userProfile.targetDailyProteinGrams,
                    totalCarbs = meals.sumOf { it.carbsGrams.toDouble() }.toFloat(),
                    totalFat = meals.sumOf { it.fatGrams.toDouble() }.toFloat(),
                    creatineTaken = true,
                    mealsCount = meals.size
                )
            }.sortedBy { it.dateIso }
        }

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                NavigationBar(
                    containerColor = SurfaceDark,
                    contentColor = PrimaryBlue
                ) {
                    NavigationBarItem(
                        selected = selectedScreenIndex == 0,
                        onClick = { selectedScreenIndex = 0 },
                        icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                        label = { Text("Today") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            selectedTextColor = Color.White,
                            indicatorColor = PrimaryBlue,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary
                        )
                    )
                    NavigationBarItem(
                        selected = selectedScreenIndex == 1,
                        onClick = { selectedScreenIndex = 1 },
                        icon = { Icon(Icons.Default.BarChart, contentDescription = "Analytics") },
                        label = { Text("Analytics") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            selectedTextColor = Color.White,
                            indicatorColor = PrimaryBlue,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary
                        )
                    )
                    NavigationBarItem(
                        selected = selectedScreenIndex == 2,
                        onClick = { selectedScreenIndex = 2 },
                        icon = { Icon(Icons.Default.History, contentDescription = "History") },
                        label = { Text("Logs") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            selectedTextColor = Color.White,
                            indicatorColor = PrimaryBlue,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary
                        )
                    )
                    NavigationBarItem(
                        selected = selectedScreenIndex == 3,
                        onClick = { selectedScreenIndex = 3 },
                        icon = { Icon(Icons.Default.Person, contentDescription = "Profile") },
                        label = { Text("Profile") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            selectedTextColor = Color.White,
                            indicatorColor = PrimaryBlue,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary
                        )
                    )
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(BackgroundDark)
            ) {
                when (selectedScreenIndex) {
                    0 -> HomeScreen(
                        userProfile = userProfile,
                        todayMeals = todayMeals,
                        todayTotalCalories = todayTotalCalories,
                        todayTotalProtein = todayTotalProtein,
                        todayTotalCarbs = todayTotalCarbs,
                        todayTotalFat = todayTotalFat,
                        creatineTaken = creatineTaken,
                        coachAdvice = coachAdvice,
                        isLoadingAi = isLoadingAi,
                        onLogMealSubmitted = { input ->
                            isLoadingAi = true
                            lifecycleScope.launch {
                                val apiKey = when (userProfile.primaryAiProvider) {
                                    "OpenAI" -> userProfile.openAiApiKey
                                    "Claude" -> userProfile.claudeApiKey
                                    "DeepSeek" -> userProfile.deepSeekApiKey
                                    else -> userProfile.geminiApiKey
                                }.trim()

                                val modelName = when (userProfile.primaryAiProvider) {
                                    "OpenAI" -> userProfile.openAiModel
                                    "Claude" -> userProfile.claudeModel
                                    "DeepSeek" -> userProfile.deepSeekModel
                                    else -> userProfile.geminiModel
                                }.trim()

                                val result = aiRepository.parseMealText(
                                    inputText = input,
                                    provider = userProfile.primaryAiProvider,
                                    apiKey = apiKey,
                                    modelName = modelName
                                )

                                isLoadingAi = false
                                result.onSuccess { parseResult ->
                                    val entry = parseResult.mealEntry
                                    val existingIndex = allMeals.indexOfFirst {
                                        it.dateIso == entry.dateIso && it.foodName.equals(entry.foodName, ignoreCase = true)
                                    }

                                    if (existingIndex != -1) {
                                        allMeals[existingIndex] = entry
                                        Toast.makeText(this@MainActivity, "Updated (${entry.dateIso}): ${entry.foodName}", Toast.LENGTH_SHORT).show()
                                    } else {
                                        allMeals.add(0, entry)
                                        Toast.makeText(this@MainActivity, "Logged (${entry.dateIso}): ${entry.foodName}", Toast.LENGTH_SHORT).show()
                                    }
                                    coachAdvice = parseResult.coachAdvice
                                }.onFailure { error ->
                                    Toast.makeText(this@MainActivity, "AI Parsing Notice: ${error.localizedMessage}", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        onToggleCreatine = { creatineTaken = !creatineTaken },
                        onDeleteMeal = { meal -> deleteMealWithUndo(meal) }
                    )
                    1 -> AnalyticsScreen(
                        weeklySummaries = weeklySummaries,
                        allMeals = allMeals,
                        targetCalories = userProfile.targetDailyCalories,
                        maintenanceCalories = userProfile.tdeeMaintenanceCalories
                    )
                    2 -> LogHistoryScreen(
                        allMeals = allMeals,
                        onDeleteMeal = { id ->
                            val target = allMeals.find { it.id == id }
                            if (target != null) {
                                deleteMealWithUndo(target)
                            }
                        }
                    )
                    3 -> ProfileScreen(
                        userProfile = userProfile,
                        onSaveProfile = { updated ->
                            userProfile = updated
                            preferencesRepository.saveUserProfile(updated)
                            Toast.makeText(this@MainActivity, "Profile and API Keys updated!", Toast.LENGTH_SHORT).show()
                        },
                        onExportJson = {
                            val file = dataExporter.exportToJson(allMeals)
                            val msg = if (file != null) "Exported JSON to ${file.name}" else "Export failed"
                            Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
                        },
                        onExportCsv = {
                            val file = dataExporter.exportToCsv(allMeals)
                            val msg = if (file != null) "Exported CSV to ${file.name}" else "Export failed"
                            Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
                        }
                    )
                }
            }
        }
    }
}
