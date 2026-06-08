// FieldNotes — AppChromeViewModel.kt
// Authored by: ui module | Implements: 08_UI_MODULE.md (background-transcription banner)
package com.fieldnotes.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fieldnotes.app.core.whisper.TranscriptionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** App-level chrome state: surfaces an in-progress background transcription for the return banner. */
@HiltViewModel
class AppChromeViewModel @Inject constructor(
    manager: TranscriptionManager,
) : ViewModel() {

    data class BannerJob(val recordingId: String, val done: Boolean)

    /**
     * The transcription to advertise in the banner: still running, or finished but not yet saved.
     * Saved jobs are removed from the map, so they don't appear here.
     */
    val backgroundTranscription: StateFlow<BannerJob?> = manager.jobs
        .map { jobs ->
            jobs.values.firstOrNull {
                it.status == TranscriptionManager.Status.RUNNING ||
                    it.status == TranscriptionManager.Status.DONE
            }?.let { BannerJob(it.recordingId, it.status == TranscriptionManager.Status.DONE) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
