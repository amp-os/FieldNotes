// FieldNotes — NoteRepository.kt
// Authored by: repositories | Implements: 06_STORAGE_MODULE.md / 07_DRIVE_SYNC_MODULE.md
package com.fieldnotes.app.data.repository

import com.fieldnotes.app.core.storage.LocalFileManager
import com.fieldnotes.app.core.storage.MarkdownManager
import com.fieldnotes.app.core.storage.NoteInfo
import com.fieldnotes.app.core.sync.SyncScheduler
import com.fieldnotes.app.core.sync.SyncStatus
import com.fieldnotes.app.core.sync.SyncWorker
import com.fieldnotes.app.data.db.NoteDao
import com.fieldnotes.app.data.db.NoteEntity
import com.fieldnotes.app.data.db.SyncQueueDao
import com.fieldnotes.app.data.db.SyncQueueEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoteRepository @Inject constructor(
    private val noteDao: NoteDao,
    private val markdownManager: MarkdownManager,
    private val localFileManager: LocalFileManager,
    private val syncQueueDao: SyncQueueDao,
    private val settingsRepository: SettingsRepository,
    private val syncScheduler: SyncScheduler,
) {
    fun getAllNotes(): Flow<List<NoteEntity>> = noteDao.getAllNotes()

    fun listNoteFilenames(): List<String> = localFileManager.listNoteFilenames()

    fun listNotes(): List<NoteInfo> = markdownManager.listNotes()

    suspend fun readNote(filename: String): String? = markdownManager.readNote(filename)

    /** Prepend [text] to [filename], persist note metadata, and queue the .md for Drive sync. */
    suspend fun saveTranscription(filename: String, text: String, timestamp: Long = System.currentTimeMillis()): String {
        val file = markdownManager.prependEntry(filename, text, timestamp)
        noteDao.upsert(
            NoteEntity(
                filename = file.name,
                filePath = file.absolutePath,
                updatedAt = System.currentTimeMillis(),
                syncStatus = SyncStatus.PENDING.name,
            ),
        )
        syncQueueDao.enqueue(
            SyncQueueEntity(filePath = file.absolutePath, fileType = SyncWorker.TYPE_NOTE, driveFolderName = "notes"),
        )
        syncScheduler.scheduleSync(settingsRepository.isWifiOnly())
        return file.name
    }
}
