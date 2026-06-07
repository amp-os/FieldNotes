// FieldNotes — TranscriptionViewModel.kt
// Authored by: ui module | Implements: 08_UI_MODULE.md / 05_WHISPER_MODULE.md
package com.fieldnotes.app.ui.transcription

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fieldnotes.app.data.repository.NoteRepository
import com.fieldnotes.app.data.repository.RecordingRepository
import com.fieldnotes.app.data.repository.TranscriptionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

sealed interface TranscriptionUiState {
    data object Transcribing : TranscriptionUiState
    data class Ready(
        val audioFileName: String,
        val durationLabel: String,
        val text: String,
        val existingNotes: List<String>,
        val labels: List<String> = emptyList(),
        val modelMissing: Boolean = false,
    ) : TranscriptionUiState
    data object Saving : TranscriptionUiState
    data class Error(val message: String) : TranscriptionUiState
}

@HiltViewModel
class TranscriptionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val transcriptionRepository: TranscriptionRepository,
    private val noteRepository: NoteRepository,
    private val recordingRepository: RecordingRepository,
) : ViewModel() {

    private val recordingId: String = checkNotNull(savedStateHandle["recordingId"])

    private val _uiState = MutableStateFlow<TranscriptionUiState>(TranscriptionUiState.Transcribing)
    val uiState: StateFlow<TranscriptionUiState> = _uiState.asStateFlow()

    init { transcribe() }

    private fun transcribe() {
        viewModelScope.launch {
            val recording = recordingRepository.getById(recordingId)
            if (recording == null) {
                _uiState.value = TranscriptionUiState.Error("Recording not found")
                return@launch
            }
            val audioName = File(recording.filePath).name
            val durationLabel = formatDuration(recording.durationMs)
            val notes = noteRepository.listNoteFilenames()

            if (!transcriptionRepository.isModelDownloaded()) {
                _uiState.value = TranscriptionUiState.Ready(
                    audioFileName = audioName,
                    durationLabel = durationLabel,
                    text = "",
                    existingNotes = notes,
                    modelMissing = true,
                )
                return@launch
            }

            val result = runCatching { transcriptionRepository.transcribeRecording(recordingId) }
            result.onSuccess { r ->
                _uiState.value = TranscriptionUiState.Ready(
                    audioFileName = audioName,
                    durationLabel = durationLabel,
                    text = r.text,
                    existingNotes = notes,
                )
            }.onFailure { e ->
                _uiState.value = TranscriptionUiState.Ready(
                    audioFileName = audioName,
                    durationLabel = durationLabel,
                    text = "",
                    existingNotes = notes,
                    modelMissing = true,
                )
            }
        }
    }

    fun updateText(text: String) {
        val s = _uiState.value
        if (s is TranscriptionUiState.Ready) _uiState.value = s.copy(text = text)
    }

    fun updateLabels(labels: List<String>) {
        val s = _uiState.value
        if (s is TranscriptionUiState.Ready) _uiState.value = s.copy(labels = labels)
    }

    fun save(filename: String, onSaved: () -> Unit) {
        val s = _uiState.value as? TranscriptionUiState.Ready ?: return
        if (filename.isBlank()) return
        _uiState.value = TranscriptionUiState.Saving
        viewModelScope.launch {
            runCatching {
                val savedName = noteRepository.saveTranscription(filename, s.text)
                recordingRepository.setNoteFilename(recordingId, savedName)
                if (s.labels.isNotEmpty()) recordingRepository.updateLabels(recordingId, s.labels)
            }.onSuccess { onSaved() }
                .onFailure { _uiState.value = s }
        }
    }

    private fun formatDuration(ms: Long): String {
        val totalSec = ms / 1000
        val m = totalSec / 60
        val s = totalSec % 60
        return if (m > 0) "${m}m ${s}s" else "${s}s"
    }
}
