package com.ncs.model

import kotlinx.serialization.Serializable

@Serializable
enum class Platform(val id: String, val displayName: String, val aspectRatio: String, val maxDurationSec: Int) {
    FACEBOOK_REELS("fb_reels", "Facebook Reels", "9:16", 90),
    INSTAGRAM_REELS("ig_reels", "Instagram Reels", "9:16", 90),
    YOUTUBE_SHORTS("yt_shorts", "YouTube Shorts", "9:16", 60),
    TIKTOK("tiktok", "TikTok", "9:16", 180),
    X_TWITTER("x_twitter", "X (Twitter)", "16:9", 140);

    companion object {
        fun fromId(id: String): Platform = values().firstOrNull { it.id == id } ?: YOUTUBE_SHORTS
    }
}

@Serializable
data class PlatformConstraints(
    val aspectRatio: String,
    val maxDurationSec: Int,
    val maxFileSizeMB: Int = 2048,
    val videoCodecs: List<String> = listOf("h264", "hevc", "vp9"),
    val audioRequirements: AudioRequirements = AudioRequirements(),
    val requiredMetadata: List<String> = listOf("title", "description", "tags"),
    val monetizationRules: MonetizationRules = MonetizationRules(),
    val nativeFeatures: List<String> = emptyList()
)

@Serializable
data class AudioRequirements(
    val loudnessLUFS: Double = -14.0,
    val maxPeakDB: Double = -1.0,
    val sampleRate: Int = 48000,
    val channels: Int = 2,
    val copyrightPolicy: String = "platform_native"
)

@Serializable
data class MonetizationRules(
    val eligible: Boolean = true,
    val minDurationSec: Int = 60,
    val requirements: List<String> = emptyList()
)
