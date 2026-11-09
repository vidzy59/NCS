package com.ncs.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ncs.R

@Composable
fun PlatformSetupScreen() {
    androidx.compose.material3.Scaffold(
        topBar = { TopAppBar(title = { Text("Platform Intelligence" ) }, colors = TopAppBarDefaults.topAppBarColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainer)) }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Koneksi Platform untuk Data Trend", fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, modifier = Modifier.padding(16.dp))
            
            PlatformCard(
                name = "Facebook Reels",
                icon = R.drawable.ic_facebook,
                connected = false,
                features = listOf("Meta Sound Collection", "Remix", "Broadcast Channel", "Group Share"),
                onConnect = { /* OAuth Meta */ }
            )
            PlatformCard(
                name = "Instagram Reels",
                icon = R.drawable.ic_instagram,
                connected = false,
                features = listOf("Meta Sound Collection", "Remix", "Collab", "Broadcast Channel"),
                onConnect = { /* OAuth Meta */ }
            )
            PlatformCard(
                name = "YouTube Shorts",
                icon = R.drawable.ic_youtube,
                connected = false,
                features = listOf("Content ID Safe Audio", "Remix Long-form", "SEO Metadata"),
                onConnect = { /* OAuth YouTube */ }
            )
            PlatformCard(
                name = "TikTok",
                icon = R.drawable.ic_tiktok,
                connected = false,
                features = listOf("Trending Sounds", "Duet/Stitch", "Green Screen", "Hashtag Challenge"),
                onConnect = { /* Apify Token */ }
            )
            PlatformCard(
                name = "X (Twitter)",
                icon = R.drawable.ic_x,
                connected = false,
                features = listOf("Thread Reply Video", "Premium Boost"),
                onConnect = { /* OAuth X */ }
            )
        }
    }
}

@Composable
fun PlatformCard(
    name: String,
    icon: Int,
    connected: Boolean,
    features: List<String>,
    onConnect: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth().padding(16.dp), elevation = 2.dp) {
        Column(modifier = Modifier.padding(16.dp)) {
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(painterResource(icon), "Icon", modifier = Modifier.size(40.dp))
                    Text(text = name, fontSize = 18.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }
                if (connected) {
                    androidx.compose.material3.Chip(onClick = { }, colors = androidx.compose.material3.ChipDefaults.colors(containerColor = Color.Green.copy(alpha = 0.2f))) {
                        Text("Terhubung", color = Color.Green)
                    }
                } else {
                    Button(onClick = onConnect) { Text("Hubungkan") }
                }
            }
            androidx.compose.foundation.layout.Column(modifier = Modifier.padding(top = 12.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    features.forEach { f ->
                        androidx.compose.material3.Chip(onClick = { }, colors = androidx.compose.material3.ChipDefaults.colors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHighest)) {
                            Text(f, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}
