// FieldNotes — MarkdownManager.kt
// Authored by: storage module | Implements: 06_STORAGE_MODULE.md
// NOTE: 100% line-coverage target (10_TESTING_PLAN.md). Keep logic small and total.
package com.fieldnotes.app.core.storage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Creates and updates Markdown note files. New transcription entries are *prepended*
 * (placed at the top, below any H1 title) so the most recent entry is always first.
 */
@Singleton
class MarkdownManager @Inject constructor(
    private val localFileManager: LocalFileManager,
) {
    /**
     * Prepend a transcription entry to [filename]. Creates the file (with an H1 title) if absent.
     * Returns the file written. Filename sanitisation/traversal-prevention is delegated to
     * [LocalFileManager.noteFile].
     */
    suspend fun prependEntry(
        filename: String,
        transcriptionText: String,
        timestamp: Long = System.currentTimeMillis(),
        labels: List<String> = emptyList(),
    ): File = withContext(Dispatchers.IO) {
        val file = localFileManager.noteFile(filename)
        val newEntry = buildString {
            appendLine("## ${formatDateHeading(timestamp)}")
            appendLine()
            appendLine(transcriptionText.trim())
            val tagLine = formatTags(labels)
            if (tagLine.isNotEmpty()) {
                appendLine()
                appendLine(tagLine)
            }
            appendLine()
            appendLine("---")
            appendLine()
        }

        if (!file.exists()) {
            val title = file.name.removeSuffix(".md").replaceFirstChar { it.uppercase() }
            file.writeText("# $title\n\n$newEntry")
        } else {
            val existing = file.readText()
            val insertionPoint = if (existing.startsWith("# ")) {
                val firstNewline = existing.indexOf('\n')
                if (firstNewline >= 0) firstNewline + 1 else existing.length
            } else {
                0
            }
            val before = existing.substring(0, insertionPoint)
            val after = existing.substring(insertionPoint).trimStart('\n')
            file.writeText("$before\n$newEntry$after")
        }
        file
    }

    /** Read a note's content, or null if it does not exist. */
    suspend fun readNote(filename: String): String? = withContext(Dispatchers.IO) {
        val file = localFileManager.noteFile(filename)
        if (file.exists()) file.readText() else null
    }

    /** List all notes (display metadata). */
    fun listNotes(): List<NoteInfo> =
        localFileManager.listNoteFilenames().map { name ->
            val file = localFileManager.noteFile(name)
            NoteInfo(
                filename = name,
                displayName = name.removeSuffix(".md"),
                lastModified = file.lastModified(),
                sizeBytes = file.length(),
            )
        }

    private fun formatDateHeading(timestamp: Long): String =
        SimpleDateFormat("yyyy-MM-dd — HH:mm", Locale.getDefault()).format(Date(timestamp))

    /**
     * Render labels as searchable `@tags` on a single line. Each tag is a single token (internal
     * whitespace collapsed to '-'), de-duplicated, blanks dropped. Returns "" when there are none.
     */
    private fun formatTags(labels: List<String>): String =
        labels.asSequence()
            .map { it.trim().removePrefix("@").trim() }
            .filter { it.isNotEmpty() }
            .map { "@" + it.replace(Regex("\\s+"), "-") }
            .distinct()
            .joinToString(" ")
}

data class NoteInfo(
    val filename: String,
    val displayName: String,
    val lastModified: Long,
    val sizeBytes: Long,
)
