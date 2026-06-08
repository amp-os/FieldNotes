// FieldNotes — TranscriptionRepository.kt
// Authored by: repositories | Implements: 05_WHISPER_MODULE.md / 02_ARCHITECTURE.md
package com.fieldnotes.app.data.repository

import com.fieldnotes.app.core.whisper.TranscriptionResult
import com.fieldnotes.app.core.whisper.WhisperEngine
import com.fieldnotes.app.core.whisper.WhisperModelManager
import kotlinx.coroutines.flow.first
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TranscriptionRepository @Inject constructor(
    private val whisperEngine: WhisperEngine,
    private val modelManager: WhisperModelManager,
    private val recordingRepository: RecordingRepository,
    private val settingsRepository: SettingsRepository,
) {
    /** The model the user has selected for transcription is the one that must be present. */
    suspend fun isModelDownloaded(): Boolean =
        modelManager.isModelDownloaded(settingsRepository.selectedModel.first())

    /** Transcribe a previously-saved recording by id. Throws if the recording/model is missing. */
    suspend fun transcribeRecording(recordingId: String): TranscriptionResult {
        val recording = recordingRepository.getById(recordingId)
            ?: error("Recording not found: $recordingId")
        return whisperEngine.transcribe(File(recording.filePath), settingsRepository.selectedModel.first())
    }

    suspend fun transcribeFile(file: File): TranscriptionResult =
        whisperEngine.transcribe(file, settingsRepository.selectedModel.first())
}
