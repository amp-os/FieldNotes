// FieldNotes — Labels.kt
// Authored by: storage module | Implements: 06_STORAGE_MODULE.md (label management)
package com.fieldnotes.app.data.db

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }

/** Parse the JSON-array labels column into a list. Tolerant of malformed/empty values. */
fun RecordingEntity.labelList(): List<String> =
    runCatching { json.decodeFromString<List<String>>(labels) }.getOrDefault(emptyList())

/** Serialise a list of labels to the JSON-array string stored in [RecordingEntity.labels]. */
fun List<String>.toLabelsJson(): String = json.encodeToString<List<String>>(this)
