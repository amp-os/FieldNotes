// FieldNotes — RecorderViewModel.kt
// Authored by: ui module | Implements: 08_UI_MODULE.md / 04_AUDIO_MODULE.md
package com.fieldnotes.app.ui.recorder

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fieldnotes.app.core.audio.AudioInputRouter
import com.fieldnotes.app.core.audio.CompletedRecording
import com.fieldnotes.app.core.audio.RecordingSession
import com.fieldnotes.app.core.audio.RecordingSessionManager
import com.fieldnotes.app.data.repository.SettingsRepository
import com.fieldnotes.app.service.RecordingService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RecorderViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    sessionManager: RecordingSessionManager,
    private val audioInputRouter: AudioInputRouter,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val session: StateFlow<RecordingSession?> = sessionManager.session
    val amplitude: StateFlow<Float> = sessionManager.amplitude

    /** One-shot completion events; the UI navigates to transcription for voice notes. */
    val completed: SharedFlow<CompletedRecording> = sessionManager.completed

    val headsetConnected: StateFlow<Boolean> = audioInputRouter.headsetMicConnected()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val selectedAudioSource: StateFlow<Int> = settingsRepository.audioSourceFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), android.media.MediaRecorder.AudioSource.MIC)

    fun availableSources(): List<Pair<Int, String>> = audioInputRouter.availableSources()

    fun selectAudioSource(source: Int) {
        viewModelScope.launch { settingsRepository.setAudioSource(source) }
    }

    fun startField() = sendAction(RecordingService.ACTION_START_FIELD)
    fun startVoice() = sendAction(RecordingService.ACTION_START_VOICE)
    fun stop() = sendAction(RecordingService.ACTION_STOP)

    private fun sendAction(action: String) {
        context.startForegroundService(RecordingService.startIntent(context, action))
    }
}
