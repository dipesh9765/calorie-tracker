package com.calorietracker.app

import com.calorietracker.app.data.remote.adapter.ClaudeAdapter
import com.calorietracker.app.data.remote.adapter.DeepSeekAdapter
import com.calorietracker.app.data.remote.adapter.GeminiAdapter
import com.calorietracker.app.data.remote.adapter.IAiProviderAdapter
import com.calorietracker.app.data.remote.adapter.OpenAiAdapter
import com.calorietracker.app.domain.model.ChatRole
import com.calorietracker.app.domain.model.ChatTurn
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Talks to a local [MockWebServer] to pin down what each provider adapter actually puts on the wire
 * and how it reads responses: path, auth header, model, error unwrapping and malformed bodies.
 */
class AdapterContractTest {
  private lateinit var server: MockWebServer
  private val gson = Gson()

  @Before
  fun start() {
    server = MockWebServer().apply { start() }
  }

  @After
  fun stop() {
    server.shutdown()
  }

  /** Everything that differs between providers. */
  private class Provider(
    val name: String,
    val create: (String) -> IAiProviderAdapter,
    val path: (model: String) -> String,
    val authHeader: String,
    val authValue: (key: String) -> String,
    val defaultModel: String,
    val success: (text: String) -> String,
    val malformedBody: String,
    val malformedMessage: String
  )

  private val providers =
    listOf(
      Provider(
        name = "OpenAI",
        create = { OpenAiAdapter(baseUrl = it) },
        path = { "/v1/chat/completions" },
        authHeader = "Authorization",
        authValue = { "Bearer $it" },
        defaultModel = "gpt-4o-mini",
        success = { """{"choices":[{"message":{"content":${gson.toJson(it)}}}]}""" },
        malformedBody = """{"unexpected":true}""",
        malformedMessage = "missing 'choices'"
      ),
      Provider(
        name = "DeepSeek",
        create = { DeepSeekAdapter(baseUrl = it) },
        path = { "/v1/chat/completions" },
        authHeader = "Authorization",
        authValue = { "Bearer $it" },
        defaultModel = "deepseek-chat",
        success = { """{"choices":[{"message":{"content":${gson.toJson(it)}}}]}""" },
        malformedBody = """{"unexpected":true}""",
        malformedMessage = "missing 'choices'"
      ),
      Provider(
        name = "Claude",
        create = { ClaudeAdapter(baseUrl = it) },
        path = { "/v1/messages" },
        authHeader = "x-api-key",
        authValue = { it },
        defaultModel = "claude-3-5-sonnet-20240620",
        success = { """{"content":[{"type":"text","text":${gson.toJson(it)}}]}""" },
        malformedBody = """{"unexpected":true}""",
        malformedMessage = "missing 'content'"
      ),
      Provider(
        name = "Gemini",
        create = { GeminiAdapter(baseUrl = it) },
        path = { "/v1beta/models/$it:generateContent" },
        authHeader = "x-goog-api-key",
        authValue = { it },
        defaultModel = "gemini-3.6-flash",
        success = { """{"candidates":[{"content":{"parts":[{"text":${gson.toJson(it)}}]}}]}""" },
        malformedBody = """{"unexpected":true}""",
        malformedMessage = "missing 'candidates'"
      )
    )

  private fun baseUrl() = server.url("/").toString().trimEnd('/')

  @Test
  fun `parseMeal sends the key in the provider header and returns the model text`() = runBlocking {
    providers.forEach { p ->
      server.enqueue(MockResponse().setBody(p.success("""{"foodName":"Poha"}""")))
      val adapter = p.create(baseUrl())

      val text = adapter.parseMeal("poha", "  key-123  ", "system prompt", "")

      assertEquals(p.name, """{"foodName":"Poha"}""", text)
      val request = server.takeRequest()
      assertEquals(p.name, "POST", request.method)
      assertEquals(p.name, p.path(p.defaultModel), request.path)
      assertEquals(p.name, p.authValue("key-123"), request.getHeader(p.authHeader))
      assertTrue(p.name, request.getHeader("Content-Type")!!.startsWith("application/json"))
    }
  }

  @Test
  fun `the API key never appears in the URL`() = runBlocking {
    providers.forEach { p ->
      server.enqueue(MockResponse().setBody(p.success("{}")))
      p.create(baseUrl()).parseMeal("poha", "super-secret-key", "sys", "")
      val url = server.takeRequest().requestUrl.toString()
      assertTrue("${p.name} leaked the key in $url", !url.contains("super-secret-key"))
    }
  }

  @Test
  fun `a custom model is used and a blank model falls back to the default`() = runBlocking {
    providers.forEach { p ->
      server.enqueue(MockResponse().setBody(p.success("{}")))
      p.create(baseUrl()).parseMeal("x", "k", "sys", "custom-model")
      val custom = server.takeRequest()
      val customModel =
        if (p.name == "Gemini") custom.path
        else gson.fromJson(custom.body.readUtf8(), JsonObject::class.java).get("model").asString
      assertTrue(p.name, customModel!!.contains("custom-model"))

      server.enqueue(MockResponse().setBody(p.success("{}")))
      p.create(baseUrl()).parseMeal("x", "k", "sys", "   ")
      val default = server.takeRequest()
      val defaultModel =
        if (p.name == "Gemini") default.path
        else gson.fromJson(default.body.readUtf8(), JsonObject::class.java).get("model").asString
      assertTrue(p.name, defaultModel!!.contains(p.defaultModel))
    }
  }

  @Test
  fun `the user text and system prompt are sent`() = runBlocking {
    providers.forEach { p ->
      server.enqueue(MockResponse().setBody(p.success("{}")))
      p.create(baseUrl()).parseMeal("two rotis and dal", "k", "SYSTEM-PROMPT-MARKER", "")
      val body = server.takeRequest().body.readUtf8()
      assertTrue(p.name, body.contains("two rotis and dal"))
      assertTrue(p.name, body.contains("SYSTEM-PROMPT-MARKER"))
    }
  }

  @Test
  fun `a blank key fails before any request is made`() = runBlocking {
    providers.forEach { p ->
      val error =
        runCatching { p.create(baseUrl()).parseMeal("x", "   ", "sys", "") }.exceptionOrNull()
      assertTrue(p.name, error is IllegalArgumentException)
      assertTrue(p.name, error!!.message!!.contains("blank"))
    }
    assertEquals(0, server.requestCount)
  }

  @Test
  fun `an HTTP error surfaces the provider's own message with the status code`() = runBlocking {
    providers.forEach { p ->
      server.enqueue(
        MockResponse().setResponseCode(401).setBody("""{"error":{"message":"Invalid API key"}}""")
      )
      val error =
        runCatching { p.create(baseUrl()).parseMeal("x", "bad", "sys", "") }.exceptionOrNull()
      assertEquals(p.name, "${p.name} API Error (401): Invalid API key", error?.message)
    }
  }

  @Test
  fun `a non JSON error body is passed through`() = runBlocking {
    providers.forEach { p ->
      server.enqueue(MockResponse().setResponseCode(502).setBody("Bad gateway"))
      val error =
        runCatching { p.create(baseUrl()).parseMeal("x", "k", "sys", "") }.exceptionOrNull()
      assertTrue(p.name, error!!.message!!.contains("(502)"))
      assertTrue(p.name, error.message!!.contains("Bad gateway"))
    }
  }

  @Test
  fun `an empty success body is reported`() = runBlocking {
    providers.forEach { p ->
      server.enqueue(MockResponse().setBody(""))
      val error =
        runCatching { p.create(baseUrl()).parseMeal("x", "k", "sys", "") }.exceptionOrNull()
      assertEquals(p.name, "Empty response received from ${p.name} API.", error?.message)
    }
  }

  @Test
  fun `a response with an unexpected structure is reported`() = runBlocking {
    providers.forEach { p ->
      server.enqueue(MockResponse().setBody(p.malformedBody))
      val error =
        runCatching { p.create(baseUrl()).parseMeal("x", "k", "sys", "") }.exceptionOrNull()
      assertTrue("${p.name}: ${error?.message}", error!!.message!!.contains(p.malformedMessage))
    }
  }

  @Test
  fun `a timeout is thrown as a SocketTimeoutException`() = runBlocking {
    val shortClient =
      OkHttpClient.Builder().readTimeout(200, java.util.concurrent.TimeUnit.MILLISECONDS).build()
    server.enqueue(
      MockResponse().setBody("{}").setBodyDelay(2, java.util.concurrent.TimeUnit.SECONDS)
    )
    val adapter = OpenAiAdapter(client = shortClient, baseUrl = baseUrl())

    val error = runCatching { adapter.parseMeal("x", "k", "sys", "") }.exceptionOrNull()

    assertTrue(error is java.net.SocketTimeoutException)
  }

  @Test
  fun `chat sends every turn and returns the model reply`() = runBlocking {
    providers.forEach { p ->
      server.enqueue(MockResponse().setBody(p.success("""{"message":"hi"}""")))
      val turns =
        listOf(
          ChatTurn(ChatRole.USER, "I had 2 idlis"),
          ChatTurn(ChatRole.ASSISTANT, "With chutney?"),
          ChatTurn(ChatRole.USER, "Yes, coconut chutney")
        )

      val reply = p.create(baseUrl()).chat(turns, "key", "SYS", "")

      assertEquals(p.name, """{"message":"hi"}""", reply)
      val body = server.takeRequest().body.readUtf8()
      listOf("I had 2 idlis", "With chutney?", "Yes, coconut chutney").forEach {
        assertTrue("${p.name} dropped '$it'", body.contains(it))
      }
    }
  }

  @Test
  fun `Gemini retries on the fallback model when the requested one is not found`() = runBlocking {
    server.enqueue(
      MockResponse().setResponseCode(404).setBody("""{"error":{"message":"model NOT_FOUND"}}""")
    )
    server.enqueue(
      MockResponse().setBody("""{"candidates":[{"content":{"parts":[{"text":"ok"}]}}]}""")
    )

    val text = GeminiAdapter(baseUrl = baseUrl()).parseMeal("x", "k", "sys", "gemini-9-ultra")

    assertEquals("ok", text)
    assertEquals("/v1beta/models/gemini-9-ultra:generateContent", server.takeRequest().path)
    assertEquals("/v1beta/models/gemini-3.5-flash:generateContent", server.takeRequest().path)
  }

  @Test
  fun `Gemini does not retry on other errors`() = runBlocking {
    server.enqueue(MockResponse().setResponseCode(429).setBody("""{"error":{"message":"quota"}}"""))

    val error =
      runCatching { GeminiAdapter(baseUrl = baseUrl()).parseMeal("x", "k", "sys", "") }
        .exceptionOrNull()

    assertTrue(error!!.message!!.contains("quota"))
    assertEquals(1, server.requestCount)
  }
}
