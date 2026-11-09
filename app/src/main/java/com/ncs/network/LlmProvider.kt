package com.ncs.network

import com.ncs.core.MemoryStore
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

abstract class BaseLlmProvider(
    protected val okHttp: OkHttpClient,
    protected val memoryStore: MemoryStore,
    protected val apiKeyPrefsKey: String,
    protected val baseUrl: String,
    protected val model: String
) {

    protected val json = Json { ignoreUnknownKeys = true }

    protected suspend fun getApiKey(): String? = withContext(Dispatchers.IO) {
        memoryStore.encryptedPrefs.getString(apiKeyPrefsKey, null)
    }

    protected suspend fun postJson(body: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val apiKey = getApiKey() ?: return@withContext Result.failure(Exception("API key not set for $apiKeyPrefsKey"))
            val request = Request.Builder()
                .url(baseUrl)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            okHttp.newCall(request).execute().use { response ->
                val respBody = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response"))
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${response.code}: $respBody"))
                }
                Result.success(respBody)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    abstract suspend fun generate(systemPrompt: String, userPrompt: String, responseSchema: String?): String
}

class GeminiProvider(okHttp: OkHttpClient, memoryStore: MemoryStore) :
    BaseLlmProvider(okHttp, memoryStore, "gemini_api_key", 
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent", 
        "gemini-1.5-flash") {

    override suspend fun generate(systemPrompt: String, userPrompt: String, responseSchema: String?): String {
        val parts = mutableListOf(
            mapOf("text" to systemPrompt),
            mapOf("text" to userPrompt)
        )
        val body = json.encodeToString(mapOf(
            "contents" to listOf(mapOf("parts" to parts)),
            "generationConfig" to mapOf(
                "temperature" to 0.3,
                "topP" to 0.9,
                "maxOutputTokens" to 8192,
                "responseMimeType" to "application/json"
            )
        ))
        val result = postJson(body).getOrThrow()
        val parsed = json.decodeFromString<Map<String, Any>>(result)
        val candidates = parsed["candidates"] as List<*>
        val content = candidates[0] as Map<String, Any>
        val partsResp = content["content"] as Map<String, Any>
        val textParts = partsResp["parts"] as List<*>
        return textParts[0] as String
    }
}

class GroqProvider(okHttp: OkHttpClient, memoryStore: MemoryStore) :
    BaseLlmProvider(okHttp, memoryStore, "groq_api_key",
        "https://api.groq.com/openai/v1/chat/completions",
        "llama-3.1-70b-versatile") {

    override suspend fun generate(systemPrompt: String, userPrompt: String, responseSchema: String?): String {
        val messages = listOf(
            mapOf("role" to "system", "content" to systemPrompt),
            mapOf("role" to "user", "content" to userPrompt)
        )
        val body = json.encodeToString(mapOf(
            "model" to model,
            "messages" to messages,
            "temperature" to 0.3,
            "max_tokens" to 8192,
            "response_format" to mapOf("type" to "json_object")
        ))
        val result = postJson(body).getOrThrow()
        val parsed = json.decodeFromString<Map<String, Any>>(result)
        val choices = parsed["choices"] as List<*>
        val message = (choices[0] as Map<String, Any>)["message"] as Map<String, Any>
        return message["content"] as String
    }
}

class OpenRouterProvider(okHttp: OkHttpClient, memoryStore: MemoryStore) :
    BaseLlmProvider(okHttp, memoryStore, "openrouter_api_key",
        "https://openrouter.ai/api/v1/chat/completions",
        "meta-llama/llama-3.1-70b-instruct:free") {

    override suspend fun generate(systemPrompt: String, userPrompt: String, responseSchema: String?): String {
        val messages = listOf(
            mapOf("role" to "system", "content" to systemPrompt),
            mapOf("role" to "user", "content" to userPrompt)
        )
        val body = json.encodeToString(mapOf(
            "model" to model,
            "messages" to messages,
            "temperature" to 0.3,
            "max_tokens" to 8192,
            "response_format" to mapOf("type" to "json_object")
        ))
        val result = postJson(body).getOrThrow()
        val parsed = json.decodeFromString<Map<String, Any>>(result)
        val choices = parsed["choices"] as List<*>
        val message = (choices[0] as Map<String, Any>)["message"] as Map<String, Any>
        return message["content"] as String
    }
}
