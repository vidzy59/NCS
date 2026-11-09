package com.ncs.core

import com.ncs.model.LyricsSpec
import com.ncs.model.Project
import com.ncs.render.FfmpegRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

class EditorEngine(
    private val ffmpeg: FfmpegRenderer,
    private val memoryStore: MemoryStore,
    private val scope: CoroutineScope
) {

    private val json = Json { ignoreUnknownKeys = true }

    data class RenderResult(
        val outputFile: File,
        val durationSec: Double,
        val loudnessLUFS: Double,
        val success: Boolean,
        val error: String?
    )

    suspend fun renderFinal(project: Project): RenderResult = withContext(Dispatchers.IO) {
        try {
            val projectData = json.decodeFromString<Map<String, Any>>(project.projectJson!!)
            val visual = projectData["visual"] as Map<String, Any>
            val scenes = visual["scenes"] as List<*>
            val audio = projectData["audio"] as Map<String, Any>
            val lyrics = projectData["lyrics"] as? List<*>
            val export = projectData["export"] as Map<String, Any>
            val preset = export["preset"] as String

            // 1. Prepare background clips (concat or single)
            val bgClips = scenes.map { (it as Map<String, Any>)["assetPath"] as String }.map { File(it) }
            val bgInput = if (bgClips.size == 1) bgClips[0] else ffmpeg.concatClips(bgClips)

            // 2. Prepare audio
            val audioFile = (audio["filePath"] as? String)?.let { File(it) }

            // 3. Generate ASS subtitle if lyrics exist
            val assFile = lyrics?.let { generateAssSubtitle(it, project) }

            // 4. Build FFmpeg filter graph
            val filterGraph = buildFilterGraph(assFile, project)

            // 5. Render
            val outputFile = File(project.outputDir, "${project.id}_final.mp4")
            val result = ffmpeg.render(
                videoInput = bgInput,
                audioInput = audioFile,
                filterGraph = filterGraph,
                outputFile = outputFile,
                preset = preset
            )

            // 6. QC Check
            val qc = ffmpeg.qualityCheck(outputFile)

            // 7. Update project
            val updated = project.copy(
                status = if (result.success) com.ncs.model.ProjectStatus.COMPLETED else com.ncs.model.ProjectStatus.FAILED,
                renderOutputPath = outputFile.absolutePath
            )
            memoryStore.saveProject(updated)

            RenderResult(outputFile, qc.duration, qc.loudnessLUFS, result.success, result.error)
        } catch (e: Exception) {
            val updated = project.copy(status = com.ncs.model.ProjectStatus.FAILED)
            memoryStore.saveProject(updated)
            RenderResult(File(""), 0.0, 0.0, false, e.message)
        }
    }

    private fun generateAssSubtitle(lyrics: List<Any>, project: Project): File {
        val lines = lyrics.map { (it as Map<String, Any>) }
            .map { TimedLine((it["start"] as Number).toDouble(), (it["end"] as Number).toDouble(), it["text"] as String) }
            .sortedBy { it.start }

        val assContent = StringBuilder()
        assContent.appendLine("[Script Info]")
        assContent.appendLine("Title: NCS Lyrics")
        assContent.appendLine("ScriptType: v4.00+")
        assContent.appendLine("WrapStyle: 0")
        assContent.appendLine("ScaledBorderAndShadow: yes")
        assContent.appendLine("YCbCr Matrix: TV.709")
        assContent.appendLine("")
        assContent.appendLine("[V4+ Styles]")
        assContent.appendLine("Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding")
        assContent.appendLine("Style: Default,Inter Bold,72,&H00FFFFFF,&H000000FF,&H00000000,&H80000000,0,0,0,0,100,100,0,0,1,2,2,2,10,10,120,1")
        assContent.appendLine("")
        assContent.appendLine("[Events]")
        assContent.appendLine("Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text")

        for (line in lines) {
            val start = formatAssTime(line.start)
            val end = formatAssTime(line.end)
            val text = escapeAss(line.text)
            assContent.appendLine("Dialogue: 0,$start,$end,Default,,0,0,0,,${text}")
        }

        val file = File(project.outputDir, "${project.id}_lyrics.ass")
        file.writeText(assContent.toString())
        return file
    }

    private fun buildFilterGraph(assFile: File?, project: Project): String {
        val filters = mutableListOf<String>()
        
        // Video filters: color grade, grain, etc.
        filters.add("format=pix_fmts=yuv420p10le")
        
        // Subtitle overlay
        assFile?.let {
            filters.add("subtitles='${it.absolutePath.replace("\\", "\\\\")}':fontsdir='${getFontsDir()}'")
        }

        return filters.joinToString(",")
    }

    private fun formatAssTime(seconds: Double): String {
        val h = (seconds / 3600).toInt()
        val m = ((seconds % 3600) / 60).toInt()
        val s = (seconds % 60).toInt()
        val cs = ((seconds * 100) % 100).toInt()
        return String.format("%d:%02d:%02d.%02d", h, m, s, cs)
    }

    private fun escapeAss(text: String): String = text
        .replace("\\", "\\\\")
        .replace("{", "\\{")
        .replace("}", "\\}")
        .replace("\n", "\\N")

    private fun getFontsDir(): String = "/system/fonts" // fallback

    private data class TimedLine(val start: Double, val end: Double, val text: String)
}
