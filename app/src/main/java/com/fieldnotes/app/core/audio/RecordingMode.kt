// FieldNotes — RecordingMode.kt
// Authored by: audio module | Implements: 04_AUDIO_MODULE.md
package com.fieldnotes.app.core.audio

enum class RecordingMode {
    FIELD,       // High-quality, lossless, 48kHz
    VOICE_NOTE   // Compressed, 16kHz, optimised for Whisper
}
