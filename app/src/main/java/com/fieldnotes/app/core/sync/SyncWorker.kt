// FieldNotes — SyncWorker.kt
// Authored by: drive-sync module | Implements: 07_DRIVE_SYNC_MODULE.md
package com.fieldnotes.app.core.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.fieldnotes.app.data.db.NoteDao
import com.fieldnotes.app.data.db.RecordingDao
import com.fieldnotes.app.data.db.SyncQueueDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File

/**
 * Drains the sync queue to Google Drive. Degrades gracefully: if Drive is not configured or the
 * user is not signed in, it returns success immediately and leaves the queue intact (07_DRIVE_SYNC_MODULE.md).
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val driveSync: DriveSync,
    private val syncQueueDao: SyncQueueDao,
    private val recordingDao: RecordingDao,
    private val noteDao: NoteDao,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val pending = syncQueueDao.getPendingItems()
        if (pending.isEmpty()) return Result.success()

        // Graceful degradation: nothing to do if we can't reach Drive. Keep items queued.
        if (!driveSync.canSync()) return Result.success()

        var allSucceeded = true
        for (item in pending) {
            try {
                val file = File(item.filePath)
                if (!file.exists()) {
                    syncQueueDao.delete(item)
                    continue
                }
                val driveId = driveSync.uploadFile(file, item.driveFolderName)
                when (item.fileType) {
                    TYPE_FIELD, TYPE_VOICE ->
                        recordingDao.updateSyncStatusByPath(item.filePath, SyncStatus.SYNCED.name, driveId)
                    TYPE_NOTE ->
                        noteDao.updateSyncStatus(file.name, SyncStatus.SYNCED.name, driveId)
                }
                syncQueueDao.delete(item)
            } catch (e: Exception) {
                syncQueueDao.markAttempt(item.id, e.message)
                recordingDao.updateSyncStatusByPath(item.filePath, SyncStatus.ERROR.name, null)
                allSucceeded = false
            }
        }
        return if (allSucceeded) Result.success() else Result.retry()
    }

    companion object {
        const val TYPE_FIELD = "FIELD_RECORDING"
        const val TYPE_VOICE = "VOICE_NOTE"
        const val TYPE_NOTE = "NOTE"
    }
}
