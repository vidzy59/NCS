package com.ncs.model

import kotlinx.serialization.Serializable

@Serializable
data class PromptPackage(
    val meta: Meta,
    val visual: VisualSpec,
    val audio: AudioSpec,
    val lyrics: LyricsSpec?,
    val pacing: String,
    val export: ExportSpec
)

@Serializable
data class Meta(
    val projectId: String,
    val contentType: ContentType,
    val platform: Platform,
    val reset: Boolean = true,
    val createdAt: Long = java.time.Instant.now().toEpochMilli()
)

@Serializable
data class VisualSpec(
    val prompt: String,
    val negativePrompt: String = "text, title, lyrics, watermark, logo, ui, subtitles, caption, letters, numbers, watermark, signature, username",
    val camera: String,
    val luts: List<String> = emptyList(),
    val grain: Float = 0.15f,
    val durationSec: Int,
    val sceneCount: Int = 6,
    val styleTags: List<String> = emptyList(),
    val constraints: PlatformConstraints? = null
)

@Serializable
data class AudioSpec(
    val source: String, // "suno", "udio", "youtube", "upload", "platform_native"
    val musicPrompt: String? = null,
    val musicFile: String? = null,
    val bpm: Int? = null,
    val vocalStem: String? = null,
    val platformAudio: List<AudioTrack> = emptyList()
)

@Serializable
data class AudioTrack(
    val id: String,
    val title: String,
    val artist: String,
    val durationSec: Int,
    val bpm: Int?,
    val genre: String?,
    val downloadUrl: String?,
    val platform: String
)

@Serializable
data class LyricsSpec(
    val source: String, // "upload", "genius", "synced", "whisper"
    val rawText: String,
    val timedData: List<TimedLyricLine> = emptyList(),
    val style: LyricsStyle = LyricsStyle(),
    val language: String = "id"
)

@Serializable
data class TimedLyricLine(
    val start: Double,
    val end: Double,
    val text: String,
    val syllableTimings: List<Double>? = null // for karaoke
)

@Serializable
data class LyricsStyle(
    val font: String = "Inter-Bold.ttf",
    val fontSize: Int = 72,
    val color: String = "#FFFFFF",
    val stroke: Stroke = Stroke(2, "#000000"),
    val shadow: Shadow = Shadow(2, 2, 4, "#00000080"),
    val animation: String = "fade_slide_up", // fade_slide_up, typewriter, beat_scale, karaoke
    val position: String = "bottom_center", // bottom_center, center, top_center, custom
    val marginV: Int = 120
)

@Serializable
data class Stroke(val width: Int, val color: String)

@Serializable
data class Shadow(val x: Int, val y: Int, val blur: Int, val color: String)

@Serializable
data class ExportSpec(
    val preset: String = "reel_4k_60", // reel_4k_60, short_1080p_30, youtube_4k_60, custom
    val loudnessLUFS: Double = -14.0,
    val bitrate: String? = null,
    val codec: String = "hevc"
)
