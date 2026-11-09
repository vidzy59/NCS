package com.ncs.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ncs.MainActivity
import com.ncs.R
import com.ncs.core.MemoryStore

@Composable
fun SettingsScreen(memoryStore: MemoryStore = (MainActivity.getContext() as MainActivity).memoryStore) {
    var geminiKey by remember { mutableStateOf("") }
    var groqKey by remember { mutableStateOf("") }
    var openRouterKey by remember { mutableStateOf("") }
    var hfToken by remember { mutableStateOf("") }
    var klingSession by remember { mutableStateOf("") }
    var maxResolution by remember { mutableStateOf(2) } // 0=FHD, 1=QHD, 2=UHD
    var maxFps by remember { mutableStateOf(60) }
    var hardwareEncoder by remember { mutableStateOf(true) }

    androidx.compose.material3.Scaffold(
        topBar = { TopAppBar(title = { Text("Settings" ) }, colors = TopAppBarDefaults.topAppBarColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainer)) }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            
            // API Keys Section
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("API Keys (Cloud Providers)", fontSize = 18.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    ApiKeyField(label = "Gemini 1.5 Flash", value = geminiKey, onChange = { geminiKey = it }, keyPrefs = "gemini_api_key")
                    ApiKeyField(label = "Groq (Llama-3.1-70B)", value = groqKey, onChange = { groqKey = it }, keyPrefs = "groq_api_key")
                    ApiKeyField(label = "OpenRouter (Free Models)", value = openRouterKey, onChange = { openRouterKey = it }, keyPrefs = "openrouter_api_key")
                    ApiKeyField(label = "Hugging Face Token", value = hfToken, onChange = { hfToken = it }, keyPrefs = "hf_token")
                    ApiKeyField(label = "Kling Session Cookie", value = klingSession, onChange = { klingSession = it }, keyPrefs = "kling_session")
                }
            }

            // Render Settings
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Render Settings", fontSize = 18.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    
                    androidx.compose.foundation.layout.Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        androidx.compose.material3.Text(text = "Max Resolusi", modifier = Modifier.weight(1f))
                        androidx.compose.material3.Text(text = when(maxResolution) { 0 -> "1080p"; 1 -> "1440p"; 2 -> "4K" }, color = androidx.compose.material3.MaterialTheme.colorScheme.primary)
                    }
                    androidx.compose.material3.Slider(value = maxResolution.toFloat(), onValueChange = { maxResolution = it.roundToInt() }, valueRange = 0f..2f, steps = 2)
                    
                    androidx.compose.foundation.layout.Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        androidx.compose.material3.Text(text = "Max FPS", modifier = Modifier.weight(1f))
                        androidx.compose.material3.Text(text = "$maxFps", color = androidx.compose.material3.MaterialTheme.colorScheme.primary)
                    }
                    androidx.compose.material3.Slider(value = maxFps.toFloat(), onValueChange = { maxFps = it.roundToInt() }, valueRange = 30f..60f, steps = 1)
                    
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Hardware Encoder (MediaCodec)")
                        Switch(checked = hardwareEncoder, onCheckedChange = { hardwareEncoder = it })
                    }
                }
            }

            // Save Button
            Button(onClick = { saveSettings() }, modifier = Modifier.fillMaxWidth()) {
                Text("Simpan Pengaturan", fontSize = 16.sp)
            }
        }
    }

    @Composable
    fun ApiKeyField(label: String, value: String, onChange: (String) -> Unit, keyPrefs: String) {
        androidx.compose.foundation.layout.Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = label, modifier = Modifier.weight(1f), fontSize = 14.sp)
            OutlinedTextField(
                value = value,
                onValueChange = onChange,
                modifier = Modifier.weight(2f),
                singleLine = true,
                visualTransformation = androidx.compose.material3.PasswordVisualTransformation()
            )
        }
    }

    fun saveSettings() {
        MainActivity.scope.launch {
            memoryStore.encryptedPrefs.edit().apply {
                putString("gemini_api_key", geminiKey)
                putString("groq_api_key", groqKey)
                putString("openrouter_api_key", openRouterKey)
                putString("hf_token", hfToken)
                putString("kling_session", klingSession)
                apply()
            }
            // TODO: Save render settings to preferences
        }
    }
}
