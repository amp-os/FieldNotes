// FieldNotes — RecordingSessionManager.kt
// Authored by: audio module | Implements: 04_AUDIO_MODULE.md / 02_ARCHITECTURE.md (data flow)
package com.fieldnotes.app.core.audio

import android.Manifest
import android.media.MediaRecorder
import android.os.SystemClock
import android.util.Log
import androidx.annotation.RequiresPermission
import com.fieldnotes.app.data.repository.RecordingRepository
import com.fieldnotes.app.data.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for an in-progress recording. Shared by [com.fieldnotes.app.service.RecordingService]
 * (which provides the foreground/notification lifecycle) and the UI (which observes [session]/[amplitude]).
 */
@Singleton
class RecordingSessionManager @Inject constructor(
    private val audioRecorder: AudioRecorder,
    private val audioEncoder: AudioEncoder,
    private val recordingRepository: RecordingRepository,
    private val settingsRepository: SettingsRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _session = MutableStateFlow<RecordingSession?>(null)
    val session: StateFlow<RecordingSession?> = _session.asStateFlow()

    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    /** Emitted when a recording finishes saving. VOICE notes carry a recordingId for transcription. */
    private val _completed = MutableSharedFlow<CompletedRecording>(extraBufferCapacity = 4)
    val completed: SharedFlow<CompletedRecording> = _completed.asSharedFlow()

    private var voiceRecorder: MediaRecorder? = null
    private var voicePcmAmplitudeJob: Job? = null
    private var amplitudeMirrorJob: Job? = null

    val isRecording: Boolean get() = _session.value != null

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start(mode: RecordingMode) {
        if (isRecording) return
        when (mode) {
            RecordingMode.FIELD -> startField()
            RecordingMode.VOICE_NOTE -> startVoice()
        }
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun startField() {
        val source = runBlocking { settingsRepository.audioSource() }
        audioRecorder.start(RecordingMode.FIELD, source, scope)
        _session.value = RecordingSession(RecordingMode.FIELD, SystemClock.elapsedRealtime())
        amplitudeMirrorJob = scope.launch {
            audioRecorder.amplitude.collect { _amplitude.value = it }
        }
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun startVoice() {
        val file = recordingRepository.newVoiceNoteFile()
        voiceRecorder = audioEncoder.startVoiceRecording(file)
        _session.value = RecordingSession(RecordingMode.VOICE_NOTE, SystemClock.elapsedRealtime(), file)
        // MediaRecorder gives no PCM; poll getMaxAmplitude for the waveform.
        voicePcmAmplitudeJob = scope.launch {
            while (isActive) {
                val max = runCatching { voiceRecorder?.maxAmplitude ?: 0 }.getOrDefault(0)
                _amplitude.value = (max / 32767f).coerceIn(0f, 1f)
                delay(100)
            }
        }
    }

    /** Stop the current recording, finalise it, persist metadata, and emit a completion event. */
    suspend fun stop() {
        val current = _session.value ?: return
        val durationMs = SystemClock.elapsedRealtime() - current.startElapsedRealtime
        amplitudeMirrorJob?.cancel(); amplitudeMirrorJob = null
        voicePcmAmplitudeJob?.cancel(); voicePcmAmplitudeJob = null
        _amplitude.value = 0f
        _session.value = null

        try {
            when (current.mode) {
                RecordingMode.FIELD -> {
                    val pcm = audioRecorder.stop()
                    val preferWav = settingsRepository.fieldFormatIsWav()
                    val (sampleRate, channels) = 48000 to 2
                    val output = audioEncoder.encodeField(pcm, sampleRate, channels, preferWav)
                    val id = recordingRepository.saveFieldRecording(output, durationMs, sampleRate)
                    _completed.emit(CompletedRecording(id, RecordingMode.FIELD))
                }
                RecordingMode.VOICE_NOTE -> {
                    val recorder = voiceRecorder
                    voiceRecorder = null
                    runCatching {
                        recorder?.stop()
                    }
                    recorder?.release()
                    val file = current.voiceFile ?: error("voice file missing")
                    val id = recordingRepository.saveVoiceNote(file, durationMs)
                    _completed.emit(CompletedRecording(id, RecordingMode.VOICE_NOTE))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to finalise recording", e)
        }
    }

    companion object {
        private const val TAG = "RecordingSessionManager"
    }
}

data class RecordingSession(
    val mode: RecordingMode,
    val startElapsedRealtime: Long,
    val voiceFile: File? = null,
)

data class CompletedRecording(
    val recordingId: String,
    val mode: RecordingMode,
)
