// FieldNotes — TranscriptionViewModel.kt
// Authored by: ui module | Implements: 08_UI_MODULE.md / 05_WHISPER_MODULE.md (issues 2 & 5)
package com.fieldnotes.app.ui.transcription

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fieldnotes.app.core.whisper.TranscriptionManager
import com.fieldnotes.app.data.db.labelList
import com.fieldnotes.app.data.repository.NoteDestination
import com.fieldnotes.app.data.repository.NoteRepository
import com.fieldnotes.app.data.repository.RecordingRepository
import com.fieldnotes.app.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * The transcription runs in the app-scoped [TranscriptionManager], so the screen stays interactive
 * while it works (pick a note + labels) and the job survives if the user leaves (issue 2). The note
 * can be saved to Drive or a local on-device folder (issue 5).
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
    val destination: NoteDestination = NoteDestination.DRIVE,
    val localFolderConfigured: Boolean = false,
    val localFolderName: String? = null,
    val error: String? = null,
) {
    val transcribing: Boolean get() = status == TranscriptionManager.Status.RUNNING
    val modelMissing: Boolean get() = status == TranscriptionManager.Status.MODEL_MISSING
    val failed: Boolean get() = status == TranscriptionManager.Status.FAILED
}

@HiltViewModel
class TranscriptionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val manager: TranscriptionManager,
    private val recordingRepository: RecordingRepository,
    private val noteRepository: NoteRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val recordingId: String = checkNotNull(savedStateHandle["recordingId"])

    private data class Meta(
        val loaded: Boolean = false,
        val notFound: Boolean = false,
        val audioFileName: String = "",
        val durationLabel: String = "",
        val driveNotes: List<String> = emptyList(),
        val localNotes: List<String> = emptyList(),
        val localFolderConfigured: Boolean = false,
        val localFolderName: String? = null,
    )

    private val _meta = MutableStateFlow(Meta())
    private val _labels = MutableStateFlow<List<String>>(emptyList())
    private val _editedText = MutableStateFlow<String?>(null)
    private val _destination = MutableStateFlow(NoteDestination.DRIVE)

    private val _navigateSaved = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** Emits when the screen should close: the note has been saved or auto-save was armed. */
    val navigateSaved: SharedFlow<Unit> = _navigateSaved.asSharedFlow()

    val allLabels: StateFlow<List<String>> = recordingRepository.allLabelsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private data class Choices(
        val meta: Meta,
        val labels: List<String>,
        val edited: String?,
        val destination: NoteDestination,
    )

    private val choices = combine(_meta, _labels, _editedText, _destination) { meta, labels, edited, dest ->
        Choices(meta, labels, edited, dest)
    }

    val uiState: StateFlow<TranscriptionUiState> =
        combine(manager.jobs, choices) { jobs, c ->
            val job = jobs[recordingId]
            val notes = if (c.destination == NoteDestination.LOCAL_FOLDER) c.meta.localNotes else c.meta.driveNotes
            TranscriptionUiState(
                loaded = c.meta.loaded,
                notFound = c.meta.notFound,
                audioFileName = c.meta.audioFileName,
                durationLabel = c.meta.durationLabel,
                status = job?.status ?: TranscriptionManager.Status.RUNNING,
                text = c.edited ?: job?.text ?: "",
                existingNotes = notes,
                labels = c.labels,
                destination = c.destination,
                localFolderConfigured = c.meta.localFolderConfigured,
                localFolderName = c.meta.localFolderName,
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
            val localUri = settingsRepository.localNotesFolderUri.first()
            val localConfigured = localUri != null
            val localNotes = if (localConfigured) noteRepository.listLocalFolderNotes() else emptyList()
            _meta.value = Meta(
                loaded = true,
                audioFileName = File(rec.filePath).name,
                durationLabel = formatDuration(rec.durationMs),
                driveNotes = noteRepository.listNoteFilenames(),
                localNotes = localNotes,
                localFolderConfigured = localConfigured,
                localFolderName = localUri?.let { android.net.Uri.parse(it).lastPathSegment },
            )
            // Default destination follows the user's preference, but only if a folder is set.
            if (localConfigured && settingsRepository.preferLocalNotes.first()) {
                _destination.value = NoteDestination.LOCAL_FOLDER
            }
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
    fun setDestination(destination: NoteDestination) { _destination.value = destination }

    /** Save the (possibly edited) text now. Used once transcription has finished. */
    fun saveNow(filename: String) {
        if (filename.isBlank()) return
        manager.saveNow(recordingId, filename, uiState.value.text, _labels.value, _destination.value)
    }

    /** Arm auto-append of the raw result and leave — no need to wait for transcription. */
    fun saveWhenReady(filename: String) {
        if (filename.isBlank()) return
        manager.armAutoSave(recordingId, filename, _labels.value, _destination.value)
        _navigateSaved.tryEmit(Unit)
    }

    private fun formatDuration(ms: Long): String {
        val totalSec = ms / 1000
        val m = totalSec / 60
        val s = totalSec % 60
        return if (m > 0) "${m}m ${s}s" else "${s}s"
    }
}
