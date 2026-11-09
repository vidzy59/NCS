package com.ncs

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.ncs.ui.MainNavHost
import com.ncs.core.MemoryStore
import com.ncs.core.AgentOrchestrator
import com.ncs.core.PromptEngine
import com.ncs.core.ProjectBuilder
import com.ncs.core.EditorEngine
import com.ncs.network.ProviderRouter
import com.ncs.render.FfmpegRenderer
import com.ncs.core.intel.PlatformIntelRegistry
import com.ncs.core.intel.MetaPlatformIntel
import com.ncs.core.intel.YouTubePlatformIntel
import com.ncs.core.intel.TikTokPlatformIntel
import com.ncs.core.intel.XPlatformIntel
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class MainActivity : ComponentActivity() {

    companion object {
        lateinit var memoryStore: MemoryStore
        lateinit var scope: CoroutineScope
        lateinit var orchestrator: AgentOrchestrator
        lateinit var providerRouter: ProviderRouter
        lateinit var context: android.content.Context
            private set
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        context = this

        // Initialize core components
        memoryStore = MemoryStore(this)
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        val okHttp = OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY))
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        val ffmpeg = FfmpegRenderer(this, scope)
        providerRouter = ProviderRouter(okHttp, memoryStore, scope)

        val intelRegistry = PlatformIntelRegistry(
            MetaPlatformIntel(okHttp, memoryStore),
            YouTubePlatformIntel(okHttp, memoryStore),
            TikTokPlatformIntel(okHttp, memoryStore),
            XPlatformIntel(okHttp, memoryStore)
        )

        val promptEngine = PromptEngine(providerRouter, intelRegistry, memoryStore, scope)
        val projectBuilder = ProjectBuilder(providerRouter, memoryStore, scope)
        val editorEngine = EditorEngine(ffmpeg, memoryStore, scope)

        orchestrator = AgentOrchestrator(promptEngine, projectBuilder, editorEngine, memoryStore, scope)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainNavHost()
                }
            }
        }
    }

    override fun onDestroy() {
        scope.coroutineContext[SupervisorJob]?.cancel()
        super.onDestroy()
    }
}
