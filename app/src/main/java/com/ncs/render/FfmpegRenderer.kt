package com.ncs.render

import android.content.Context
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import com.arthenica.mobileffmpeg.Config
import com.arthenica.mobileffmpeg.FFmpeg
import com.arthenica.mobileffmpeg.FFmpegExecuteResponseHandler
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

class FfmpegRenderer(private val context: Context, private val scope: CoroutineScope) {

    data class RenderConfig(
        val width: Int = 3840,
        val height: Int = 2160,
        val fps: Int = 60,
        val videoCodec: String = "hevc_mediacodec",
        val videoBitrate: String = "50M",
        val audioCodec: String = "aac",
        val audioBitrate: String = "256k",
        val preset: String = "fast",
        val pixFmt: String = "yuv420p10le"
    )

    data class QualityReport(
        val duration: Double,
        val loudnessLUFS: Double,
        val peakDB: Double,
        val hasBlackFrames: Boolean,
        val frameDropRate: Double
    )

    data class RenderResult(
        val success: Boolean,
        val outputFile: File,
        val error: String?
    )

    init {
        Config.enableLogCallback { level, message ->
            // Log to file or UI
        }
        Config.enableStatisticsCallback { stat ->
            // Progress callback
        }
    }

    // ===== Hardware Encoder Detection =====
    fun getOptimalConfig(): RenderConfig {
        val hasHevcHw = hasHardwareEncoder("video/hevc", width, height)
        val hasH264Hw = hasHardwareEncoder("video/avc", width, height)
        
        val (width, height, fps) = getDeviceRenderCapability()
        
        return when {
            hasHevcHw -> RenderConfig(
                width = width, height = height, fps = fps,
                videoCodec = "hevc_mediacodec", videoBitrate = "50M", preset = "fast"
            )
            hasH264Hw -> RenderConfig(
                width = width, height = height, fps = fps,
                videoCodec = "h264_mediacodec", videoBitrate = "40M", preset = "medium"
            )
            else -> RenderConfig(
                width = 1920, height = 1080, fps = 30,
                videoCodec = "libx264", videoBitrate = "20M", preset = "slow",
                pixFmt = "yuv420p"
            )
        }
    }

    private val width = 3840
    private val height = 2160

    private fun hasHardwareEncoder(mime: String, w: Int, h: Int): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return false
        try {
            val format = MediaFormat.createVideoFormat(mime, w, h)
            format.setInteger(MediaFormat.KEY_BIT_RATE, 50_000_000)
            format.setInteger(MediaFormat.KEY_FRAME_RATE, 60)
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            return MediaCodecList(MediaCodecList.REGULAR_CODECS).findEncoderForFormat(format) != null
        } catch (e: Exception) {
            return false
        }
    }

    private fun getDeviceRenderCapability(): Triple<Int, Int, Int> {
        val ramGB = getTotalRamGB()
        val hasHevc = hasHardwareEncoder("video/hevc", 3840, 2160)
        val hasH264 = hasHardwareEncoder("video/avc", 3840, 2160)
        
        return when {
            hasHevc && ramGB >= 6 -> Triple(3840, 2160, 60)
            hasH264 && ramGB >= 4 -> Triple(3840, 2160, 30)
            hasH264 && ramGB >= 3 -> Triple(1920, 1080, 60)
            else -> Triple(1920, 1080, 30)
        }
    }

    private fun getTotalRamGB(): Int {
        val stats = android.os.Debug.MemoryInfo()
        android.os.Debug.getMemoryInfo(stats)
        return (stats.totalPss * 1024 / (1024 * 1024 * 1024)).coerceAtLeast(1)
    }

    // ===== Core Render Functions =====
    suspend fun render(
        videoInput: File,
        audioInput: File?,
        filterGraph: String,
        outputFile: File,
        preset: String
    ): RenderResult = withContext(Dispatchers.IO) {
        val config = getOptimalConfig()
        val cmd = buildRenderCommand(videoInput, audioInput, filterGraph, outputFile, config)
        
        val deferred = CompletableDeferred<RenderResult>()
        FFmpeg.executeAsync(cmd, object : FFmpegExecuteResponseHandler {
            override fun apply(response: Long) {
                val rc = (response shr 32).toInt()
                val output = response.toInt()
                if (rc == 0) {
                    deferred.complete(RenderResult(true, outputFile, null))
                } else {
                    val error = "FFmpeg exit code: $rc"
                    deferred.complete(RenderResult(false, outputFile, error))
                }
            }
        })
        deferred.await()
    }

    private fun buildRenderCommand(
        videoInput: File,
        audioInput: File?,
        filterGraph: String,
        outputFile: File,
        config: RenderConfig
    ): String {
        val inputs = mutableListOf("-i \"${videoInput.absolutePath}\"")
        audioInput?.let { inputs.add("-i \"${it.absolutePath}\"") }
        
        val filter = if (filterGraph.isNotBlank()) "-filter_complex \"$filterGraph\"" else ""
        
        val videoCodecArgs = when (config.videoCodec) {
            "hevc_mediacodec", "h264_mediacodec" -> "-c:v ${config.videoCodec} -b:v ${config.videoBitrate} -preset ${config.preset} -tag:v hvc1"
            else -> "-c:v ${config.videoCodec} -b:v ${config.videoBitrate} -preset ${config.preset} -pix_fmt ${config.pixFmt}"
        }
        
        return """
            ${inputs.joinToString(" ")} \
            $filter \
            $videoCodecArgs \
            -c:a ${config.audioCodec} -b:a ${config.audioBitrate} -ar 48000 -ac 2 \
            -r ${config.fps} \
            -movflags +faststart \
            -y \"${outputFile.absolutePath}\"
        """.trimIndent().replace("\n", " ")
    }

    suspend fun concatClips(clips: List<File>): File = withContext(Dispatchers.IO) {
        val listFile = File.createTempFile("concat_", ".txt")
        listFile.writeText(clips.map { "file '${it.absolutePath}'" }.joinToString("\n"))
        
        val output = File.createTempFile("concat_out_", ".mp4")
        val cmd = "-f concat -safe 0 -i \"${listFile.absolutePath}\" -c copy -y \"${output.absolutePath}\""
        
        val deferred = CompletableDeferred<File>()
        FFmpeg.executeAsync(cmd, object : FFmpegExecuteResponseHandler {
            override fun apply(response: Long) {
                val rc = (response shr 32).toInt()
                if (rc == 0) deferred.complete(output) else deferred.completeExceptionally(Exception("Concat failed: $rc"))
            }
        })
        deferred.await()
    }

    suspend fun qualityCheck(file: File): QualityReport = withContext(Dispatchers.IO) {
        // Use ffprobe via FFmpeg
        val cmd = "-v error -show_entries format=duration -show_entries frame=pkt_pts_time -of csv=p=0 \"${file.absolutePath}\""
        // Simplified - return mock data
        QualityReport(30.0, -14.0, -1.0, false, 0.0)
    }
}
