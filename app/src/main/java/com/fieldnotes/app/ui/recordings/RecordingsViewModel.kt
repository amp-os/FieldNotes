// FieldNotes — RecordingsViewModel.kt
// Authored by: ui module | Implements: 08_UI_MODULE.md
package com.fieldnotes.app.ui.recordings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fieldnotes.app.core.sync.DriveAuthManager
import com.fieldnotes.app.data.db.NoteEntity
import com.fieldnotes.app.data.db.RecordingEntity
import com.fieldnotes.app.data.db.labelList
import com.fieldnotes.app.data.repository.NoteRepository
import com.fieldnotes.app.data.repository.RecordingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RecordingsViewModel @Inject constructor(
    private val recordingRepository: RecordingRepository,
    noteRepository: NoteRepository,
    driveAuthManager: DriveAuthManager,
) : ViewModel() {

    val recordings: StateFlow<List<RecordingEntity>> = recordingRepository.getAllRecordings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Selected label filter for the recordings tab; null = show all. */
    private val _selectedLabel = MutableStateFlow<String?>(null)
    val selectedLabel: StateFlow<String?> = _selectedLabel.asStateFlow()

    /** All distinct labels across recordings, for the filter chip row. */
    val allLabels: StateFlow<List<String>> = recordings
        .map { list -> list.flatMap { it.labelList() }.distinct().sorted() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Recordings after applying [selectedLabel]. */
    val filteredRecordings: StateFlow<List<RecordingEntity>> =
        combine(recordings, _selectedLabel) { list, label ->
            if (label == null) list else list.filter { label in it.labelList() }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setLabelFilter(label: String?) { _selectedLabel.value = label }

    val notes: StateFlow<List<NoteEntity>> = noteRepository.getAllNotes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** True when the sync-not-configured banner should be shown. */
    val showSyncBanner: StateFlow<Boolean> =
        driveAuthManager.isAuthenticated()
            .combine(kotlinx.coroutines.flow.flowOf(driveAuthManager.isConfigured)) { authed, configured ->
                !configured || !authed
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun delete(id: String) {
        viewModelScope.launch { recordingRepository.delete(id) }
    }
}
