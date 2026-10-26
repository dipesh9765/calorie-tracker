package com.calorietracker.app.data.repository

import android.content.SharedPreferences
import androidx.core.content.edit
import com.calorietracker.app.data.model.UserProfile
import com.calorietracker.app.data.security.SecretCipher
import com.calorietracker.app.domain.repository.IUserProfileRepository
import com.google.gson.Gson
import com.google.gson.JsonParseException
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Persists the [UserProfile] locally and publishes every change as a [StateFlow].
 *
 * The four AI provider API keys are never written in plain text: the profile JSON is stored with
 * the keys blanked out, and each key is encrypted separately through [SecretCipher]. A profile
 * saved by an older version (keys inside the JSON) is migrated the first time it is read.
 */
@Singleton
class UserProfileRepository
@Inject
constructor(
  @Named(PROFILE_PREFS) private val prefs: SharedPreferences,
  private val gson: Gson,
  private val cipher: SecretCipher
) : IUserProfileRepository {

  private val state = MutableStateFlow(load())
  override val profile: StateFlow<UserProfile> = state.asStateFlow()

  override suspend fun update(profile: UserProfile) {
    withContext(Dispatchers.IO) { persist(profile) }
    state.value = profile
  }

  private fun load(): UserProfile {
    val stored = parseStored() ?: return UserProfile()
    val hasLegacyKeys = SECRETS.any { it.get(stored).isNotBlank() }
    if (hasLegacyKeys) {
      // Pre-encryption data: move the keys into encrypted storage and drop them from the JSON.
      persist(stored)
      return stored
    }
    return SECRETS.fold(stored) { profile, secret -> secret.set(profile, readSecret(secret.id)) }
  }

  private fun parseStored(): UserProfile? {
    val json = prefs.getString(KEY_PROFILE, null) ?: return null
    return try {
      gson.fromJson(json, UserProfile::class.java)
    } catch (_: JsonParseException) {
      null
    }
  }

  private fun persist(profile: UserProfile) {
    val blanked = SECRETS.fold(profile) { p, secret -> secret.set(p, "") }
    prefs.edit {
      putString(KEY_PROFILE, gson.toJson(blanked))
      SECRETS.forEach { secret ->
        val value = secret.get(profile)
        if (value.isBlank()) remove(secret.prefKey)
        else putString(secret.prefKey, cipher.encrypt(value))
      }
    }
  }

  private fun readSecret(id: String): String {
    val token = prefs.getString(SECRET_PREFIX + id, null) ?: return ""
    return cipher.decrypt(token).orEmpty()
  }

  /** One encrypted field of [UserProfile]. */
  private class Secret(
    val id: String,
    val get: (UserProfile) -> String,
    val set: (UserProfile, String) -> UserProfile
  ) {
    val prefKey = SECRET_PREFIX + id
  }

  companion object {
    const val PROFILE_PREFS = "profile_prefs"
    const val PREFS_NAME = "user_profile_prefs"
    private const val KEY_PROFILE = "key_user_profile"
    private const val SECRET_PREFIX = "secret_"

    private val SECRETS =
      listOf(
        Secret("gemini", { it.geminiApiKey }, { p, v -> p.copy(geminiApiKey = v) }),
        Secret("openai", { it.openAiApiKey }, { p, v -> p.copy(openAiApiKey = v) }),
        Secret("claude", { it.claudeApiKey }, { p, v -> p.copy(claudeApiKey = v) }),
        Secret("deepseek", { it.deepSeekApiKey }, { p, v -> p.copy(deepSeekApiKey = v) })
      )
  }
}
