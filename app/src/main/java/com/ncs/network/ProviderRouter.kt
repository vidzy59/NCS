package com.ncs.network

import com.ncs.core.MemoryStore
import com.ncs.model.AudioTrack
import com.ncs.model.Platform
import com.ncs.model.PromptPackage
import com.ncs.model.TimedLyricLine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

class ProviderRouter(
    private val okHttp: OkHttpClient,
    private val memoryStore: MemoryStore,
    private val scope: CoroutineScope
) {

    // ===== LLM Providers =====
    private val geminiProvider = GeminiProvider(okHttp, memoryStore)
    private val groqProvider = GroqProvider(okHttp, memoryStore)
    private val openRouterProvider = OpenRouterProvider(okHttp, memoryStore)

    // ===== Media Providers =====
    private val pollinationsProvider = PollinationsProvider(okHttp)
    private val hfProvider = HuggingFaceProvider(okHttp, memoryStore)
    private val klingProvider = KlingProvider(okHttp, memoryStore)
    private val ltxProvider = LTXVideoProvider(okHttp, memoryStore)
    private val sunoProvider = SunoProvider(okHttp, memoryStore)
    private val udioProvider = UdioProvider(okHttp, memoryStore)

    // ===== Health & Quota Tracking =====
    private val providerHealth = mutableMapOf<String, ProviderStatus>()
    private val quotaTracker = mutableMap<String, QuotaInfo>()

    data class ProviderStatus(
        val name: String,
        var healthy: Boolean = true,
        var consecutiveFailures: Int = 0,
        var lastSuccess: Long = 0,
        var lastError: String? = null
    )

    data class QuotaInfo(
        val used: Long = 0,
        val limit: Long = Long.MAX_VALUE,
        val resetAt: Long = 0
    )

    // ===== LLM Calls with Fallback =====
    suspend fun callLLM(
        systemPrompt: String,
        userPrompt: String,
        responseSchema: String? = null,
        preferredProvider: LlmProviderType = LlmProviderType.AUTO
    ): Result<String> = scope.coroutineContext[Dispatchers.IO]?.let { dispatcher ->
        withContext(dispatcher) { _callLLM(systemPrompt, userPrompt, responseSchema, preferredProvider) }
    } ?: runBlocking { _callLLM(systemPrompt, userPrompt, responseSchema, preferredProvider) }

    private suspend fun _callLLM(
        systemPrompt: String,
        userPrompt: String,
        responseSchema: String? = null,
        preferredProvider: LlmProviderType
    ): Result<String> {
        val providers = when (preferredProvider) {
            LlmProviderType.GEMINI -> listOf(geminiProvider, groqProvider, openRouterProvider)
            LlmProviderType.GROQ -> listOf(groqProvider, geminiProvider, openRouterProvider)
            LlmProviderType.OPENROUTER -> listOf(openRouterProvider, geminiProvider, groqProvider)
            else -> listOf(geminiProvider, groqProvider, openRouterProvider)
        }

        var lastException: Exception? = null
        for (provider in providers) {
            val status = getOrCreateStatus(provider.name)
            if (!status.healthy && status.consecutiveFailures >= 3) continue

            try {
                val result = withTimeoutOrNull(30_000) {
                    provider.generate(systemPrompt, userPrompt, responseSchema)
                }
                if (result != null) {
                    onSuccess(provider.name)
                    return Result.success(result)
                }
            } catch (e: Exception) {
                lastException = e
                onFailure(provider.name, e.message ?: "Unknown error")
            }
        }
        return Result.failure(lastException ?: Exception("All LLM providers failed"))
    }

    // ===== Image Generation =====
    suspend fun generateImage(prompt: String, negativePrompt: String? = null, aspectRatio: String = "9:16"): Result<File> {
        val providers = listOf(pollinationsProvider, hfProvider.image)
        return tryProviders(providers) { it.generate(prompt, negativePrompt, aspectRatio) }
    }

    // ===== Video Generation =====
    suspend fun generateVideo(prompt: String, durationSec: Int, aspectRatio: String = "9:16"): Result<File> {
        val providers = listOf(klingProvider, ltxProvider, hfProvider.video, pollinationsProvider.video)
        return tryProviders(providers) { it.generate(prompt, durationSec, aspectRatio) }
    }

    // ===== Music Generation =====
    suspend fun generateMusic(prompt: String, durationSec: Int): Result<File> {
        val providers = listOf(sunoProvider, udioProvider)
        return tryProviders(providers) { it.generate(prompt, durationSec) }
    }

    // ===== Platform Audio Library =====
    suspend fun getPlatformAudio(platform: Platform, genre: String?): Result<List<AudioTrack>> {
        return when (platform) {
            Platform.FACEBOOK_REELS, Platform.INSTAGRAM_REELS -> MetaAudioProvider(okHttp, memoryStore).getTrendingAudio(genre)
            Platform.YOUTUBE_SHORTS -> YouTubeAudioProvider(okHttp, memoryStore).getTrendingAudio(genre)
            Platform.TIKTOK -> TikTokAudioProvider(okHttp, memoryStore).getTrendingAudio(genre)
            else -> Result.success(emptyList())
        }
    }

    // ===== Lyrics Alignment (Whisper) =====
    suspend fun alignLyrics(audioFile: File, lyricsText: String): Result<List<TimedLyricLine>> {
        return hfProvider.alignLyrics(audioFile, lyricsText)
    }

    // ===== Generic Fallback Helper =====
    private suspend fun <T> tryProviders(
        providers: List<MediaProvider<T>>,
        call: (MediaProvider<T>) -> Result<T>
    ): Result<T> {
        var lastEx: Exception? = null
        for (provider in providers) {
            val status = getOrCreateStatus(provider.name)
            if (!status.healthy && status.consecutiveFailures >= 3) continue

            try {
                val result = withTimeoutOrNull(60_000) { call(provider) }
                if (result?.isSuccess == true) {
                    onSuccess(provider.name)
                    return Result.success(result.getOrThrow())
                } else {
                    onFailure(provider.name, result?.exceptionOrNull()?.message ?: "Failed")
                }
            } catch (e: Exception) {
                lastEx = e
                onFailure(provider.name, e.message ?: "Unknown error")
            }
        }
        return Result.failure(lastEx ?: Exception("All providers failed"))
    }

    private fun getOrCreateStatus(name: String): ProviderStatus =
        providerHealth.getOrPut(name) { ProviderStatus(name) }

    private fun onSuccess(name: String) {
        val status = getOrCreateStatus(name)
        status.healthy = true
        status.consecutiveFailures = 0
        status.lastSuccess = System.currentTimeMillis()
    }

    private fun onFailure(name: String, error: String) {
        val status = getOrCreateStatus(name)
        status.consecutiveFailures++
        status.lastError = error
        if (status.consecutiveFailures >= 3) status.healthy = false
    }

    // ===== Public Status for UI =====
    fun getProviderStatus(): Map<String, ProviderStatus> = providerHealth.toMap()

    enum class LlmProviderType { AUTO, GEMINI, GROQ, OPENROUTER }

    // ===== Media Provider Interface =====
    interface MediaProvider<T> {
        val name: String
    }
}
