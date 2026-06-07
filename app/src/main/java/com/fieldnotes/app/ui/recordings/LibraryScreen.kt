// FieldNotes — LibraryScreen.kt
// Authored by: ui module | Implements: 08_UI_MODULE.md (Library: Recordings + Notes tabs)
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.fieldnotes.app.ui.recordings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fieldnotes.app.core.audio.RecordingMode
import com.fieldnotes.app.core.sync.SyncStatus
import com.fieldnotes.app.data.db.RecordingEntity
import com.fieldnotes.app.data.db.labelList
import com.fieldnotes.app.ui.common.FieldAmber
import com.fieldnotes.app.ui.common.SyncStatusIcon
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LibraryScreen(
    onOpenNote: (String) -> Unit,
    onSetupSync: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecordingsViewModel = hiltViewModel(),
) {
    val recordings by viewModel.recordings.collectAsStateWithLifecycle()
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val showBanner by viewModel.showSyncBanner.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }

    Column(modifier.fillMaxSize()) {
        if (showBanner) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onSetupSync),
            ) {
                Text(
                    "Sync not configured — tap to set up Google Drive.",
                    modifier = Modifier.padding(12.dp),
                    color = FieldAmber,
                )
            }
        }
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Recordings") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Notes") })
        }
        when (tab) {
            0 -> RecordingsList(recordings, onDelete = viewModel::delete)
            else -> NotesList(notes.map { it.filename }, onOpenNote = onOpenNote)
        }
    }
}

@Composable
private fun RecordingsList(recordings: List<RecordingEntity>, onDelete: (String) -> Unit) {
    if (recordings.isEmpty()) {
        EmptyState("No recordings yet")
        return
    }
    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(recordings, key = { it.id }) { rec ->
            RecordingCard(rec, onLongPress = { onDelete(rec.id) })
        }
    }
}

@Composable
private fun RecordingCard(rec: RecordingEntity, onLongPress: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().combinedClickable(onClick = {}, onLongClick = onLongPress),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val isField = rec.mode == RecordingMode.FIELD.name
            Icon(
                if (isField) Icons.Default.Mic else Icons.AutoMirrored.Filled.Notes,
                contentDescription = if (isField) "Field recording" else "Voice note",
                tint = FieldAmber,
            )
            Column(Modifier.weight(1f)) {
                Text(java.io.File(rec.filePath).name, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "${formatDate(rec.createdAt)} · ${formatDuration(rec.durationMs)} · ${formatSize(rec.fileSizeBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val labels = rec.labelList()
                if (labels.isNotEmpty()) {
                    Text(labels.joinToString("  ") { "#$it" }, style = MaterialTheme.typography.bodySmall, color = FieldAmber)
                }
            }
            SyncStatusIcon(SyncStatus.fromString(rec.syncStatus))
        }
    }
}

@Composable
private fun NotesList(filenames: List<String>, onOpenNote: (String) -> Unit) {
    if (filenames.isEmpty()) {
        EmptyState("No notes yet")
        return
    }
    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(filenames, key = { it }) { name ->
            Card(Modifier.fillMaxWidth().combinedClickable(onClick = { onOpenNote(name) })) {
                Text(name, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(16.dp))
            }
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

private fun formatDate(ts: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(ts))

private fun formatDuration(ms: Long): String {
    val s = ms / 1000
    return if (s >= 60) "${s / 60}m ${s % 60}s" else "${s}s"
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
    bytes >= 1_000 -> "%.0f KB".format(bytes / 1_000.0)
    else -> "$bytes B"
}
