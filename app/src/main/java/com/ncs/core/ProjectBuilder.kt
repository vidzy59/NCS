package com.ncs.core

import com.ncs.model.Project
import com.ncs.model.PromptPackage
import com.ncs.model.ResearchResult
import com.ncs.network.ProviderRouter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

class ProjectBuilder(
    private val providerRouter: ProviderRouter,
    private val memoryStore: MemoryStore,
    private val scope: CoroutineScope
) {

    private val json = Json { ignoreUnknownKeys = true }

    data class BuildResult(
        val project: Project,
        val backgroundClips: List<File>,
        val audioFile: File?,
        val lyricsData: List<com.ncs.model.TimedLyricLine>?
    )

    suspend fun build(research: ResearchResult, promptPackage: PromptPackage): BuildResult = withContext(Dispatchers.IO) {
        val projectId = promptPackage.meta.projectId
        val contentType = promptPackage.meta.contentType
        val platform = promptPackage.meta.platform
        val durationSec = promptPackage.visual.durationSec
        val sceneCount = promptPackage.visual.sceneCount

        // 1. Generate background video clips (parallel per scene)
        val scenePrompts = generateScenePrompts(promptPackage.visual.prompt, sceneCount, contentType)
        val backgroundClips = mutableListOf<File>()
        
        for ((index, scenePrompt) in scenePrompts.withIndex()) {
            val sceneDuration = durationSec / sceneCount
            val clipResult = providerRouter.generateVideo(scenePrompt, sceneDuration, platform.aspectRatio)
            if (clipResult.isSuccess) {
                backgroundClips.add(clipResult.getOrThrow())
            } else {
                // Fallback: generate image + ken burns
                val imgResult = providerRouter.generateImage(scenePrompt, promptPackage.visual.negativePrompt, platform.aspectRatio)
                if (imgResult.isSuccess) {
                    backgroundClips.add(imgResult.getOrThrow())
                }
            }
        }

        // 2. Generate/Download Music
        val audioFile = when (promptPackage.audio.source) {
            "suno", "udio" -> providerRouter.generateMusic(promptPackage.audio.musicPrompt ?: "", durationSec).getOrNull()
            "platform_native" -> {
                val tracks = providerRouter.getPlatformAudio(platform, promptPackage.audio.platformAudio.firstOrNull()?.genre).getOrNull()
                tracks?.firstOrNull()?.downloadUrl?.let { downloadAudio(it) }
            }
            else -> promptPackage.audio.musicFile?.let { File(it) }
        }

        // 3. Align Lyrics (if lyric vibe)
        val lyricsData = if (contentType == com.ncs.model.ContentType.LYRIC_VIBE && promptPackage.lyrics != null) {
            val lyrics = promptPackage.lyrics!!
            if (lyrics.timedData.isNotEmpty()) {
                lyrics.timedData
            } else if (audioFile != null && lyrics.rawText.isNotBlank()) {
                providerRouter.alignLyrics(audioFile, lyrics.rawText).getOrNull()
            } else {
                generateBasicLyricTiming(lyrics.rawText, durationSec)
            }
        } else null

        // 4. Build Project JSON
        val projectJson = buildProjectJson(promptPackage, backgroundClips, audioFile, lyricsData)

        val project = Project(
            id = projectId,
            title = research.analysis.title,
            contentType = contentType,
            platform = platform,
            brief = research.analysis.description,
            promptPackageJson = json.encodeToString(promptPackage),
            projectJson = projectJson,
            status = com.ncs.model.ProjectStatus.GENERATING_ASSETS
        )

        memoryStore.saveProject(project)

        BuildResult(project, backgroundClips, audioFile, lyricsData)
    }

    private fun generateScenePrompts(mainPrompt: String, count: Int, contentType: com.ncs.model.ContentType): List<String> {
        val sceneTemplates = when (contentType) {
            com.ncs.model.ContentType.NATURE -> listOf(
                "Establishing wide shot: %s",
                "Medium shot detail: %s, texture focus",
                "Close-up macro: %s, intricate details",
                "Dynamic action: %s, natural behavior",
                "Atmospheric mood: %s, lighting change",
                "Closing wide: %s, peaceful resolution"
            )
            com.ncs.model.ContentType.LYRIC_VIBE -> listOf(
                "Intro atmosphere: %s, mood setting",
                "Verse 1 visual: %s, narrative imagery",
                "Chorus peak: %s, emotional climax, dynamic camera",
                "Verse 2: %s, evolving story",
                "Bridge: %s, tension build, unique angle",
                "Final chorus/outro: %s, resolution, wide pull back"
            )
            else -> listOf(
                "Scene 1: %s",
                "Scene 2: %s, different angle",
                "Scene 3: %s, detail focus",
                "Scene 4: %s, movement",
                "Scene 5: %s, atmosphere",
                "Scene 6: %s, conclusion"
            )
        }
        return (0 until count).map { index ->
            val template = sceneTemplates.getOrElse(index) { sceneTemplates.last() }
            String.format(template, mainPrompt)
        }
    }

    private fun generateBasicLyricTiming(lyrics: String, durationSec: Int): List<com.ncs.model.TimedLyricLine> {
        val lines = lyrics.trim().split("\n").filter { it.isNotBlank() }
        val lineDuration = durationSec.toDouble() / lines.size
        return lines.mapIndexed { i, line ->
            com.ncs.model.TimedLyricLine(
                start = i * lineDuration,
                end = (i + 1) * lineDuration,
                text = line
            )
        }
    }

    private suspend fun downloadAudio(url: String): File? = withContext(Dispatchers.IO) {
        try {
            val request = okhttp3.Request.Builder().url(url).build()
            val response = providerRouter.okHttp.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null
            val bytes = response.body?.bytes() ?: return@withContext null
            val file = File.createTempFile("ncs_audio_", ".mp3")
            java.io.FileOutputStream(file).use { it.write(bytes) }
            file
        } catch (e: Exception) { null }
    }

    private fun buildProjectJson(pkg: PromptPackage, clips: List<File>, audio: File?, lyrics: List<com.ncs.model.TimedLyricLine>?): String {
        val scenes = clips.mapIndexed { index, clip ->
            mapOf(
                "id" to index + 1,
                "type" to "background",
                "assetPath" to clip.absolutePath,
                "durationSec" to pkg.visual.durationSec / pkg.visual.sceneCount,
                "camera" to pkg.visual.camera,
                "prompt" to generateScenePrompts(pkg.visual.prompt, pkg.visual.sceneCount, pkg.meta.contentType)[index]
            )
        }
        return json.encodeToString(mapOf(
            "meta" to mapOf(
                "projectId" to pkg.meta.projectId,
                "contentType" to pkg.meta.contentType.name,
                "platform" to pkg.meta.platform.id
            ),
            "visual" to mapOf(
                "luts" to pkg.visual.luts,
                "grain" to pkg.visual.grain,
                "scenes" to scenes
            ),
            "audio" to mapOf(
                "filePath" to audio?.absolutePath,
                "bpm" to pkg.audio.bpm,
                "vocalStem" to pkg.audio.vocalStem
            ),
            "lyrics" to lyrics?.map { mapOf("start" to it.start, "end" to it.end, "text" to it.text) } ?: emptyList(),
            "pacing" to pkg.pacing,
            "export" to mapOf("preset" to pkg.export.preset)
        ))
    }
}
