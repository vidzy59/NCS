package com.ncs.core.intel

import com.ncs.model.AudioTrack
import com.ncs.model.HashtagPlan
import com.ncs.model.Platform
import com.ncs.model.PlatformConstraints
import com.ncs.model.ResearchResult
import com.ncs.model.TimeSlot
import com.ncs.model.TrendReport
import kotlinx.coroutines.CoroutineScope

interface PlatformIntel {
    val platform: Platform
    val constraints: PlatformConstraints
    val nativeFeatures: List<String>

    suspend fun getTrending(niche: String, region: String = "ID"): TrendReport
    suspend fun getTrendingAudio(genre: String?): List<AudioTrack>
    suspend fun getOptimalPostingTime(audience: String): List<TimeSlot>
    suspend fun getHashtagStrategy(niche: String): HashtagPlan
    suspend fun analyzeTopPerformers(niche: String, count: Int = 10): List<ResearchResult.ContentAnalysis>
}

class PlatformIntelRegistry(
    private val metaIntel: MetaPlatformIntel,
    private val youtubeIntel: YouTubePlatformIntel,
    private val tiktokIntel: TikTokPlatformIntel,
    private val xIntel: XPlatformIntel
) {
    fun getIntel(platform: Platform): PlatformIntel = when (platform) {
        Platform.FACEBOOK_REELS, Platform.INSTAGRAM_REELS -> metaIntel
        Platform.YOUTUBE_SHORTS -> youtubeIntel
        Platform.TIKTOK -> tiktokIntel
        Platform.X_TWITTER -> xIntel
    }
}

// Stub implementations - to be filled with real API calls
class MetaPlatformIntel(okHttp: OkHttpClient, memoryStore: MemoryStore) : PlatformIntel {
    override val platform = Platform.FACEBOOK_REELS
    override val constraints = PlatformConstraints(
        aspectRatio = "9:16", maxDurationSec = 90,
        nativeFeatures = listOf("meta_sound_collection", "remix", "broadcast_channel", "group_share")
    )
    override val nativeFeatures = constraints.nativeFeatures
    override suspend fun getTrending(niche: String, region: String) = TrendReport(emptyList(), emptyList(), emptyList(), 
        com.ncs.model.SentimentAnalysis(0.5, 0.3, 0.2, listOf("joy", "curiosity")), System.currentTimeMillis())
    override suspend fun getTrendingAudio(genre: String?) = emptyList()
    override suspend fun getOptimalPostingTime(audience: String) = listOf(TimeSlot(1, 19, 0.85), TimeSlot(5, 20, 0.92))
    override suspend fun getHashtagStrategy(niche: String) = HashtagPlan(listOf("#reels", "#viral"), listOf("#indonesia"), listOf(), listOf("#fbreels"))
    override suspend fun analyzeTopPerformers(niche: String, count: Int) = emptyList()
}

class YouTubePlatformIntel(okHttp: OkHttpClient, memoryStore: MemoryStore) : PlatformIntel {
    override val platform = Platform.YOUTUBE_SHORTS
    override val constraints = PlatformConstraints(
        aspectRatio = "9:16", maxDurationSec = 60,
        nativeFeatures = listOf("content_id_safe", "remix_longform", "seo_metadata", "related_video_link")
    )
    override val nativeFeatures = constraints.nativeFeatures
    override suspend fun getTrending(niche: String, region: String) = TrendReport(emptyList(), emptyList(), emptyList(),
        com.ncs.model.SentimentAnalysis(0.6, 0.2, 0.2, listOf("entertainment", "education")), System.currentTimeMillis())
    override suspend fun getTrendingAudio(genre: String?) = emptyList()
    override suspend fun getOptimalPostingTime(audience: String) = listOf(TimeSlot(2, 18, 0.88), TimeSlot(6, 15, 0.91))
    override suspend fun getHashtagStrategy(niche: String) = HashtagPlan(listOf("#shorts", "#viral"), listOf("#indonesia"), listOf(), listOf("#ytsshorts"))
    override suspend fun analyzeTopPerformers(niche: String, count: Int) = emptyList()
}

class TikTokPlatformIntel(okHttp: OkHttpClient, memoryStore: MemoryStore) : PlatformIntel {
    override val platform = Platform.TIKTOK
    override val constraints = PlatformConstraints(
        aspectRatio = "9:16", maxDurationSec = 180,
        nativeFeatures = listOf("sound_centered", "duet_stitch", "green_screen", "hashtag_challenge")
    )
    override val nativeFeatures = constraints.nativeFeatures
    override suspend fun getTrending(niche: String, region: String) = TrendReport(emptyList(), emptyList(), emptyList(),
        com.ncs.model.SentimentAnalysis(0.7, 0.2, 0.1, listOf("fun", "trend")), System.currentTimeMillis())
    override suspend fun getTrendingAudio(genre: String?) = emptyList()
    override suspend fun getOptimalPostingTime(audience: String) = listOf(TimeSlot(3, 19, 0.93), TimeSlot(7, 21, 0.95))
    override suspend fun getHashtagStrategy(niche: String) = HashtagPlan(listOf("#fyp", "#viral"), listOf("#indonesia"), listOf(), listOf("#tiktokindonesia"))
    override suspend fun analyzeTopPerformers(niche: String, count: Int) = emptyList()
}

class XPlatformIntel(okHttp: OkHttpClient, memoryStore: MemoryStore) : PlatformIntel {
    override val platform = Platform.X_TWITTER
    override val constraints = PlatformConstraints(
        aspectRatio = "16:9", maxDurationSec = 140,
        nativeFeatures = listOf("thread_reply", "premium_boost", "spaces_integration")
    )
    override val nativeFeatures = constraints.nativeFeatures
    override suspend fun getTrending(niche: String, region: String) = TrendReport(emptyList(), emptyList(), emptyList(),
        com.ncs.model.SentimentAnalysis(0.5, 0.3, 0.2, listOf("news", "opinion")), System.currentTimeMillis())
    override suspend fun getTrendingAudio(genre: String?) = emptyList()
    override suspend fun getOptimalPostingTime(audience: String) = listOf(TimeSlot(4, 12, 0.82), TimeSlot(1, 20, 0.88))
    override suspend fun getHashtagStrategy(niche: String) = HashtagPlan(listOf("#trending"), listOf(), listOf(), listOf())
    override suspend fun analyzeTopPerformers(niche: String, count: Int) = emptyList()
}
