package com.calorietracker.app.presentation.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calorietracker.app.data.exporter.DataExporter
import com.calorietracker.app.data.local.WeightEntity
import com.calorietracker.app.data.model.UserProfile
import com.calorietracker.app.data.repository.WeightRepository
import com.calorietracker.app.domain.repository.IMealRepository
import com.calorietracker.app.domain.repository.IUserProfileRepository
import com.calorietracker.app.presentation.UiEffect
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** State holder for the Profile screen: settings, weight history and data export. */
@HiltViewModel
class ProfileViewModel
@Inject
constructor(
  private val profileRepository: IUserProfileRepository,
  private val mealRepository: IMealRepository,
  private val exporter: DataExporter,
  private val weights: WeightRepository
) : ViewModel() {
  private val effectChannel = Channel<UiEffect>(Channel.BUFFERED)

  val effects: Flow<UiEffect> = effectChannel.receiveAsFlow()

  val profile: StateFlow<UserProfile> = profileRepository.profile

  val weightHistory: StateFlow<List<WeightEntity>> =
    weights
      .observeAll()
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

  fun save(updated: UserProfile) {
    viewModelScope.launch {
      profileRepository.update(updated)
      if (updated.profileConfigured) weights.logIfChanged(updated.weightKg)
      effectChannel.send(UiEffect.Message("Profile saved"))
    }
  }

  fun exportJson() = export("JSON") { meals -> exporter.exportToJson(meals) }

  fun exportCsv() = export("CSV") { meals -> exporter.exportToCsv(meals) }

  private fun export(
    label: String,
    write: (List<com.calorietracker.app.domain.model.Meal>) -> File?
  ) {
    viewModelScope.launch {
      val file = withContext(Dispatchers.IO) { write(mealRepository.getAllMeals().first()) }
      val text = if (file != null) "Exported $label to ${file.name}" else "Export failed"
      effectChannel.send(UiEffect.Message(text, long = true))
    }
  }

  private companion object {
    const val STOP_TIMEOUT_MS = 5_000L
  }
}
