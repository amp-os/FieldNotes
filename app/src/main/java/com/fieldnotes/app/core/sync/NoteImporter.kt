// FieldNotes — NoteImporter.kt
// Authored by: drive-sync module | Implements: 07_DRIVE_SYNC_MODULE.md (issue 7)
// After a reinstall or a Drive disconnect/reconnect, pulls the notes already in Drive back into the
// app so they reappear and future entries append to the existing content instead of replacing it.
package com.fieldnotes.app.core.sync

import android.util.Log
import com.fieldnotes.app.core.storage.LocalFileManager
import com.fieldnotes.app.data.db.NoteDao
import com.fieldnotes.app.data.db.NoteEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoteImporter @Inject constructor(
    private val driveSync: DriveSync,
    private val localFileManager: LocalFileManager,
    private val noteDao: NoteDao,
) {
    /**
     * Reconcile Drive's notes with local state. A note missing locally is downloaded; a note that
     * already exists locally is only *linked* to its Drive file id (so later uploads update that
     * file rather than creating a duplicate) — its content is never overwritten.
     */
    suspend fun importExistingNotes() {
        if (!driveSync.canSync()) return
        val remote = runCatching { driveSync.listRemoteNotes() }
            .onFailure { Log.w(TAG, "Could not list Drive notes", it) }
            .getOrDefault(emptyList())

        for (note in remote) {
            val localFile = localFileManager.noteFile(note.name)
            val existing = noteDao.getByFilename(localFile.name)
            runCatching {
                if (!localFile.exists()) {
                    driveSync.downloadFile(note.id, localFile)
                    noteDao.upsert(
                        NoteEntity(
                            filename = localFile.name,
                            filePath = localFile.absolutePath,
                            updatedAt = System.currentTimeMillis(),
                            syncStatus = SyncStatus.SYNCED.name,
                            driveFileId = note.id,
                        ),
                    )
                } else if (existing == null || existing.driveFileId == null) {
                    // Local copy is authoritative; just remember which Drive file it maps to.
                    noteDao.upsert(
                        (existing ?: NoteEntity(
                            filename = localFile.name,
                            filePath = localFile.absolutePath,
                            updatedAt = System.currentTimeMillis(),
                        )).copy(driveFileId = note.id),
                    )
                }
            }.onFailure { Log.w(TAG, "Could not import note ${note.name}", it) }
        }
    }

    private companion object {
        const val TAG = "NoteImporter"
    }
}
