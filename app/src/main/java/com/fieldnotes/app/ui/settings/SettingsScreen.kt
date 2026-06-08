// FieldNotes — SettingsScreen.kt
// Authored by: ui module | Implements: 08_UI_MODULE.md
@file:OptIn(ExperimentalMaterial3Api::class)

package com.fieldnotes.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fieldnotes.app.data.repository.SettingsRepository
import com.fieldnotes.app.core.whisper.WhisperModelManager

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val wifiOnly by viewModel.wifiOnly.collectAsStateWithLifecycle()
    val fieldFormat by viewModel.fieldFormat.collectAsStateWithLifecycle()
    val selectedModel by viewModel.selectedModel.collectAsStateWithLifecycle()
    val email by viewModel.connectedEmail.collectAsStateWithLifecycle()
    val pending by viewModel.pendingUploads.collectAsStateWithLifecycle()
    val storageUsed by viewModel.storageUsed.collectAsStateWithLifecycle()
    val modelProgress by viewModel.modelProgress.collectAsStateWithLifecycle()
    val authLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        viewModel.onAuthResult(result.data)
    }

    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionTitle("Google Drive")
        if (!viewModel.isDriveConfigured) {
            Text(
                "Drive sync is not configured in this build (no OAuth client id). " +
                    "Recordings still save locally and queue for upload.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (email == null) {
            Button(onClick = { viewModel.buildAuthIntent()?.let(authLauncher::launch) }) {
                Text("Connect Google Drive")
            }
        } else {
            Text("Connected: $email")
            OutlinedButton(onClick = viewModel::signOut) { Text("Disconnect") }
        }
        SettingRow("Sync over WiFi only") {
            Switch(checked = wifiOnly, onCheckedChange = viewModel::setWifiOnly)
        }
        Text("Pending uploads: $pending")
        OutlinedButton(onClick = viewModel::syncNow) { Text("Sync now") }

        HorizontalDivider()
        SectionTitle("Audio")
        SettingRow("Field recording format") {
            Dropdown(
                current = fieldFormat,
                options = listOf(SettingsRepository.FORMAT_FLAC, SettingsRepository.FORMAT_WAV),
                onSelect = viewModel::setFieldFormat,
            )
        }
        SettingRow("Transcription model") {
            Dropdown(
                current = if (selectedModel == WhisperModelManager.SMALL_MODEL) "Small (466MB)" else "Base (142MB)",
                options = listOf("Base (142MB)", "Small (466MB)"),
                onSelect = {
                    viewModel.setModel(if (it.startsWith("Small")) WhisperModelManager.SMALL_MODEL else WhisperModelManager.BASE_MODEL)
                },
            )
        }
        if (modelProgress != null) {
            LinearProgressIndicator(progress = { modelProgress ?: 0f }, modifier = Modifier.fillMaxWidth())
        } else {
            OutlinedButton(onClick = { viewModel.downloadModel() }) {
                Text(if (viewModel.baseModelDownloaded) "Re-download base model" else "Download base model")
            }
        }

        HorizontalDivider()
        SectionTitle("Storage")
        Text("Used: ${formatSize(storageUsed)}")
        OutlinedButton(onClick = viewModel::clearTempFiles) { Text("Clear temp files") }

        HorizontalDivider()
        SectionTitle("About")
        Text("Version 1.0")
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun SettingRow(label: String, control: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label)
        control()
    }
}

@Composable
private fun Dropdown(current: String, options: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text(current)
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = { onSelect(option); expanded = false })
            }
        }
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1_000_000 -> "%.0f MB".format(bytes / 1_000_000.0)
    bytes >= 1_000 -> "%.0f KB".format(bytes / 1_000.0)
    else -> "$bytes B"
}
