package com.calorietracker.app

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.calorietracker.app.data.exporter.DataExporter
import com.calorietracker.app.data.local.AppDatabase
import com.calorietracker.app.data.local.MealDao
import com.calorietracker.app.data.local.toMealEntity
import com.calorietracker.app.data.local.toMealEntry
import com.calorietracker.app.data.model.DailySummary
import com.calorietracker.app.data.model.MealEntry
import com.calorietracker.app.data.model.UserProfile
import com.calorietracker.app.data.repository.AiRepository
import com.calorietracker.app.data.repository.MemoryRepository
import com.calorietracker.app.data.repository.PreferencesRepository
import com.calorietracker.app.data.repository.WeightRepository
import com.calorietracker.app.notification.NotificationHelper
import com.calorietracker.app.notification.NotificationScheduler
import com.calorietracker.app.presentation.DietitianChatViewModel
import com.calorietracker.app.ui.screens.*
import com.calorietracker.app.ui.theme.BackgroundDark
import com.calorietracker.app.ui.theme.CalorieTrackerTheme
import com.calorietracker.app.ui.theme.PrimaryBlue
import com.calorietracker.app.ui.theme.SurfaceDark
import com.calorietracker.app.ui.theme.TextSecondary
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

  private val aiRepository = AiRepository()
  private lateinit var dataExporter: DataExporter
  private lateinit var preferencesRepository: PreferencesRepository
  private lateinit var database: AppDatabase
  private lateinit var mealDao: MealDao
  private lateinit var memoryRepository: MemoryRepository
  private lateinit var weightRepository: WeightRepository
  private val chatViewModel: DietitianChatViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    dataExporter = DataExporter(this)
    preferencesRepository = PreferencesRepository(this)
    database = AppDatabase.getInstance(this)
    mealDao = database.mealDao()
    memoryRepository = MemoryRepository(database.memoryDao())
    weightRepository = WeightRepository(database.weightDao())

    NotificationHelper.createNotificationChannel(this)
    NotificationScheduler.schedulePeriodicCoachNotifications(this)

    setContent { CalorieTrackerTheme { MainAppContainer() } }
  }

  private fun activeApiKey(p: UserProfile): String =
    when (p.primaryAiProvider) {
      "OpenAI" -> p.openAiApiKey
      "Claude" -> p.claudeApiKey
      "DeepSeek" -> p.deepSeekApiKey
      else -> p.geminiApiKey
    }.trim()

  @Composable
  fun MainAppContainer() {
    var selectedScreenIndex by remember { mutableIntStateOf(0) }
    var showMemory by remember { mutableStateOf(false) }
    var userProfile by remember { mutableStateOf(preferencesRepository.getUserProfile()) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Notification Permission Request for Android 13+
    val permissionLauncher =
      rememberLauncherForActivityResult(contract = ActivityResultContracts.RequestPermission()) { _
        ->
      }

    LaunchedEffect(Unit) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        if (
          ContextCompat.checkSelfPermission(
            this@MainActivity,
            Manifest.permission.POST_NOTIFICATIONS
          ) != PackageManager.PERMISSION_GRANTED
        ) {
          permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
      }
    }

    val photoPicker =
      rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) chatViewModel.attachImage(uri)
      }
    // The camera app can outlive our process, so the target Uri must survive recreation.
    var cameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    val takePicture =
      rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = cameraUri
        if (saved && uri != null) chatViewModel.attachImage(uri)
        cameraUri = null
      }
    fun launchCamera() {
      val dir = File(cacheDir, "camera").apply { mkdirs() }
      dir.listFiles()?.forEach { it.delete() } // drop photos from earlier sessions
      val file = File.createTempFile("meal_", ".jpg", dir)
      val uri = FileProvider.getUriForFile(this@MainActivity, "$packageName.fileprovider", file)
      cameraUri = uri
      takePicture.launch(uri)
    }

    val profileVersion by chatViewModel.profileVersion.collectAsState()
    LaunchedEffect(profileVersion) {
      // The chat can update weight and targets; pick those up.
      if (profileVersion > 0) userProfile = preferencesRepository.getUserProfile()
    }
    val weightHistory by
      remember { weightRepository.observeAll() }.collectAsState(initial = emptyList())

    val chatMessages by chatViewModel.messages.collectAsState()
    val chatLoading by chatViewModel.isLoading.collectAsState()
    val chatPendingImage by chatViewModel.pendingImage.collectAsState()
    val memories by remember { memoryRepository.observeAll() }.collectAsState(initial = emptyList())

    // Collect all meals reactively from Room SQLite database
    val allMealEntities by mealDao.getAllMeals().collectAsState(initial = emptyList())
    val allMeals = remember(allMealEntities) { allMealEntities.map { it.toMealEntry() } }

    fun deleteMealWithUndo(meal: MealEntry) {
      scope.launch {
        withContext(Dispatchers.IO) { mealDao.deleteMealById(meal.id) }
        val result =
          snackbarHostState.showSnackbar(
            message = "Deleted: ${meal.foodName}",
            actionLabel = "UNDO",
            duration = SnackbarDuration.Short
          )
        if (result == SnackbarResult.ActionPerformed) {
          withContext(Dispatchers.IO) { mealDao.insertMeal(meal.toMealEntity()) }
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
    val weeklySummaries =
      remember(allMeals, userProfile.targetDailyCalories, userProfile.targetDailyProteinGrams) {
        val grouped = allMeals.groupBy { it.dateIso }
        grouped
          .map { (date, meals) ->
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
          }
          .sortedBy { it.dateIso }
      }

    Scaffold(
      snackbarHost = { SnackbarHost(snackbarHostState) },
      bottomBar = {
        NavigationBar(containerColor = SurfaceDark, contentColor = PrimaryBlue) {
          NavigationBarItem(
            selected = selectedScreenIndex == 0,
            onClick = { selectedScreenIndex = 0 },
            icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
            label = { Text("Today") },
            colors =
              NavigationBarItemDefaults.colors(
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
            icon = { Icon(Icons.Default.Chat, contentDescription = "Dietitian") },
            label = { Text("Dietitian") },
            colors =
              NavigationBarItemDefaults.colors(
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
            icon = { Icon(Icons.Default.BarChart, contentDescription = "Analytics") },
            label = { Text("Analytics") },
            colors =
              NavigationBarItemDefaults.colors(
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
            icon = { Icon(Icons.Default.History, contentDescription = "History") },
            label = { Text("Logs") },
            colors =
              NavigationBarItemDefaults.colors(
                selectedIconColor = Color.White,
                selectedTextColor = Color.White,
                indicatorColor = PrimaryBlue,
                unselectedIconColor = TextSecondary,
                unselectedTextColor = TextSecondary
              )
          )
          NavigationBarItem(
            selected = selectedScreenIndex == 4,
            onClick = { selectedScreenIndex = 4 },
            icon = { Icon(Icons.Default.Person, contentDescription = "Profile") },
            label = { Text("Profile") },
            colors =
              NavigationBarItemDefaults.colors(
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
      Box(modifier = Modifier.fillMaxSize().padding(innerPadding).background(BackgroundDark)) {
        when (selectedScreenIndex) {
          0 ->
            HomeScreen(
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
                  val apiKey =
                    when (userProfile.primaryAiProvider) {
                      "OpenAI" -> userProfile.openAiApiKey
                      "Claude" -> userProfile.claudeApiKey
                      "DeepSeek" -> userProfile.deepSeekApiKey
                      else -> userProfile.geminiApiKey
                    }.trim()

                  val modelName =
                    when (userProfile.primaryAiProvider) {
                      "OpenAI" -> userProfile.openAiModel
                      "Claude" -> userProfile.claudeModel
                      "DeepSeek" -> userProfile.deepSeekModel
                      else -> userProfile.geminiModel
                    }.trim()

                  val yesterdayIso =
                    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                      .format(
                        java.util.Calendar.getInstance()
                          .apply { add(java.util.Calendar.DAY_OF_YEAR, -1) }
                          .time
                      )
                  val yesterdayMeals = allMeals.filter { it.dateIso == yesterdayIso }
                  val yesterdaySummaryText =
                    if (yesterdayMeals.isNotEmpty()) {
                      "Total: ${yesterdayMeals.sumOf { it.calories }} kcal, ${yesterdayMeals.sumOf { it.proteinGrams.toDouble() }}g Protein, ${yesterdayMeals.sumOf { it.carbsGrams.toDouble() }}g Carbs, ${yesterdayMeals.sumOf { it.fatGrams.toDouble() }}g Fat. Foods logged: " +
                        yesterdayMeals.joinToString("; ") {
                          "${it.foodName} (${it.calories} kcal, ${it.proteinGrams}g Protein)"
                        }
                    } else {
                      "No meals logged for yesterday."
                    }

                  val todaySummaryText =
                    if (todayMeals.isNotEmpty()) {
                      "Current Total: $todayTotalCalories kcal, ${todayTotalProtein}g Protein, ${todayTotalCarbs}g Carbs, ${todayTotalFat}g Fat. Foods logged: " +
                        todayMeals.joinToString("; ") {
                          "${it.foodName} (${it.calories} kcal, ${it.proteinGrams}g Protein)"
                        }
                    } else {
                      "No meals logged yet today."
                    }

                  val result =
                    aiRepository.parseMealText(
                      inputText = input,
                      provider = userProfile.primaryAiProvider,
                      apiKey = apiKey,
                      modelName = modelName,
                      todayContext = todaySummaryText,
                      yesterdayContext = yesterdaySummaryText,
                      profile = userProfile,
                      memoryContext =
                        MemoryRepository.formatForPrompt(
                          withContext(Dispatchers.IO) { memoryRepository.getAll() },
                          emptyText = ""
                        )
                    )

                  isLoadingAi = false
                  result
                    .onSuccess { parseResult ->
                      val entry = parseResult.mealEntry
                      val isUpdate = parseResult.action.equals("UPDATE", ignoreCase = true)
                      withContext(Dispatchers.IO) {
                        val existing =
                          if (isUpdate) {
                            allMealEntities.find {
                              it.dateIso == entry.dateIso &&
                                it.foodName.equals(entry.foodName, ignoreCase = true)
                            }
                          } else {
                            null
                          }
                        val entityToSave =
                          if (existing != null) {
                            entry.copy(id = existing.id).toMealEntity()
                          } else {
                            entry.toMealEntity()
                          }
                        mealDao.insertMeal(entityToSave)
                      }
                      val actionLabel = if (isUpdate) "Updated" else "Saved"
                      Toast.makeText(
                          this@MainActivity,
                          "$actionLabel (${entry.dateIso}): ${entry.foodName}",
                          Toast.LENGTH_SHORT
                        )
                        .show()
                      coachAdvice = parseResult.coachAdvice
                    }
                    .onFailure { error ->
                      Toast.makeText(
                          this@MainActivity,
                          "AI Parsing Notice: ${error.localizedMessage}",
                          Toast.LENGTH_LONG
                        )
                        .show()
                    }
                }
              },
              onToggleCreatine = { creatineTaken = !creatineTaken },
              onDeleteMeal = { meal -> deleteMealWithUndo(meal) }
            )
          1 ->
            if (showMemory) {
              MemoryScreen(
                memories = memories,
                onAdd = { category, content ->
                  scope.launch(Dispatchers.IO) { memoryRepository.add(category, content) }
                },
                onEdit = { id, category, content ->
                  scope.launch(Dispatchers.IO) { memoryRepository.edit(id, category, content) }
                },
                onDelete = { id -> scope.launch(Dispatchers.IO) { memoryRepository.delete(id) } },
                onBack = { showMemory = false }
              )
            } else {
              DietitianChatScreen(
                messages = chatMessages,
                isLoading = chatLoading,
                pendingImage = chatPendingImage,
                userName = userProfile.displayName,
                setupHint =
                  when {
                    activeApiKey(userProfile).isBlank() ->
                      "Add your ${userProfile.primaryAiProvider} API key in Profile to start chatting. Tap here."
                    !userProfile.profileConfigured ->
                      "Set up your profile for accurate calorie and protein targets. Tap here."
                    else -> null
                  },
                onSetupClick = { selectedScreenIndex = 4 },
                onSend = chatViewModel::send,
                onTakePhoto = ::launchCamera,
                onPickFromGallery = {
                  photoPicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                  )
                },
                onClearImage = chatViewModel::clearImage,
                onSaveMeal = chatViewModel::saveMeal,
                onOpenMemory = { showMemory = true },
                onNewChat = chatViewModel::newChat
              )
            }
          2 ->
            AnalyticsScreen(
              weeklySummaries = weeklySummaries,
              allMeals = allMeals,
              targetCalories = userProfile.targetDailyCalories,
              maintenanceCalories = userProfile.tdeeMaintenanceCalories
            )
          3 ->
            LogHistoryScreen(
              allMeals = allMeals,
              targetCalories = userProfile.targetDailyCalories,
              targetProtein = userProfile.targetDailyProteinGrams,
              onDeleteMeal = { id ->
                val target = allMeals.find { it.id == id }
                if (target != null) {
                  deleteMealWithUndo(target)
                }
              }
            )
          4 ->
            ProfileScreen(
              userProfile = userProfile,
              weightHistory = weightHistory,
              onSaveProfile = { updated ->
                userProfile = updated
                preferencesRepository.saveUserProfile(updated)
                if (updated.profileConfigured) {
                  scope.launch(Dispatchers.IO) { weightRepository.logIfChanged(updated.weightKg) }
                }
                Toast.makeText(this@MainActivity, "Profile saved", Toast.LENGTH_SHORT).show()
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
