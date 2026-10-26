package com.calorietracker.app

import com.calorietracker.app.data.security.AesGcmCipher
import com.calorietracker.app.data.security.SecureStorageException
import java.util.Base64
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AesGcmCipherTest {
  private fun newKey(): SecretKey =
    KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

  private fun cipher(key: SecretKey = newKey()) =
    AesGcmCipher(
      keyProvider = { key },
      encode = { Base64.getEncoder().encodeToString(it) },
      decode = { Base64.getDecoder().decode(it) }
    )

  @Test
  fun `a secret survives an encrypt and decrypt round trip`() {
    val cipher = cipher()
    val token = cipher.encrypt("sk-live-1234567890")
    assertEquals("sk-live-1234567890", cipher.decrypt(token))
  }

  @Test
  fun `the token does not contain the plain text`() {
    val token = cipher().encrypt("sk-live-1234567890")
    assertTrue(!token.contains("sk-live"))
  }

  @Test
  fun `encrypting the same value twice gives different tokens`() {
    val cipher = cipher()
    assertNotEquals(cipher.encrypt("same"), cipher.encrypt("same"))
  }

  @Test
  fun `unicode and empty strings round trip`() {
    val cipher = cipher()
    assertEquals("कुंजी-🔑", cipher.decrypt(cipher.encrypt("कुंजी-🔑")))
    assertEquals("", cipher.decrypt(cipher.encrypt("")))
  }

  @Test
  fun `a tampered token is rejected`() {
    val cipher = cipher()
    val bytes = Base64.getDecoder().decode(cipher.encrypt("secret"))
    bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
    assertNull(cipher.decrypt(Base64.getEncoder().encodeToString(bytes)))
  }

  @Test
  fun `a token made with another key cannot be read`() {
    val token = cipher().encrypt("secret")
    assertNull(cipher().decrypt(token))
  }

  @Test
  fun `a key that cannot be used raises SecureStorageException`() {
    val failing =
      AesGcmCipher(
        keyProvider = { throw java.security.InvalidKeyException("keystore unavailable") },
        encode = { Base64.getEncoder().encodeToString(it) },
        decode = { Base64.getDecoder().decode(it) }
      )
    val error = runCatching { failing.encrypt("x") }.exceptionOrNull()
    assertTrue(error is SecureStorageException)
  }

  @Test
  fun `garbage and truncated input return null instead of throwing`() {
    val cipher = cipher()
    assertNull(cipher.decrypt("not base64 !!!"))
    assertNull(cipher.decrypt(""))
    assertNull(cipher.decrypt(Base64.getEncoder().encodeToString(ByteArray(5))))
  }
}
