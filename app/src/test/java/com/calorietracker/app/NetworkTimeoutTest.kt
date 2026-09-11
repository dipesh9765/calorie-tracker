package com.calorietracker.app

import com.calorietracker.app.data.remote.adapter.IAiProviderAdapter
import com.calorietracker.app.data.repository.AiNutritionRepositoryImpl
import com.calorietracker.app.domain.model.AiProviderType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.net.SocketTimeoutException

/**
 * Production Test Suite verifying network timeout handling, user-friendly error transformation,
 * and connection failure recovery across AI nutrition repositories.
 */
class NetworkTimeoutTest {

    @Test
    fun `repository handles SocketTimeoutException gracefully with user-friendly error message`() = runBlocking {
        val timingOutAdapter = object : IAiProviderAdapter {
            override suspend fun parseMeal(inputText: String, apiKey: String, systemInstruction: String, modelName: String): String {
                throw SocketTimeoutException("Read timed out after 60000ms")
            }
        }

        val repository = AiNutritionRepositoryImpl(
            geminiAdapter = timingOutAdapter,
            openAiAdapter = timingOutAdapter,
            claudeAdapter = timingOutAdapter,
            deepSeekAdapter = timingOutAdapter
        )

        val result = repository.parseMealText("Yesterday I ate 2 rotis", AiProviderType.GEMINI, "valid_key")
        assertTrue(result.isFailure)

        val exception = result.exceptionOrNull()
        assertNotNull(exception)
        val errorMessage = exception?.message ?: ""
        assertTrue("Error message should mention timeout and provider name, but was: $errorMessage", 
            errorMessage.contains("timed out") && errorMessage.contains("Gemini"))
    }

    @Test
    fun `repository handles UnknownHostException with offline network guidance`() = runBlocking {
        val offlineAdapter = object : IAiProviderAdapter {
            override suspend fun parseMeal(inputText: String, apiKey: String, systemInstruction: String, modelName: String): String {
                throw java.net.UnknownHostException("Unable to resolve host generativelanguage.googleapis.com")
            }
        }

        val repository = AiNutritionRepositoryImpl(geminiAdapter = offlineAdapter)

        val result = repository.parseMealText("2 rotis with dal", AiProviderType.GEMINI, "valid_key")
        assertTrue(result.isFailure)

        val errorMessage = result.exceptionOrNull()?.message ?: ""
        assertTrue(errorMessage.contains("No internet connection available"))
    }
}
