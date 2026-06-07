// FieldNotes — Entities.kt
// Authored by: storage module | Implements: 06_STORAGE_MODULE.md / 02_ARCHITECTURE.md (Room schema)
package com.fieldnotes.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "recordings")
data class RecordingEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val filePath: String,
    val mode: String,            // "FIELD" or "VOICE_NOTE"
    val createdAt: Long,
    val durationMs: Long,
    val fileSizeBytes: Long,
    val sampleRate: Int,
    val labels: String = "[]",   // JSON array: ["nature","birds"]
    val syncStatus: String = "PENDING",
    val driveFileId: String? = null,
    val noteFilename: String? = null, // which .md file this voice note was saved to
)

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey val filename: String,
    val filePath: String,
    val updatedAt: Long,
    val syncStatus: String = "PENDING",
    val driveFileId: String? = null,
)

@Entity(tableName = "sync_queue")
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val filePath: String,
    val fileType: String,        // "FIELD_RECORDING" | "VOICE_NOTE" | "NOTE"
    val driveFolderName: String, // e.g. "recordings" | "voice" | "notes"
    val attempts: Int = 0,
    val lastError: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)
