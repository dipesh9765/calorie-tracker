package com.calorietracker.app.data.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Raised when a secret cannot be encrypted, e.g. the Keystore is unavailable on this device. */
class SecureStorageException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Encrypts short secrets (API keys) for storage at rest. */
interface SecretCipher {
  /** Returns an opaque, self-contained string; throws [SecureStorageException] if impossible. */
  fun encrypt(plainText: String): String

  /** Returns null when [token] is corrupt, was tampered with, or was made with another key. */
  fun decrypt(token: String): String?
}

/**
 * AES-256-GCM with a fresh random 96-bit IV per message. The token is `base64(iv ||
 * ciphertext+tag)`. GCM authenticates the data, so any modification makes [decrypt] return null.
 *
 * @param keyProvider supplies the key; the app uses an Android Keystore key that never leaves
 *   secure hardware, tests pass an in-memory key.
 */
class AesGcmCipher(
  private val keyProvider: () -> SecretKey,
  private val encode: (ByteArray) -> String = { Base64.encodeToString(it, Base64.NO_WRAP) },
  private val decode: (String) -> ByteArray = { Base64.decode(it, Base64.NO_WRAP) }
) : SecretCipher {
  private val random = SecureRandom()

  override fun encrypt(plainText: String): String =
    try {
      val iv = ByteArray(IV_BYTES).also(random::nextBytes)
      val cipher = Cipher.getInstance(TRANSFORMATION)
      cipher.init(Cipher.ENCRYPT_MODE, keyProvider(), GCMParameterSpec(TAG_BITS, iv))
      val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
      encode(iv + encrypted)
    } catch (e: GeneralSecurityException) {
      throw SecureStorageException("Could not encrypt the secret with the device key.", e)
    }

  override fun decrypt(token: String): String? =
    try {
      val bytes = decode(token)
      if (bytes.size <= IV_BYTES) {
        null
      } else {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
          Cipher.DECRYPT_MODE,
          keyProvider(),
          GCMParameterSpec(TAG_BITS, bytes.copyOfRange(0, IV_BYTES))
        )
        String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES), Charsets.UTF_8)
      }
    } catch (_: GeneralSecurityException) {
      null
    } catch (_: IllegalArgumentException) {
      null // not valid base64
    }

  private companion object {
    const val TRANSFORMATION = "AES/GCM/NoPadding"
    const val IV_BYTES = 12
    const val TAG_BITS = 128
  }
}

/** Creates (once) and returns the app's AES key inside the Android Keystore. */
object KeystoreKeyProvider {
  private const val ANDROID_KEYSTORE = "AndroidKeyStore"
  private const val KEY_ALIAS = "calorietracker_secrets_v1"
  private const val KEY_SIZE_BITS = 256

  fun secretKey(): SecretKey {
    val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let {
      return it
    }
    val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
    generator.init(
      KeyGenParameterSpec.Builder(
          KEY_ALIAS,
          KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setKeySize(KEY_SIZE_BITS)
        .build()
    )
    return generator.generateKey()
  }
}
