// FieldNotes — DriveSync.kt
// Authored by: drive-sync module | Implements: 07_DRIVE_SYNC_MODULE.md
package com.fieldnotes.app.core.sync

import com.google.api.client.http.FileContent
import com.google.api.services.drive.Drive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import com.google.api.services.drive.model.File as DriveFile

@Singleton
class DriveSync @Inject constructor(
    private val authManager: DriveAuthManager,
) {
    /** True when uploads can be attempted (client id present AND signed in). */
    suspend fun canSync(): Boolean = authManager.getDriveService() != null

    /** Upload (or update) [localFile] into FieldNotes/[driveFolderName]. Returns the Drive file id. */
    suspend fun uploadFile(
        localFile: File,
        driveFolderName: String,
        existingDriveFileId: String? = null,
    ): String = withContext(Dispatchers.IO) {
        val drive = authManager.getDriveService()
            ?: throw IllegalStateException("Not authenticated with Drive")

        val rootFolderId = findOrCreateFolder(drive, "FieldNotes", null)
        val targetFolderId = findOrCreateFolder(drive, driveFolderName, rootFolderId)

        val mimeType = when (localFile.extension.lowercase()) {
            "flac" -> "audio/flac"
            "wav" -> "audio/wav"
            "m4a" -> "audio/mp4"
            "md" -> "text/markdown"
            else -> "application/octet-stream"
        }
        val content = FileContent(mimeType, localFile)

        if (existingDriveFileId != null) {
            val metadata = DriveFile().apply { name = localFile.name }
            drive.files().update(existingDriveFileId, metadata, content).setFields("id").execute().id
        } else {
            val metadata = DriveFile().apply {
                name = localFile.name
                parents = listOf(targetFolderId)
            }
            drive.files().create(metadata, content).setFields("id").execute().id
        }
    }

    private fun findOrCreateFolder(drive: Drive, name: String, parentId: String?): String {
        val query = buildString {
            append("name='$name' and mimeType='application/vnd.google-apps.folder' and trashed=false")
            if (parentId != null) append(" and '$parentId' in parents")
        }
        val existing = drive.files().list().setQ(query).setSpaces("drive").setFields("files(id)").execute()
        existing.files.firstOrNull()?.id?.let { return it }

        val folder = DriveFile().apply {
            this.name = name
            mimeType = "application/vnd.google-apps.folder"
            if (parentId != null) parents = listOf(parentId)
        }
        return drive.files().create(folder).setFields("id").execute().id
    }
}
