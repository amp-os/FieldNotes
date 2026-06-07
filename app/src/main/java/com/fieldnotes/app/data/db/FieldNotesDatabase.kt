// FieldNotes — FieldNotesDatabase.kt
// Authored by: storage module | Implements: 06_STORAGE_MODULE.md
package com.fieldnotes.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [RecordingEntity::class, NoteEntity::class, SyncQueueEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class FieldNotesDatabase : RoomDatabase() {
    abstract fun recordingDao(): RecordingDao
    abstract fun noteDao(): NoteDao
    abstract fun syncQueueDao(): SyncQueueDao
}
