// FieldNotes — NoteRepository.kt
// Authored by: repositories | Implements: 06_STORAGE_MODULE.md / 07_DRIVE_SYNC_MODULE.md
package com.fieldnotes.app.data.repository

import android.net.Uri
import com.fieldnotes.app.core.storage.LocalFileManager
import com.fieldnotes.app.core.storage.LocalFolderNoteWriter
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
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** Where a note's .md is written. */
enum class NoteDestination { DRIVE, LOCAL_FOLDER }

@Singleton
class NoteRepository @Inject constructor(
    private val noteDao: NoteDao,
    private val markdownManager: MarkdownManager,
    private val localFileManager: LocalFileManager,
    private val localFolderNoteWriter: LocalFolderNoteWriter,
    private val syncQueueDao: SyncQueueDao,
    private val settingsRepository: SettingsRepository,
    private val syncScheduler: SyncScheduler,
) {
    fun getAllNotes(): Flow<List<NoteEntity>> = noteDao.getAllNotes()

    fun listNoteFilenames(): List<String> = localFileManager.listNoteFilenames()

    fun listNotes(): List<NoteInfo> = markdownManager.listNotes()

    suspend fun readNote(filename: String): String? = markdownManager.readNote(filename)

    /** Existing .md notes in the configured local folder, or empty if none is set. */
    suspend fun listLocalFolderNotes(): List<String> {
        val uri = settingsRepository.localNotesFolderUri.first() ?: return emptyList()
        return localFolderNoteWriter.listNotes(Uri.parse(uri))
    }

    /**
     * Prepend [text] to [filename]. For [NoteDestination.DRIVE] the note is written to app storage,
     * tracked in Room and queued for Drive sync. For [NoteDestination.LOCAL_FOLDER] it is written to
     * the user's chosen on-device folder and is not synced (issue 5). Returns the note's name.
     */
    suspend fun saveTranscription(
        filename: String,
        text: String,
        labels: List<String> = emptyList(),
        destination: NoteDestination = NoteDestination.DRIVE,
        timestamp: Long = System.currentTimeMillis(),
    ): String = when (destination) {
        NoteDestination.LOCAL_FOLDER -> {
            val uri = settingsRepository.localNotesFolderUri.first()
                ?: error("No local notes folder configured")
            localFolderNoteWriter.prependEntry(Uri.parse(uri), filename, text, labels, timestamp)
        }
        NoteDestination.DRIVE -> {
            val file = markdownManager.prependEntry(filename, text, timestamp, labels)
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
            file.name
        }
    }
}
