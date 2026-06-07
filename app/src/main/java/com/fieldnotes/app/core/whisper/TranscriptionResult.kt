// FieldNotes — TranscriptionResult.kt
// Authored by: whisper module | Implements: 05_WHISPER_MODULE.md
package com.fieldnotes.app.core.whisper

import java.io.File

data class TranscriptionResult(
    val text: String,
    val audioFile: File,
    val processedAt: Long,
)
