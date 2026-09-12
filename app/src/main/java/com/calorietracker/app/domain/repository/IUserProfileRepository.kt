package com.calorietracker.app.domain.repository

import com.calorietracker.app.data.model.UserProfile
import kotlinx.coroutines.flow.Flow

interface IUserProfileRepository {
  fun getUserProfile(): Flow<UserProfile>

  suspend fun updateUserProfile(profile: UserProfile)

  suspend fun getApiKeyForProvider(providerName: String): String
}
