package com.ncs.core

import com.ncs.core.intel.PlatformIntel
import com.ncs.core.intel.PlatformIntelRegistry
import com.ncs.model.ContentType
import com.ncs.model.LyricsSpec
import com.ncs.model.Platform
import com.ncs.model.PromptPackage
import com.ncs.model.ResearchResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.Json

class PromptEngine(
    private val providerRouter: ProviderRouter,
    private val intelRegistry: PlatformIntelRegistry,
    private val memoryStore: MemoryStore,
    private val scope: CoroutineScope
) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun research(brief: String, contentType: ContentType, platform: Platform): ResearchResult {
        val intel = intelRegistry.getIntel(platform)
        val prefs = memoryStore.getPreferences()

        // 1. Get platform-specific intel
        val trends = intel.getTrending(niche = extractNiche(brief))
        val trendingAudio = intel.getTrendingAudio(genre = extractGenre(brief))
        val constraints = intel.constraints
        val postingTimes = intel.getOptimalPostingTime(audience = "general")
        val hashtags = intel.getHashtagStrategy(niche = extractNiche(brief))
        val competitors = intel.analyzeTopPerformers(niche = extractNiche(brief))

        // 2. LLM Analysis
        val systemPrompt = buildResearchSystemPrompt(contentType, platform)
        val userPrompt = buildResearchUserPrompt(brief, trends, trendingAudio, competitors, prefs)

        val analysisJson = providerRouter.callLLM(systemPrompt, userPrompt).getOrThrow()
        val analysis = json.decodeFromString<ResearchResult.ContentAnalysis>(analysisJson)

        return ResearchResult(
            trends = trends,
            trendingAudio = trendingAudio,
            constraints = constraints,
            analysis = analysis,
            optimalPostingTimes = postingTimes,
            hashtagStrategy = hashtags,
            competitorAnalysis = competitors
        )
    }

    suspend fun buildPromptPackage(research: ResearchResult, prefs: Preferences): PromptPackage {
        val contentType = when {
            research.analysis.visualStyle.lowercase().contains("lyric") || research.analysis.visualStyle.lowercase().contains("music") -> ContentType.LYRIC_VIBE
            research.analysis.visualStyle.lowercase().contains("nature") || research.analysis.visualStyle.lowercase().contains("animal") -> ContentType.NATURE
            else -> ContentType.HYBRID
        }

        val platform = research.constraints.nativeFeatures.firstOrNull { it.contains("meta") || it.contains("instagram") }
            ?.let { if (it.contains("meta")) Platform.FACEBOOK_REELS else Platform.INSTAGRAM_REELS }
            ?: Platform.YOUTUBE_SHORTS

        // Build visual prompt with platform constraints
        val visualPrompt = providerRouter.callLLM(
            buildVisualSystemPrompt(contentType, platform),
            buildVisualUserPrompt(research, prefs)
        ).getOrThrow()

        // Build audio prompt
        val audioPrompt = providerRouter.callLLM(
            "Anda adalah music supervisor. Buat prompt musik untuk AI generator (Suno/Udio) berdasarkan brief.",
            "Genre: ${research.analysis.audioVibe}\nBPM: ${prefs.bpmRanges[contentType.name.lowercase()]?.random()}\nMood: ${research.analysis.audioVibe}\nDurasi: ${research.constraints.maxDurationSec} detik"
        ).getOrThrow()

        // Build lyrics spec
        val lyricsSpec = if (contentType == ContentType.LYRIC_VIBE || contentType == ContentType.HYBRID) {
            LyricsSpec(
                source = "upload",
                rawText = "", // User will provide
                style = com.ncs.model.LyricsStyle(
                    animation = "karaoke",
                    position = "bottom_center"
                )
            )
        } else null

        return PromptPackage(
            meta = PromptPackage.Meta(
                projectId = java.util.UUID.randomUUID().toString(),
                contentType = contentType,
                platform = platform,
                reset = true
            ),
            visual = PromptPackage.VisualSpec(
                prompt = visualPrompt,
                camera = extractCameraFromPrefs(prefs, contentType),
                luts = listOf(prefs.colorPalettes[contentType.name.lowercase()] ?: "cinematic"),
                grain = 0.15f,
                durationSec = research.constraints.maxDurationSec,
                sceneCount = 6,
                styleTags = research.analysis.visualStyle.split(", ").toList(),
                constraints = research.constraints
            ),
            audio = PromptPackage.AudioSpec(
                source = "suno",
                musicPrompt = audioPrompt,
                bpm = prefs.bpmRanges[contentType.name.lowercase()]?.random(),
                platformAudio = research.trendingAudio
            ),
            lyrics = lyricsSpec,
            pacing = prefs.pacingRules[contentType.name.lowercase()] ?: "variable_3_12s",
            export = PromptPackage.ExportSpec(
                preset = when (platform) {
                    Platform.FACEBOOK_REELS, Platform.INSTAGRAM_REELS -> "reel_4k_60"
                    Platform.YOUTUBE_SHORTS -> "short_4k_60"
                    Platform.TIKTOK -> "tiktok_4k_60"
                    else -> "reel_4k_60"
                }
            )
        )
    }

    suspend fun validatePrompt(pkg: PromptPackage): PromptPackage {
        val criticPrompt = """
            Critique this video generation prompt for realism (1-10 score + specific fixes):
            Visual: ${pkg.visual.prompt}
            Negative: ${pkg.visual.negativePrompt}
            Camera: ${pkg.visual.camera}
            Platform: ${pkg.meta.platform}
            Return JSON: {"score": 8, "issues": ["issue1"], "fixed_prompt": "..."}
        """.trimIndent()
        val result = providerRouter.callLLM("You are a professional cinematographer and AI prompt critic.", criticPrompt).getOrThrow()
        val critique = json.decodeFromString<Map<String, Any>>(result)
        val score = (critique["score"] as? Int) ?: 7
        if (score < 7) {
            val fixed = critique["fixed_prompt"] as? String
            if (fixed != null) {
                return pkg.copy(visual = pkg.visual.copy(prompt = fixed))
            }
        }
        return pkg
    }

    // Helper methods
    private fun buildResearchSystemPrompt(contentType: ContentType, platform: Platform): String = """
        Anda adalah Creative Director untuk konten ${contentType.name.lowercase()} di ${platform.displayName}.
        Tugas: Analisis brief user + data platform → hasilkan strategi konten yang VIRAL & PLATFORM-NATIVE.
        Output JSON: ContentAnalysis (hook, visualStyle, pacing, cameraStyle, colorPalette, audioVibe, title, description, tags)
    """.trimIndent()

    private fun buildResearchUserPrompt(brief: String, trends: TrendReport, audio: List<AudioTrack>, competitors: List<ResearchResult.ContentAnalysis>, prefs: Preferences): String = """
        Brief User: $brief
        
        Platform Trends:
        - Topik: ${trends.trendingTopics.take(5).joinToString(", ") { it.topic }}
        - Format: ${trends.trendingFormats.take(3).joinToString(", ") { it.format }}
        - Audio Trending: ${audio.take(3).joinToString(", ") { it.title }}
        - Competitor Hooks: ${competitors.take(3).joinToString(", ") { it.hook }}
        - Sentimen: ${trends.audienceSentiment.keyEmotions.joinToString(", ")}
        
        User Preferences:
        - Genre: ${prefs.preferredGenres[contentType.name.lowercase()]}
        - BPM: ${prefs.bpmRanges[contentType.name.lowercase()]}
        - Warna: ${prefs.colorPalettes[contentType.name.lowercase()]}
        - Lensa: ${prefs.lensPreferences[contentType.name.lowercase()]}
        - Gerakan: ${prefs.cameraMovements[contentType.name.lowercase()]}
        
        Hasilkan analisis lengkap untuk konten yang akan viral di platform ini.
    """.trimIndent()

    private fun buildVisualSystemPrompt(contentType: ContentType, platform: Platform): String = """
        Anda adalah Director of Photography. Buat PROMPT VISUAL untuk AI video generator (Kling/LTX/HF).
        ATURAN WAJIB:
        1. JANGAN PERNAH menyertakan teks, lirik, watermark, UI, subtitle, angka, huruf
        2. Fokus: visual cinematic, realistic, natural lighting, proper camera movement
        3. Platform: ${platform.displayName} (${platform.aspectRatio}, max ${platform.maxDurationSec}s)
        4. Negative prompt otomatis: "text, title, lyrics, watermark, logo, ui, subtitles, caption, letters, numbers"
        Output: HANYA prompt visual (bahasa Inggris, deskriptif, teknis kamera)
    """.trimIndent()

    private fun buildVisualUserPrompt(research: ResearchResult, prefs: Preferences): String = """
        Visual Style: ${research.analysis.visualStyle}
        Camera Style: ${research.analysis.cameraStyle}
        Color Palette: ${research.analysis.colorPalette}
        Pacing: ${research.analysis.pacing}
        Platform Constraints: ${research.constraints.aspectRatio}, ${research.constraints.maxDurationSec}s
        Preferred Lenses: ${prefs.lensPreferences[research.analysis.visualStyle.lowercase()]}
        Preferred Movements: ${prefs.cameraMovements[research.analysis.visualStyle.lowercase()]}
    """.trimIndent()

    private fun extractNiche(brief: String): String = brief.take(50)
    private fun extractGenre(brief: String): String? = brief.lowercase().let {
        when {
            it.contains("pop") -> "pop"
            it.contains("rock") -> "rock"
            it.contains("lofi") -> "lofi"
            it.contains("ambient") -> "ambient"
            it.contains("indie") -> "indie"
            else -> null
        }
    }

    private fun extractCameraFromPrefs(prefs: Preferences, contentType: ContentType): String {
        val lens = prefs.lensPreferences[contentType.name.lowercase()]?.random() ?: 35
        val movement = prefs.cameraMovements[contentType.name.lowercase()]?.random() ?: "slow_dolly"
        return "${lens}mm, $movement"
    }
}
