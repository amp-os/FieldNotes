// FieldNotes — TranscriptionViewModel.kt
// Authored by: ui module | Implements: 08_UI_MODULE.md / 05_WHISPER_MODULE.md (issue 2)
package com.fieldnotes.app.ui.transcription

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fieldnotes.app.core.whisper.TranscriptionManager
import com.fieldnotes.app.data.db.labelList
import com.fieldnotes.app.data.repository.NoteRepository
import com.fieldnotes.app.data.repository.RecordingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * The transcription runs in the app-scoped [TranscriptionManager], so the screen stays interactive
 * while it works (pick a note + labels) and the job survives if the user leaves (issue 2).
 */
data class TranscriptionUiState(
    val loaded: Boolean = false,
    val notFound: Boolean = false,
    val audioFileName: String = "",
    val durationLabel: String = "",
    val status: TranscriptionManager.Status = TranscriptionManager.Status.RUNNING,
    val text: String = "",
    val existingNotes: List<String> = emptyList(),
    val labels: List<String> = emptyList(),
    val autoSaveArmed: Boolean = false,
    val error: String? = null,
) {
    val transcribing: Boolean get() = status == TranscriptionManager.Status.RUNNING
    val modelMissing: Boolean get() = status == TranscriptionManager.Status.MODEL_MISSING
    val failed: Boolean get() = status == TranscriptionManager.Status.FAILED
    /** True once the user can save real text (transcription finished, or they're typing manually). */
    val canEditText: Boolean get() = !transcribing
}

@HiltViewModel
class TranscriptionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val manager: TranscriptionManager,
    private val recordingRepository: RecordingRepository,
    private val noteRepository: NoteRepository,
) : ViewModel() {

    private val recordingId: String = checkNotNull(savedStateHandle["recordingId"])

    private data class Meta(
        val loaded: Boolean = false,
        val notFound: Boolean = false,
        val audioFileName: String = "",
        val durationLabel: String = "",
        val existingNotes: List<String> = emptyList(),
    )

    private val _meta = MutableStateFlow(Meta())
    private val _labels = MutableStateFlow<List<String>>(emptyList())
    private val _editedText = MutableStateFlow<String?>(null)
    private val _armed = MutableStateFlow(false)

    private val _navigateSaved = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** Emits when the screen should close: the note has been saved or auto-save was armed. */
    val navigateSaved: SharedFlow<Unit> = _navigateSaved.asSharedFlow()

    val allLabels: StateFlow<List<String>> = recordingRepository.allLabelsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val uiState: StateFlow<TranscriptionUiState> =
        combine(manager.jobs, _meta, _labels, _editedText, _armed) { jobs, meta, labels, edited, armed ->
            val job = jobs[recordingId]
            TranscriptionUiState(
                loaded = meta.loaded,
                notFound = meta.notFound,
                audioFileName = meta.audioFileName,
                durationLabel = meta.durationLabel,
                status = job?.status ?: TranscriptionManager.Status.RUNNING,
                text = edited ?: job?.text ?: "",
                existingNotes = meta.existingNotes,
                labels = labels,
                autoSaveArmed = armed,
                error = job?.error,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TranscriptionUiState())

    init {
        viewModelScope.launch {
            val rec = recordingRepository.getById(recordingId)
            if (rec == null) {
                _meta.value = Meta(loaded = true, notFound = true)
                return@launch
            }
            _labels.value = rec.labelList()
            _meta.value = Meta(
                loaded = true,
                audioFileName = File(rec.filePath).name,
                durationLabel = formatDuration(rec.durationMs),
                existingNotes = noteRepository.listNoteFilenames(),
            )
            manager.start(recordingId)
        }
        // A manual save completes asynchronously in the manager; close the screen when it lands.
        viewModelScope.launch {
            manager.jobs.collect { jobs ->
                if (jobs[recordingId]?.savedAs != null) {
                    manager.clear(recordingId)
                    _navigateSaved.emit(Unit)
                }
            }
        }
    }

    fun updateText(text: String) { _editedText.value = text }
    fun updateLabels(labels: List<String>) { _labels.value = labels }

    /** Save the (possibly edited) text now. Used once transcription has finished. */
    fun saveNow(filename: String) {
        if (filename.isBlank()) return
        manager.saveNow(recordingId, filename, uiState.value.text, _labels.value)
    }

    /** Arm auto-append of the raw result and leave — no need to wait for transcription. */
    fun saveWhenReady(filename: String) {
        if (filename.isBlank()) return
        _armed.value = true
        manager.armAutoSave(recordingId, filename, _labels.value)
        _navigateSaved.tryEmit(Unit)
    }

    private fun formatDuration(ms: Long): String {
        val totalSec = ms / 1000
        val m = totalSec / 60
        val s = totalSec % 60
        return if (m > 0) "${m}m ${s}s" else "${s}s"
    }
}
