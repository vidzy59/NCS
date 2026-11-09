package com.ncs.core

import com.ncs.model.ContentType
import com.ncs.model.Project
import com.ncs.model.PromptPackage
import com.ncs.model.ResearchResult
import kotlinx.coroutines.CoroutineScope

class AgentOrchestrator(
    private val promptEngine: PromptEngine,
    private val projectBuilder: ProjectBuilder,
    private val editorEngine: EditorEngine,
    private val memoryStore: MemoryStore,
    private val scope: CoroutineScope
) {

    enum class Stage { RESEARCH, PROMPT_BUILD, VALIDATE, BUILD_ASSETS, EDIT_RENDER, COMPLETED, FAILED }

    data class RunState(
        val projectId: String,
        var stage: Stage = Stage.RESEARCH,
        var brief: String = "",
        var contentType: ContentType? = null,
        var platform: com.ncs.model.Platform? = null,
        var research: ResearchResult? = null,
        var promptPackage: PromptPackage? = null,
        var project: Project? = null,
        var error: String? = null
    )

    suspend fun runFullPipeline(
        brief: String,
        contentType: ContentType,
        platform: com.ncs.model.Platform,
        onProgress: (Stage, String) -> Unit
    ): Project {
        val projectId = java.util.UUID.randomUUID().toString()
        val state = RunState(projectId, brief = brief, contentType = contentType, platform = platform)

        try {
            // Stage 1: Research
            onProgress(Stage.RESEARCH, "Menganalisis trend & platform...")
            state.research = promptEngine.research(brief, contentType, platform)
            state.stage = Stage.PROMPT_BUILD

            // Stage 2: Build Prompt Package
            onProgress(Stage.PROMPT_BUILD, "Menyusun prompt visual, audio, lirik...")
            val prefs = memoryStore.getPreferences()
            state.promptPackage = promptEngine.buildPromptPackage(state.research!!, prefs)
            state.stage = Stage.VALIDATE

            // Stage 3: Validate Prompt
            onProgress(Stage.VALIDATE, "Memvalidasi prompt untuk realisme...")
            state.promptPackage = promptEngine.validatePrompt(state.promptPackage!!)
            state.stage = Stage.BUILD_ASSETS

            // Stage 4: Build Assets (Intermediary)
            onProgress(Stage.BUILD_ASSETS, "Generate background video, musik, sinkron lirik...")
            val buildResult = projectBuilder.build(state.research!!, state.promptPackage!!)
            state.project = buildResult.project
            state.stage = Stage.EDIT_RENDER

            // Stage 5: Edit & Render (Editor Agent)
            onProgress(Stage.EDIT_RENDER, "Rendering video final (FFmpeg 4K)...")
            val renderResult = editorEngine.renderFinal(state.project!!)
            
            if (!renderResult.success) throw Exception(renderResult.error ?: "Render failed")
            
            state.stage = Stage.COMPLETED
            onProgress(Stage.COMPLETED, "Selesai! Video siap di ${state.project!!.renderOutputPath}")
            return state.project!!

        } catch (e: Exception) {
            state.stage = Stage.FAILED
            state.error = e.message
            onProgress(Stage.FAILED, "Gagal: ${e.message}")
            throw e
        }
    }
}
