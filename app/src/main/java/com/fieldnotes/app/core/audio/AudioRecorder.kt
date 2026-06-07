// FieldNotes — AudioRecorder.kt
// Authored by: audio module | Implements: 04_AUDIO_MODULE.md / 02_ARCHITECTURE.md
package com.fieldnotes.app.core.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder.AudioSource
import androidx.annotation.RequiresPermission
import com.fieldnotes.app.core.storage.LocalFileManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Raw-PCM capture engine for FIELD recordings (and any path needing PCM). Writes PCM to a temp
 * file and publishes a normalised RMS amplitude for the waveform UI.
 *
 * Voice notes do NOT use this class — they use [AudioEncoder.startVoiceRecording] (MediaRecorder).
 */
@Singleton
class AudioRecorder @Inject constructor(
    @Suppress("unused") @ApplicationContext private val context: Context,
    private val localFileManager: LocalFileManager,
) {
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null

    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    private val _state = MutableStateFlow<RecorderState>(RecorderState.Idle)
    val state: StateFlow<RecorderState> = _state.asStateFlow()

    /**
     * Begin capture. Returns the temp PCM file being written. FIELD = 48kHz stereo 16-bit;
     * VOICE_NOTE = 16kHz mono 16-bit (Whisper-native).
     */
    @SuppressLint("MissingPermission")
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start(
        mode: RecordingMode,
        audioSource: Int = AudioSource.MIC,
        scope: CoroutineScope,
    ): File {
        val (sampleRate, channels, channelConfig) = when (mode) {
            RecordingMode.FIELD -> Triple(48000, 2, AudioFormat.CHANNEL_IN_STEREO)
            RecordingMode.VOICE_NOTE -> Triple(16000, 1, AudioFormat.CHANNEL_IN_MONO)
        }

        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate, channelConfig, AudioFormat.ENCODING_PCM_16BIT,
        )
        val bufferSize = (if (minBuffer > 0) minBuffer else sampleRate * channels * 2) * 4

        val pcmFile = localFileManager.newTempFile("recording_${System.currentTimeMillis()}.pcm")

        val record = AudioRecord(
            audioSource, sampleRate, channelConfig,
            AudioFormat.ENCODING_PCM_16BIT, bufferSize,
        )
        audioRecord = record
        record.startRecording()
        _state.value = RecorderState.Recording(mode, sampleRate, channels, pcmFile)

        recordingJob = scope.launch(Dispatchers.IO) {
            val buffer = ShortArray(bufferSize / 2)
            pcmFile.outputStream().buffered().use { out ->
                while (isActive) {
                    val read = record.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        val bytes = ByteArray(read * 2)
                        for (i in 0 until read) {
                            bytes[i * 2] = (buffer[i].toInt() and 0xFF).toByte()
                            bytes[i * 2 + 1] = (buffer[i].toInt() shr 8).toByte()
                        }
                        out.write(bytes)
                        var sum = 0.0
                        for (i in 0 until read) sum += buffer[i].toDouble().pow(2)
                        _amplitude.value = (sqrt(sum / read) / Short.MAX_VALUE).toFloat()
                    }
                }
            }
        }
        return pcmFile
    }

    /** Stop capture and return the completed PCM file. */
    fun stop(): File {
        recordingJob?.cancel()
        recordingJob = null
        audioRecord?.apply {
            runCatching { stop() }
            release()
        }
        audioRecord = null
        val current = _state.value
        _state.value = RecorderState.Idle
        _amplitude.value = 0f
        return (current as? RecorderState.Recording)?.pcmFile
            ?: error("stop() called while not recording")
    }

    val isRecording: Boolean get() = _state.value is RecorderState.Recording
}

sealed class RecorderState {
    data object Idle : RecorderState()
    data class Recording(
        val mode: RecordingMode,
        val sampleRate: Int,
        val channels: Int,
        val pcmFile: File,
    ) : RecorderState()
}
