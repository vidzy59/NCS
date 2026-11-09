package com.ncs.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ncs.MainActivity
import com.ncs.R
import com.ncs.core.AgentOrchestrator
import com.ncs.core.MemoryStore
import com.ncs.model.ContentType
import com.ncs.model.Project
import kotlinx.coroutines.launch

@Composable
fun EditorScreen(
    projectId: String,
    onBack: () -> Unit,
    memoryStore: MemoryStore = (MainActivity.getContext() as MainActivity).memoryStore,
    orchestrator: AgentOrchestrator = (MainActivity.getContext() as MainActivity).orchestrator
) {
    var project by remember { mutableStateOf<Project?>(null) }
    var stage by remember { mutableStateOf(AgentOrchestrator.Stage.RESEARCH) }
    var progressText by remember { mutableStateOf("Memulai...") }
    var progressPercent by remember { mutableStateOf(0) }
    var isRunning by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // Load project
    androidx.compose.runtime.LaunchedEffect(Unit) {
        project = memoryStore.getProject(projectId)
    }

    fun startPipeline(brief: String, contentType: ContentType, platform: com.ncs.model.Platform) {
        isRunning = true
        error = null
        MainActivity.scope.launch {
            try {
                val result = orchestrator.runFullPipeline(brief, contentType, platform) { st, msg ->
                    stage = st
                    progressText = msg
                    progressPercent = when (st) {
                        AgentOrchestrator.Stage.RESEARCH -> 10
                        AgentOrchestrator.Stage.PROMPT_BUILD -> 25
                        AgentOrchestrator.Stage.VALIDATE -> 35
                        AgentOrchestrator.Stage.BUILD_ASSETS -> 55
                        AgentOrchestrator.Stage.EDIT_RENDER -> 80
                        AgentOrchestrator.Stage.COMPLETED -> 100
                        else -> 0
                    }
                }
                // Refresh project
                project = memoryStore.getProject(projectId)
            } catch (e: Exception) {
                error = e.message
            } finally {
                isRunning = false
            }
        }
    }

    androidx.compose.material3.Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(project?.title ?: "Editor", fontSize = 18.sp) },
                navigationIcon = { Icon(painterResource(R.drawable.ic_arrow_back), "Back", onClick = onBack) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainer)
            )
        },
        floatingActionButton = if (!isRunning) {
            ExtendedFloatingActionButton(
                onClick = { /* show brief dialog */ },
                icon = { Icon(painterResource(R.drawable.ic_play_arrow), "Generate") },
                text = { Text("Generate Video") }
            )
        } else null
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Progress
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = progressText, fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        Text(text = "$progressPercent%", fontSize = 14.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.primary)
                    }
                    LinearProgressIndicator(progress = progressPercent / 100f, modifier = Modifier.padding(top = 8.dp).fillMaxWidth())
                    
                    // Stage indicator
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AgentOrchestrator.Stage.values().forEach { st ->
                            val isActive = st.ordinal <= stage.ordinal
                            val isCurrent = st == stage
                            androidx.compose.material3.Chip(
                                onClick = { },
                                modifier = Modifier.weight(1f),
                                colors = androidx.compose.material3.ChipDefaults.colors(
                                    containerColor = if (isActive) androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer 
                                        else androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHighest
                                )
                            ) {
                                Text(text = st.name, fontSize = 10.sp, color = if (isActive) androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer else androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            // Error display
            error?.let { err ->
                Card(colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.errorContainer)) {
                    Text(text = "Error: $err", modifier = Modifier.padding(16.dp), color = androidx.compose.material3.MaterialTheme.colorScheme.onErrorContainer)
                }
            }

            // Project info
            project?.let { p ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = "Platform: ${p.platform.displayName} (${p.platform.aspectRatio}, ${p.platform.maxDurationSec}s)", fontSize = 14.sp)
                        Text(text = "Tipe: ${p.contentType.name}", fontSize = 14.sp)
                        Text(text = "Brief: ${p.brief}", fontSize = 14.sp, maxLines = 3, overflow = androidx.compose.ui.text.overflow.Ellipsis)
                    }
                }
            }

            // Output video preview (if completed)
            if (stage == AgentOrchestrator.Stage.COMPLETED && project?.renderOutputPath != null) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = "Video Selesai!", fontSize = 18.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = androidx.compose.material3.MaterialTheme.colorScheme.primary)
                        Text(text = "Path: ${project?.renderOutputPath}", fontSize = 12.sp, maxLines = 2, overflow = androidx.compose.ui.text.overflow.Ellipsis)
                        androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 16.dp)) {
                            Button(onClick = { /* share */ }) { Text("Bagikan") }
                            Button(onClick = { /* save template */ }) { Text("Simpan Template") }
                            Button(onClick = { /* rate */ }) { Text("Rating ⭐") }
                        }
                    }
                }
            }
        }
    }
}
