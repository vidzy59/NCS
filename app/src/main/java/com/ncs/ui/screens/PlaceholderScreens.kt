package com.ncs.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AssetLibraryScreen() {
    androidx.compose.material3.Scaffold(
        topBar = { TopAppBar(title = { Text("Asset Library" ) }, colors = TopAppBarDefaults.topAppBarColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainer)) }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Text(text = "Asset Library - Coming Soon", fontSize = 18.sp, modifier = Modifier.fillMaxSize().wrapContentSize(Alignment.Center))
        }
    }
}

@Composable
fun TemplateGalleryScreen() {
    androidx.compose.material3.Scaffold(
        topBar = { TopAppBar(title = { Text("Templates" ) }, colors = TopAppBarDefaults.topAppBarColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainer)) }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Text(text = "Template Gallery - Coming Soon", fontSize = 18.sp, modifier = Modifier.fillMaxSize().wrapContentSize(Alignment.Center))
        }
    }
}

@Composable
fun TrendDashboardScreen() {
    androidx.compose.material3.Scaffold(
        topBar = { TopAppBar(title = { Text("Trend Dashboard" ) }, colors = TopAppBarDefaults.topAppBarColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainer)) }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Text(text = "Trend Dashboard - Coming Soon", fontSize = 18.sp, modifier = Modifier.fillMaxSize().wrapContentSize(Alignment.Center))
        }
    }
}
