package com.ncs.network

import com.ncs.core.MemoryStore
import com.ncs.model.TimedLyricLine
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream

interface ImageProvider : ProviderRouter.MediaProvider<File> {
    suspend fun generate(prompt: String, negativePrompt: String?, aspectRatio: String): Result<File>
}

class PollinationsProvider(private val okHttp: OkHttpClient) : ImageProvider {
    override val name = "Pollinations"

    override suspend fun generate(prompt: String, negativePrompt: String?, aspectRatio: String): Result<File> {
        return try {
            val encodedPrompt = prompt.replace(" ", "%20")
            val neg = negativePrompt?.let { "&noprompt=$it" } ?: ""
            val (w, h) = when (aspectRatio) {
                "9:16" -> 576 to 1024
                "16:9" -> 1024 to 576
                "1:1" -> 1024 to 1024
                else -> 576 to 1024
            }
            val url = "https://image.pollinations.ai/prompt/$encodedPrompt?width=$w&height=$h&model=flux&nologo=true$neg"
            
            val request = Request.Builder().url(url).build()
            val response = okHttp.newCall(request).execute()
            if (!response.isSuccessful) return Result.failure(Exception("HTTP ${response.code}"))
            
            val bytes = response.body?.bytes() ?: return Result.failure(Exception("Empty image"))
            val file = File.createTempFile("ncs_img_", ".jpg")
            FileOutputStream(file).use { it.write(bytes) }
            Result.success(file)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

class HuggingFaceProvider(
    private val okHttp: OkHttpClient,
    private val memoryStore: MemoryStore
) {
    val image = object : ImageProvider {
        override val name = "HF-FLUX"
        override suspend fun generate(prompt: String, negativePrompt: String?, aspectRatio: String): Result<File> {
            return try {
                val apiKey = memoryStore.encryptedPrefs.getString("hf_token", "") ?: 
                    return Result.failure(Exception("HF token not set"))
                val (w, h) = when (aspectRatio) {
                    "9:16" -> 576 to 1024
                    "16:9" -> 1024 to 576
                    else -> 576 to 1024
                }
                val body = """{"inputs":"$prompt","parameters":{"width":$w,"height":$h,"negative_prompt":"${negativePrompt ?: ""}"}}"""
                
                val request = Request.Builder()
                    .url("https://api-inference.huggingface.co/models/black-forest-labs/FLUX.1-schnell")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()
                
                val response = okHttp.newCall(request).execute()
                if (!response.isSuccessful) return Result.failure(Exception("HF HTTP ${response.code}"))
                
                val bytes = response.body?.bytes() ?: return Result.failure(Exception("Empty image"))
                val file = File.createTempFile("ncs_hf_img_", ".jpg")
                FileOutputStream(file).use { it.write(bytes) }
                Result.success(file)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    val video = object : VideoProvider {
        override val name = "HF-LTX"
        override suspend fun generate(prompt: String, durationSec: Int, aspectRatio: String): Result<File> {
            return try {
                val apiKey = memoryStore.encryptedPrefs.getString("hf_token", "") ?: 
                    return Result.failure(Exception("HF token not set"))
                val body = """{"inputs":"$prompt","parameters":{"num_frames":${durationSec * 8},"height":576,"width":1024}}"""
                
                val request = Request.Builder()
                    .url("https://api-inference.huggingface.co/models/Lightricks/LTX-Video")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()
                
                val response = okHttp.newCall(request).execute()
                if (!response.isSuccessful) return Result.failure(Exception("HF Video HTTP ${response.code}"))
                
                val bytes = response.body?.bytes() ?: return Result.failure(Exception("Empty video"))
                val file = File.createTempFile("ncs_hf_vid_", ".mp4")
                FileOutputStream(file).use { it.write(bytes) }
                Result.success(file)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun alignLyrics(audioFile: File, lyricsText: String): Result<List<TimedLyricLine>> {
        // Call Whisper API for alignment
        // Simplified - returns basic timing
        return Result.success(lyricsText.split("\n").mapIndexed { i, line ->
            TimedLyricLine(start = i * 4.0, end = (i + 1) * 4.0, text = line)
        })
    }
}
