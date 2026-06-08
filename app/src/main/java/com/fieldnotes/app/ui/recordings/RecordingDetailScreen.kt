// FieldNotes — RecordingDetailScreen.kt
// Authored by: ui module | Implements: 08_UI_MODULE.md (RecordingDetailScreen)
// Serves two spec user stories for FIELD recordings: "see duration/size after stopping" (shown on
// arrival here post-stop) and "name/label a recording after capture" (label editor below).
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.fieldnotes.app.ui.recordings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fieldnotes.app.core.audio.RecordingMode
import com.fieldnotes.app.core.sync.SyncStatus
import com.fieldnotes.app.data.db.RecordingEntity
import com.fieldnotes.app.data.db.labelList
import com.fieldnotes.app.data.repository.RecordingRepository
import com.fieldnotes.app.ui.common.AudioPlayer
import com.fieldnotes.app.ui.common.FieldRed
import com.fieldnotes.app.ui.common.LabelEditor
import com.fieldnotes.app.ui.common.SyncStatusIcon
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class RecordingDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val recordingRepository: RecordingRepository,
) : ViewModel() {

    private val recordingId: String = checkNotNull(savedStateHandle["recordingId"])

    private val _recording = MutableStateFlow<RecordingEntity?>(null)
    val recording: StateFlow<RecordingEntity?> = _recording.asStateFlow()

    /** All existing labels, for one-touch suggestions. */
    val allLabels: StateFlow<List<String>> = recordingRepository.allLabelsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init { reload() }

    private fun reload() {
        viewModelScope.launch { _recording.value = recordingRepository.getById(recordingId) }
    }

    fun addLabel(label: String) {
        val rec = _recording.value ?: return
        val trimmed = label.trim()
        if (trimmed.isEmpty() || trimmed in rec.labelList()) return
        viewModelScope.launch {
            recordingRepository.updateLabels(recordingId, rec.labelList() + trimmed)
            reload()
        }
    }

    fun removeLabel(label: String) {
        val rec = _recording.value ?: return
        viewModelScope.launch {
            recordingRepository.updateLabels(recordingId, rec.labelList() - label)
            reload()
        }
    }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            recordingRepository.delete(recordingId)
            onDeleted()
        }
    }
}

@Composable
fun RecordingDetailScreen(
    onBack: () -> Unit,
    onTranscribe: (String) -> Unit,
    viewModel: RecordingDetailViewModel = hiltViewModel(),
) {
    val recording by viewModel.recording.collectAsStateWithLifecycle()
    val allLabels by viewModel.allLabels.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recording") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        val rec = recording
        if (rec == null) {
            Text("Loading…", modifier = Modifier.padding(padding).padding(16.dp))
            return@Scaffold
        }
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(File(rec.filePath).name, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.titleMedium)
            val audioFile = File(rec.filePath)
            if (audioFile.exists()) {
                AudioPlayer(audioFile)
            }
            Button(onClick = { onTranscribe(rec.id) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.AutoMirrored.Filled.Notes, contentDescription = null)
                Text(if (rec.noteFilename != null) "  Transcribe again" else "  Transcribe")
            }
            val isField = rec.mode == RecordingMode.FIELD.name
            InfoRow("Type", if (isField) "Field recording" else "Voice note")
            InfoRow("Recorded", formatDate(rec.createdAt))
            InfoRow("Duration", formatDuration(rec.durationMs))
            InfoRow("Size", formatSize(rec.fileSizeBytes))
            InfoRow("Sample rate", "${rec.sampleRate} Hz")
            Column {
                Text("Sync", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                SyncStatusIcon(SyncStatus.fromString(rec.syncStatus))
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("Labels", style = MaterialTheme.typography.titleSmall)
            LabelEditor(
                labels = rec.labelList(),
                suggestions = allLabels,
                onAdd = viewModel::addLabel,
                onRemove = viewModel::removeLabel,
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Button(
                onClick = { viewModel.delete(onBack) },
                colors = ButtonDefaults.buttonColors(containerColor = FieldRed),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Delete, contentDescription = null)
                Text("  Delete recording")
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        Text(value)
    }
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
