package com.ncs.model

import kotlinx.serialization.Serializable

@Serializable
data class ResearchResult(
    val trends: TrendReport,
    val trendingAudio: List<AudioTrack>,
    val constraints: PlatformConstraints,
    val analysis: ContentAnalysis,
    val optimalPostingTimes: List<TimeSlot>,
    val hashtagStrategy: HashtagPlan,
    val competitorAnalysis: List<ContentAnalysis>
)

@Serializable
data class TrendReport(
    val trendingTopics: List<TrendingTopic>,
    val trendingAudio: List<AudioTrack>,
    val trendingFormats: List<FormatTrend>,
    val audienceSentiment: SentimentAnalysis,
    val freshness: Long
)

@Serializable
data class TrendingTopic(
    val topic: String,
    val volume: Long,
    val growth: Double,
    val relatedKeywords: List<String>
)

@Serializable
data class FormatTrend(
    val format: String, // "POV", "Storytime", "Transformation", "Listicle"
    val popularity: Double,
    val examples: List<String>
)

@Serializable
data class SentimentAnalysis(
    val positive: Double,
    val neutral: Double,
    val negative: Double,
    val keyEmotions: List<String>
)

@Serializable
data class ContentAnalysis(
    val hook: String,
    val visualStyle: String,
    val pacing: String,
    val cameraStyle: String,
    val colorPalette: String,
    val audioVibe: String,
    val title: String,
    val description: String,
    val tags: List<String>
)

@Serializable
data class TimeSlot(
    val dayOfWeek: Int, // 1-7
    val hour: Int, // 0-23
    val engagementScore: Double
)

@Serializable
data class HashtagPlan(
    val primaryTags: List<String>,
    val secondaryTags: List<String>,
    val nicheTags: List<String>,
    val platformSpecificTags: List<String>
)
