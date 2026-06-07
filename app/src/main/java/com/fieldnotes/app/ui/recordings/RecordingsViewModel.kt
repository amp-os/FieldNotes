// FieldNotes — RecordingsViewModel.kt
// Authored by: ui module | Implements: 08_UI_MODULE.md
package com.fieldnotes.app.ui.recordings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fieldnotes.app.core.sync.DriveAuthManager
import com.fieldnotes.app.data.db.NoteEntity
import com.fieldnotes.app.data.db.RecordingEntity
import com.fieldnotes.app.data.repository.NoteRepository
import com.fieldnotes.app.data.repository.RecordingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
