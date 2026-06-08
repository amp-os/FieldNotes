// FieldNotes — LocalFolderNoteWriter.kt
// Authored by: storage module | Implements: 06_STORAGE_MODULE.md (issue 5: on-device note folder)
// Writes/append notes into a user-chosen folder via the Storage Access Framework, so the .md lives
// in a directory on the device (visible in the Files app) rather than syncing to Drive.
package com.fieldnotes.app.core.storage

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalFolderNoteWriter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val markdownManager: MarkdownManager,
) {
    /** Existing .md notes in [treeUri], by display name. Empty if the folder is inaccessible. */
    suspend fun listNotes(treeUri: Uri): List<String> = withContext(Dispatchers.IO) {
        val dir = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext emptyList()
        dir.listFiles()
            .mapNotNull { it.name }
            .filter { it.endsWith(".md", ignoreCase = true) }
            .sorted()
    }

    /**
     * Prepend an entry to [filename] inside [treeUri], creating the file if absent. Returns the
     * display name written. Throws if the folder is no longer accessible.
     */
    suspend fun prependEntry(
        treeUri: Uri,
        filename: String,
        text: String,
        labels: List<String>,
        timestamp: Long = System.currentTimeMillis(),
    ): String = withContext(Dispatchers.IO) {
        val dir = DocumentFile.fromTreeUri(context, treeUri)
            ?: error("Local notes folder is not accessible")
        val name = safeName(filename)
        var doc = dir.findFile(name)
        val existing = doc?.takeIf { it.exists() }?.let { readText(it.uri) }
        val content = markdownManager.renderPrepended(existing, name, text, timestamp, labels)
        if (doc == null) {
            doc = dir.createFile(MIME_MARKDOWN, name) ?: error("Could not create note in folder")
        }
        writeText(doc.uri, content)
        doc.name ?: name
    }

    /** Human-readable folder name for display in Settings. */
    fun displayName(treeUri: Uri): String? =
        DocumentFile.fromTreeUri(context, treeUri)?.name

    private fun readText(uri: Uri): String =
        context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: ""

    private fun writeText(uri: Uri, content: String) {
        // "wt" truncates before writing so the file isn't left with trailing stale bytes.
        context.contentResolver.openOutputStream(uri, "wt")?.use {
            it.write(content.toByteArray())
        } ?: error("Could not write note")
    }

    private fun safeName(raw: String): String {
        val base = raw.substringAfterLast('/').substringAfterLast('\\').trim()
        val withExt = if (base.endsWith(".md", ignoreCase = true)) base else "$base.md"
        return withExt.ifBlank { "note.md" }
    }

    private companion object {
        const val MIME_MARKDOWN = "text/markdown"
    }
}
