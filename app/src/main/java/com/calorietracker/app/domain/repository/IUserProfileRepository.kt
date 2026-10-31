package com.calorietracker.app.domain.repository

import com.calorietracker.app.data.model.UserProfile
import kotlinx.coroutines.flow.StateFlow

/** Source of truth for the user's profile, targets and AI provider settings. */
interface IUserProfileRepository {
  /** Always holds the latest saved profile; collectors see changes made anywhere in the app. */
  val profile: StateFlow<UserProfile>

  suspend fun update(profile: UserProfile)
}
