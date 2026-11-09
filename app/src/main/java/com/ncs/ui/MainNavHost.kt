package com.ncs.ui

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navArgument
import androidx.navigation.compose.rememberNavController
import com.ncs.ui.screens.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainNavHost() {
    val navController = rememberNavController()
    val startDestination = remember { mutableStateOf("projects") }

    NavHost(navController, startDestination.value) {
        composable("projects") {
            ProjectListScreen(onNewProject = { startDestination.value = "editor" })
        }
        composable(
            route = "editor/{projectId}",
            arguments = listOf(navArgument("projectId") { type = androidx.navigation.NavType.StringType })
        ) { backStackEntry ->
            val projectId = backStackEntry.getString()!!
            EditorScreen(projectId = projectId, onBack = { navController.popBackStack() })
        }
        composable("assets") { AssetLibraryScreen() }
        composable("templates") { TemplateGalleryScreen() }
        composable("settings") { SettingsScreen() }
        composable("platform_setup") { PlatformSetupScreen() }
        composable("trends") { TrendDashboardScreen() }
    }
}
