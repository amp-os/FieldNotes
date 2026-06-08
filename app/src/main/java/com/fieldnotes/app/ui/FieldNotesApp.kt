// FieldNotes — FieldNotesApp.kt
// Authored by: ui module | Implements: 08_UI_MODULE.md (navigation + bottom bar)
package com.fieldnotes.app.ui

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fieldnotes.app.ui.common.TranscriptionBanner
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.fieldnotes.app.ui.notes.NoteViewScreen
import com.fieldnotes.app.ui.recorder.RecorderScreen
import com.fieldnotes.app.ui.recordings.LibraryScreen
import com.fieldnotes.app.ui.recordings.RecordingDetailScreen
import com.fieldnotes.app.ui.settings.SettingsScreen
import com.fieldnotes.app.ui.transcription.TranscriptionScreen

private sealed class TopDest(val route: String, val label: String, val icon: ImageVector) {
    data object Recorder : TopDest("recorder", "Record", Icons.Default.Mic)
    data object Library : TopDest("library", "Library", Icons.AutoMirrored.Filled.LibraryBooks)
    data object Settings : TopDest("settings", "Settings", Icons.Default.Settings)
}

private val topDestinations = listOf(TopDest.Recorder, TopDest.Library, TopDest.Settings)

@Composable
fun FieldNotesApp(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val showBottomBar = currentRoute in topDestinations.map { it.route }

    val chromeViewModel: AppChromeViewModel = hiltViewModel()
    val backgroundTranscription by chromeViewModel.backgroundTranscription.collectAsStateWithLifecycle()

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    val current = backStackEntry?.destination
                    topDestinations.forEach { dest ->
                        NavigationBarItem(
                            selected = current?.hierarchy?.any { it.route == dest.route } == true,
                            onClick = {
                                navController.navigate(dest.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(dest.icon, contentDescription = dest.label) },
                            label = { Text(dest.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            // Show a tappable banner for a transcription running/finished in the background, unless
            // we're already on its screen.
            val onTranscriptionRoute = currentRoute?.startsWith("transcription") == true
            val active = backgroundTranscription
            if (active != null && !onTranscriptionRoute) {
                TranscriptionBanner(
                    done = active.done,
                    onClick = { navController.navigate("transcription/${active.recordingId}") },
                )
            }
            NavHost(
                navController = navController,
                startDestination = TopDest.Recorder.route,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
            composable(TopDest.Recorder.route) {
                RecorderScreen(
                    onNavigateToTranscription = { id -> navController.navigate("transcription/$id") },
                    onNavigateToRecordingDetail = { id -> navController.navigate("recordingDetail/$id") },
                )
            }
            composable(TopDest.Library.route) {
                LibraryScreen(
                    onOpenNote = { filename -> navController.navigate("note/${Uri.encode(filename)}") },
                    onOpenRecording = { id -> navController.navigate("recordingDetail/$id") },
                    onSetupSync = { navController.navigate(TopDest.Settings.route) },
                )
            }
            composable(TopDest.Settings.route) { SettingsScreen() }
            composable(
                route = "transcription/{recordingId}",
                arguments = listOf(navArgument("recordingId") { type = NavType.StringType }),
            ) {
                TranscriptionScreen(
                    onSaved = { navController.popBackStack() },
                    onDiscarded = { navController.popBackStack() },
                )
            }
            composable(
                route = "recordingDetail/{recordingId}",
                arguments = listOf(navArgument("recordingId") { type = NavType.StringType }),
            ) {
                RecordingDetailScreen(
                    onBack = { navController.popBackStack() },
                    onTranscribe = { id -> navController.navigate("transcription/$id") },
                )
            }
            composable(
                route = "note/{filename}",
                arguments = listOf(navArgument("filename") { type = NavType.StringType }),
            ) {
                NoteViewScreen(onBack = { navController.popBackStack() })
            }
            }
        }
    }
}
