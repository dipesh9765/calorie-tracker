package com.calorietracker.app.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.calorietracker.app.data.model.UserProfile
import com.google.gson.Gson

/**
 * Enterprise Preferences Repository providing persistent local storage for User Profile, API Keys,
 * custom AI model configurations, and target macro goals using SharedPreferences.
 */
class PreferencesRepository(context: Context, private val gson: Gson = Gson()) {
  private val prefs: SharedPreferences =
    context.getSharedPreferences("user_profile_prefs", Context.MODE_PRIVATE)

  companion object {
    private const val KEY_PROFILE = "key_user_profile"
  }

  fun getUserProfile(): UserProfile {
    val json = prefs.getString(KEY_PROFILE, null) ?: return UserProfile()
    return try {
      gson.fromJson(json, UserProfile::class.java) ?: UserProfile()
    } catch (_: Exception) {
      UserProfile()
    }
  }

  fun saveUserProfile(profile: UserProfile) {
    val json = gson.toJson(profile)
    prefs.edit().putString(KEY_PROFILE, json).apply()
  }
}
