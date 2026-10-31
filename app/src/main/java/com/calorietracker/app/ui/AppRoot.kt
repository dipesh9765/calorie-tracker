package com.calorietracker.app.ui

import android.Manifest
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calorietracker.app.presentation.DietitianChatViewModel
import com.calorietracker.app.presentation.UiEffect
import com.calorietracker.app.presentation.history.HistoryViewModel
import com.calorietracker.app.presentation.home.HomeUiEvent
import com.calorietracker.app.presentation.home.HomeViewModel
import com.calorietracker.app.presentation.memory.MemoryViewModel
import com.calorietracker.app.presentation.profile.ProfileViewModel
import com.calorietracker.app.ui.screens.AnalyticsScreen
import com.calorietracker.app.ui.screens.DietitianChatScreen
import com.calorietracker.app.ui.screens.HomeScreen
import com.calorietracker.app.ui.screens.LogHistoryScreen
import com.calorietracker.app.ui.screens.MemoryScreen
import com.calorietracker.app.ui.screens.ProfileScreen
import com.calorietracker.app.ui.theme.BackgroundDark
import com.calorietracker.app.ui.theme.PrimaryBlue
import com.calorietracker.app.ui.theme.SurfaceDark
import com.calorietracker.app.ui.theme.TextSecondary
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.merge

private enum class Tab(val label: String, val icon: ImageVector) {
  Today("Today", Icons.Default.Home),
  Dietitian("Dietitian", Icons.Default.Chat),
  Analytics("Analytics", Icons.Default.BarChart),
  Logs("Logs", Icons.Default.History),
  Profile("Profile", Icons.Default.Person)
}

/** Top-level screen: bottom navigation, snackbar host and the one-shot effect plumbing. */
@Composable
fun AppRoot() {
  val context = LocalContext.current
  var selectedTab by rememberSaveable { mutableIntStateOf(Tab.Today.ordinal) }
  var showMemory by rememberSaveable { mutableStateOf(false) }
  val snackbarHostState = remember { SnackbarHostState() }

  val homeViewModel: HomeViewModel = hiltViewModel()
  val historyViewModel: HistoryViewModel = hiltViewModel()
  val profileViewModel: ProfileViewModel = hiltViewModel()
  val chatViewModel: DietitianChatViewModel = hiltViewModel()

  RequestNotificationPermission()
  CollectEffects(
    effects =
      remember { merge(homeViewModel.effects, historyViewModel.effects, profileViewModel.effects) },
    snackbarHostState = snackbarHostState,
    onUndo = { meal ->
      // Both screens write to the same table, so either ViewModel can restore the meal.
      homeViewModel.onEvent(HomeUiEvent.UndoDelete(meal))
    },
    onMessage = { text, long ->
      Toast.makeText(context, text, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
    }
  )

  Scaffold(
    snackbarHost = { SnackbarHost(snackbarHostState) },
    bottomBar = { BottomBar(selected = selectedTab, onSelect = { selectedTab = it }) }
  ) { innerPadding ->
    Box(modifier = Modifier.fillMaxSize().padding(innerPadding).background(BackgroundDark)) {
      when (Tab.entries[selectedTab]) {
        Tab.Today -> {
          val state by homeViewModel.uiState.collectAsStateWithLifecycle()
          HomeScreen(state = state, onEvent = homeViewModel::onEvent)
        }
        Tab.Dietitian ->
          if (showMemory) {
            val memoryViewModel: MemoryViewModel = hiltViewModel()
            val memories by memoryViewModel.memories.collectAsStateWithLifecycle()
            MemoryScreen(
              memories = memories,
              onAdd = memoryViewModel::add,
              onEdit = memoryViewModel::edit,
              onDelete = memoryViewModel::delete,
              onBack = { showMemory = false }
            )
          } else {
            ChatTab(
              chatViewModel = chatViewModel,
              profileViewModel = profileViewModel,
              onOpenProfile = { selectedTab = Tab.Profile.ordinal },
              onOpenMemory = { showMemory = true }
            )
          }
        Tab.Analytics -> {
          val state by historyViewModel.uiState.collectAsStateWithLifecycle()
          AnalyticsScreen(
            weeklySummaries = state.summaries,
            allMeals = state.allMeals,
            targetCalories = state.profile.targetDailyCalories,
            maintenanceCalories = state.profile.tdeeMaintenanceCalories
          )
        }
        Tab.Logs -> {
          val state by historyViewModel.uiState.collectAsStateWithLifecycle()
          LogHistoryScreen(
            allMeals = state.allMeals,
            summaries = state.summaries,
            onDeleteMeal = { id ->
              state.allMeals.find { it.id == id }?.let(historyViewModel::deleteMeal)
            }
          )
        }
        Tab.Profile -> {
          val profile by profileViewModel.profile.collectAsStateWithLifecycle()
          val weights by profileViewModel.weightHistory.collectAsStateWithLifecycle()
          ProfileScreen(
            userProfile = profile,
            weightHistory = weights,
            onSaveProfile = profileViewModel::save,
            onExportJson = profileViewModel::exportJson,
            onExportCsv = profileViewModel::exportCsv
          )
        }
      }
    }
  }
}

@Composable
private fun BottomBar(selected: Int, onSelect: (Int) -> Unit) {
  NavigationBar(containerColor = SurfaceDark, contentColor = PrimaryBlue) {
    Tab.entries.forEach { tab ->
      NavigationBarItem(
        selected = selected == tab.ordinal,
        onClick = { onSelect(tab.ordinal) },
        icon = { Icon(tab.icon, contentDescription = tab.label) },
        label = { Text(tab.label) },
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
}

@Composable
private fun ChatTab(
  chatViewModel: DietitianChatViewModel,
  profileViewModel: ProfileViewModel,
  onOpenProfile: () -> Unit,
  onOpenMemory: () -> Unit
) {
  val context = LocalContext.current
  val profile by profileViewModel.profile.collectAsStateWithLifecycle()
  val messages by chatViewModel.messages.collectAsStateWithLifecycle()
  val loading by chatViewModel.isLoading.collectAsStateWithLifecycle()
  val pendingImage by chatViewModel.pendingImage.collectAsStateWithLifecycle()

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

  DietitianChatScreen(
    messages = messages,
    isLoading = loading,
    pendingImage = pendingImage,
    userName = profile.displayName,
    setupHint =
      when {
        profile.activeApiKey.isBlank() ->
          "Add your ${profile.primaryAiProvider} API key in Profile to start chatting. Tap here."
        !profile.profileConfigured ->
          "Set up your profile for accurate calorie and protein targets. Tap here."
        else -> null
      },
    onSetupClick = onOpenProfile,
    onSend = chatViewModel::send,
    onTakePhoto = {
      val dir = File(context.cacheDir, "camera").apply { mkdirs() }
      dir.listFiles()?.forEach { it.delete() } // drop photos from earlier sessions
      val file = File.createTempFile("meal_", ".jpg", dir)
      val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
      cameraUri = uri
      takePicture.launch(uri)
    },
    onPickFromGallery = {
      photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    },
    onClearImage = chatViewModel::clearImage,
    onSaveMeal = chatViewModel::saveMeal,
    onOpenMemory = onOpenMemory,
    onNewChat = chatViewModel::newChat
  )
}

/** Android 13+ needs a runtime grant before the coach can post notifications. */
@Composable
private fun RequestNotificationPermission() {
  val context = LocalContext.current
  val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
  LaunchedEffect(Unit) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      val granted =
        androidx.core.content.ContextCompat.checkSelfPermission(
          context,
          Manifest.permission.POST_NOTIFICATIONS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
      if (!granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
  }
}

@Composable
private fun CollectEffects(
  effects: Flow<UiEffect>,
  snackbarHostState: SnackbarHostState,
  onUndo: (com.calorietracker.app.domain.model.Meal) -> Unit,
  onMessage: (text: String, long: Boolean) -> Unit
) {
  LaunchedEffect(effects) {
    effects.collect { effect ->
      when (effect) {
        is UiEffect.Message -> onMessage(effect.text, effect.long)
        is UiEffect.MealDeleted -> {
          val result =
            snackbarHostState.showSnackbar(
              message = "Deleted: ${effect.meal.foodName}",
              actionLabel = "UNDO",
              duration = SnackbarDuration.Short
            )
          if (result == SnackbarResult.ActionPerformed) onUndo(effect.meal)
        }
      }
    }
  }
}
