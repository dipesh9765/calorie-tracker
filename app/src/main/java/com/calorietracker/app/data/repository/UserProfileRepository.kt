package com.calorietracker.app.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.calorietracker.app.data.model.UserProfile
import com.calorietracker.app.domain.repository.IUserProfileRepository
import com.google.gson.Gson
import com.google.gson.JsonParseException
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** Persists the [UserProfile] locally and publishes every change as a [StateFlow]. */
@Singleton
class UserProfileRepository
@Inject
constructor(@ApplicationContext context: Context, private val gson: Gson) : IUserProfileRepository {
  private val prefs: SharedPreferences =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  private val state = MutableStateFlow(read())
  override val profile: StateFlow<UserProfile> = state.asStateFlow()

  override suspend fun update(profile: UserProfile) {
    withContext(Dispatchers.IO) { prefs.edit { putString(KEY_PROFILE, gson.toJson(profile)) } }
    state.value = profile
  }

  private fun read(): UserProfile {
    val json = prefs.getString(KEY_PROFILE, null) ?: return UserProfile()
    return try {
      gson.fromJson(json, UserProfile::class.java) ?: UserProfile()
    } catch (_: JsonParseException) {
      UserProfile()
    }
  }

  private companion object {
    const val PREFS_NAME = "user_profile_prefs"
    const val KEY_PROFILE = "key_user_profile"
  }
}
