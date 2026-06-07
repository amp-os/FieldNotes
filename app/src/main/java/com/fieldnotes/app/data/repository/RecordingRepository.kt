// FieldNotes — RecordingRepository.kt
// Authored by: repositories | Implements: 02_ARCHITECTURE.md (combines audio, storage, sync)
package com.fieldnotes.app.data.repository

import com.fieldnotes.app.core.audio.RecordingMode
import com.fieldnotes.app.core.storage.LocalFileManager
import com.fieldnotes.app.core.sync.SyncScheduler
import com.fieldnotes.app.core.sync.SyncStatus
import com.fieldnotes.app.core.sync.SyncWorker
import com.fieldnotes.app.data.db.RecordingDao
import com.fieldnotes.app.data.db.RecordingEntity
import com.fieldnotes.app.data.db.SyncQueueDao
import com.fieldnotes.app.data.db.SyncQueueEntity
import com.fieldnotes.app.data.db.labelList
import com.fieldnotes.app.data.db.toLabelsJson
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecordingRepository @Inject constructor(
    private val recordingDao: RecordingDao,
    private val syncQueueDao: SyncQueueDao,
    private val localFileManager: LocalFileManager,
    private val settingsRepository: SettingsRepository,
    private val syncScheduler: SyncScheduler,
) {
    fun newVoiceNoteFile(): File = localFileManager.newVoiceNoteFile()

    fun getAllRecordings(): Flow<List<RecordingEntity>> = recordingDao.getAllRecordings()
    fun getRecordingsByMode(mode: RecordingMode): Flow<List<RecordingEntity>> =
        recordingDao.getRecordingsByMode(mode.name)

    suspend fun getById(id: String): RecordingEntity? = recordingDao.getById(id)

    suspend fun saveFieldRecording(file: File, durationMs: Long, sampleRate: Int): String {
        val entity = RecordingEntity(
            id = UUID.randomUUID().toString(),
            filePath = file.absolutePath,
            mode = RecordingMode.FIELD.name,
            createdAt = System.currentTimeMillis(),
            durationMs = durationMs,
            fileSizeBytes = file.length(),
            sampleRate = sampleRate,
            syncStatus = SyncStatus.PENDING.name,
        )
        recordingDao.insert(entity)
        enqueueAndSchedule(file, SyncWorker.TYPE_FIELD, "recordings")
        return entity.id
    }

    suspend fun saveVoiceNote(file: File, durationMs: Long): String {
        val entity = RecordingEntity(
            id = UUID.randomUUID().toString(),
            filePath = file.absolutePath,
            mode = RecordingMode.VOICE_NOTE.name,
            createdAt = System.currentTimeMillis(),
            durationMs = durationMs,
            fileSizeBytes = file.length(),
            sampleRate = 16000,
            syncStatus = SyncStatus.PENDING.name,
        )
        recordingDao.insert(entity)
        enqueueAndSchedule(file, SyncWorker.TYPE_VOICE, "voice")
        return entity.id
    }

    suspend fun setNoteFilename(recordingId: String, noteFilename: String) {
        val existing = recordingDao.getById(recordingId) ?: return
        recordingDao.insert(existing.copy(noteFilename = noteFilename))
    }

    suspend fun updateLabels(recordingId: String, labels: List<String>) {
        val existing = recordingDao.getById(recordingId) ?: return
        recordingDao.insert(existing.copy(labels = labels.toLabelsJson()))
    }

    suspend fun delete(recordingId: String) {
        val existing = recordingDao.getById(recordingId) ?: return
        runCatching { File(existing.filePath).delete() }
        recordingDao.deleteById(recordingId)
    }

    suspend fun allLabels(): List<String> =
        recordingDao.getAllRecordingsOnce().flatMap { it.labelList() }.distinct().sorted()

    private suspend fun enqueueAndSchedule(file: File, fileType: String, folder: String) {
        syncQueueDao.enqueue(
            SyncQueueEntity(filePath = file.absolutePath, fileType = fileType, driveFolderName = folder),
        )
        syncScheduler.scheduleSync(settingsRepository.isWifiOnly())
    }
}
