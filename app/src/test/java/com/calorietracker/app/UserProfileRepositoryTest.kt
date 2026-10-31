package com.calorietracker.app

import com.calorietracker.app.data.model.UserProfile
import com.calorietracker.app.data.repository.UserProfileRepository
import com.calorietracker.app.data.security.AesGcmCipher
import com.calorietracker.app.data.security.SecretCipher
import com.calorietracker.app.data.security.SecureStorageException
import com.calorietracker.app.support.InMemorySharedPreferences
import com.google.gson.Gson
import java.util.Base64
import javax.crypto.KeyGenerator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserProfileRepositoryTest {
  private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
  private val cipher: SecretCipher =
    AesGcmCipher(
      keyProvider = { key },
      encode = { Base64.getEncoder().encodeToString(it) },
      decode = { Base64.getDecoder().decode(it) }
    )
  private val gson = Gson()

  private fun repository(prefs: InMemorySharedPreferences) =
    UserProfileRepository(prefs, gson, cipher)

  private val profileWithKeys =
    UserProfile(
      name = "Asha",
      geminiApiKey = "gem-secret-111",
      openAiApiKey = "oai-secret-222",
      claudeApiKey = "",
      deepSeekApiKey = "ds-secret-333"
    )

  @Test
  fun `a fresh install starts with the default profile`() {
    assertEquals(UserProfile(), repository(InMemorySharedPreferences()).profile.value)
  }

  @Test
  fun `keys are not stored in plain text`() = runBlocking {
    val prefs = InMemorySharedPreferences()
    repository(prefs).update(profileWithKeys)

    val everythingStored = prefs.data.values.joinToString("|")
    listOf("gem-secret-111", "oai-secret-222", "ds-secret-333").forEach {
      assertFalse("$it leaked into preferences", everythingStored.contains(it))
    }
    assertTrue(prefs.data.containsKey("secret_gemini"))
    assertFalse(prefs.data.containsKey("secret_claude")) // blank keys are not stored at all
  }

  @Test
  fun `the profile and its keys survive a restart`() = runBlocking {
    val prefs = InMemorySharedPreferences()
    repository(prefs).update(profileWithKeys)

    val reloaded = repository(prefs).profile.value

    assertEquals(profileWithKeys, reloaded)
  }

  @Test
  fun `clearing a key removes it from storage`() = runBlocking {
    val prefs = InMemorySharedPreferences()
    val repo = repository(prefs)
    repo.update(profileWithKeys)

    repo.update(profileWithKeys.copy(geminiApiKey = ""))

    assertFalse(prefs.data.containsKey("secret_gemini"))
    assertEquals("", repository(prefs).profile.value.geminiApiKey)
  }

  @Test
  fun `update publishes the new profile to collectors immediately`() = runBlocking {
    val repo = repository(InMemorySharedPreferences())
    repo.update(profileWithKeys)
    assertEquals("Asha", repo.profile.value.name)
  }

  @Test
  fun `a plain text profile from an older version is migrated to encrypted storage`() {
    val prefs = InMemorySharedPreferences()
    prefs.edit().putString("key_user_profile", gson.toJson(profileWithKeys)).apply()

    val loaded = repository(prefs).profile.value

    assertEquals("gem-secret-111", loaded.geminiApiKey)
    val stored = prefs.getString("key_user_profile", "")!!
    assertFalse(stored.contains("gem-secret-111"))
    assertTrue(prefs.data.containsKey("secret_gemini"))
    // and it still loads correctly the next time
    assertEquals(profileWithKeys, repository(prefs).profile.value)
  }

  @Test
  fun `a key that cannot be decrypted becomes blank instead of crashing`() = runBlocking {
    val prefs = InMemorySharedPreferences()
    repository(prefs).update(profileWithKeys)
    prefs.data["secret_gemini"] = "corrupted"

    val reloaded = repository(prefs).profile.value

    assertEquals("", reloaded.geminiApiKey)
    assertEquals("oai-secret-222", reloaded.openAiApiKey)
  }

  @Test
  fun `corrupt profile json falls back to defaults`() {
    val prefs = InMemorySharedPreferences()
    prefs.edit().putString("key_user_profile", "{ not json").apply()
    assertEquals(UserProfile(), repository(prefs).profile.value)
  }

  @Test
  fun `a failing keystore surfaces as SecureStorageException and stores nothing`() = runBlocking {
    val prefs = InMemorySharedPreferences()
    val brokenCipher =
      object : SecretCipher {
        override fun encrypt(plainText: String): String =
          throw SecureStorageException("no keystore")

        override fun decrypt(token: String): String? = null
      }
    val repo = UserProfileRepository(prefs, gson, brokenCipher)

    val failure = runCatching { repo.update(profileWithKeys) }.exceptionOrNull()

    assertTrue(failure is SecureStorageException)
    assertFalse(prefs.data.values.joinToString("|").contains("gem-secret-111"))
  }

  @Test
  fun `toString never prints api keys`() {
    val text = profileWithKeys.toString()
    assertFalse(text.contains("secret"))
    assertTrue(text.contains("3 set"))
  }
}
