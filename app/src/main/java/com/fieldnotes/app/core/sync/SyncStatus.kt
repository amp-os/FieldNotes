// FieldNotes — SyncStatus.kt
// Authored by: drive-sync module | Implements: 02_ARCHITECTURE.md / 07_DRIVE_SYNC_MODULE.md
package com.fieldnotes.app.core.sync

enum class SyncStatus {
    PENDING, UPLOADING, SYNCED, ERROR;

    companion object {
        fun fromString(value: String?): SyncStatus =
            entries.firstOrNull { it.name == value } ?: PENDING
    }
}
