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

    /** A note file present in Drive's FieldNotes/notes folder. */
    data class RemoteNote(val id: String, val name: String)

    /** List the .md notes already in FieldNotes/notes on Drive (for re-import after reconnect). */
    suspend fun listRemoteNotes(): List<RemoteNote> = withContext(Dispatchers.IO) {
        val drive = authManager.getDriveService() ?: return@withContext emptyList()
        val rootFolderId = findOrCreateFolder(drive, "FieldNotes", null)
        val notesFolderId = findOrCreateFolder(drive, "notes", rootFolderId)
        val result = drive.files().list()
            .setQ("'$notesFolderId' in parents and trashed=false and mimeType != 'application/vnd.google-apps.folder'")
            .setSpaces("drive")
            .setFields("files(id,name)")
            .execute()
        result.files.orEmpty()
            .filter { it.name.endsWith(".md", ignoreCase = true) }
            .map { RemoteNote(it.id, it.name) }
    }

    /** Download a Drive file's bytes into [dest]. */
    suspend fun downloadFile(fileId: String, dest: File): Unit = withContext(Dispatchers.IO) {
        val drive = authManager.getDriveService()
            ?: throw IllegalStateException("Not authenticated with Drive")
        dest.parentFile?.mkdirs()
        dest.outputStream().use { out -> drive.files().get(fileId).executeMediaAndDownloadTo(out) }
    }

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
