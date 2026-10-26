package com.calorietracker.app.presentation.memory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calorietracker.app.data.local.MemoryEntity
import com.calorietracker.app.data.repository.MemoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** State holder for the screen where the user reviews what the dietitian remembers. */
@HiltViewModel
class MemoryViewModel @Inject constructor(private val repository: MemoryRepository) : ViewModel() {
  val memories: StateFlow<List<MemoryEntity>> =
    repository
      .observeAll()
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

  fun add(category: String, content: String) {
    viewModelScope.launch { repository.add(category, content) }
  }

  fun edit(id: Long, category: String, content: String) {
    viewModelScope.launch { repository.edit(id, category, content) }
  }

  fun delete(id: Long) {
    viewModelScope.launch { repository.delete(id) }
  }

  private companion object {
    const val STOP_TIMEOUT_MS = 5_000L
  }
}
