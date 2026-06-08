// FieldNotes — Daos.kt
// Authored by: storage module | Implements: 06_STORAGE_MODULE.md
package com.fieldnotes.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingDao {
    @Query("SELECT * FROM recordings ORDER BY createdAt DESC")
    fun getAllRecordings(): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings ORDER BY createdAt DESC")
    suspend fun getAllRecordingsOnce(): List<RecordingEntity>

    @Query("SELECT * FROM recordings WHERE mode = :mode ORDER BY createdAt DESC")
    fun getRecordingsByMode(mode: String): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings WHERE id = :id")
    suspend fun getById(id: String): RecordingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(recording: RecordingEntity)

    @Query("UPDATE recordings SET syncStatus = :status, driveFileId = :driveId WHERE filePath = :filePath")
    suspend fun updateSyncStatusByPath(filePath: String, status: String, driveId: String?)

    @Query("UPDATE recordings SET syncStatus = :status, driveFileId = :driveId WHERE id = :id")
    suspend fun updateSyncStatus(id: String, status: String, driveId: String?)

    @Query("DELETE FROM recordings WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY updatedAt DESC")
    fun getAllNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE filename = :filename")
    suspend fun getByFilename(filename: String): NoteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(note: NoteEntity)

    @Query("UPDATE notes SET syncStatus = :status, driveFileId = :driveId WHERE filename = :filename")
    suspend fun updateSyncStatus(filename: String, status: String, driveId: String?)
}

@Dao
interface SyncQueueDao {
    @Query("SELECT * FROM sync_queue ORDER BY createdAt ASC LIMIT 20")
    suspend fun getPendingItems(): List<SyncQueueEntity>

    @Query("SELECT COUNT(*) FROM sync_queue")
    fun pendingCount(): Flow<Int>

    @Insert
    suspend fun enqueue(item: SyncQueueEntity)

    @Delete
    suspend fun delete(item: SyncQueueEntity)

    @Query("UPDATE sync_queue SET attempts = attempts + 1, lastError = :error WHERE id = :id")
    suspend fun markAttempt(id: Int, error: String?)
}
