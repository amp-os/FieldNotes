# FieldNotes — Storage Module

## Overview

Manages local file storage for recordings and Markdown notes. No third-party storage library — pure Kotlin file I/O.

## Directory structure

```
context.filesDir/           (/data/data/com.fieldnotes.app/files/)
  recordings/               ← .flac or .wav field recordings
  voice/                    ← .m4a voice note audio files
  notes/                    ← .md markdown note files
  whisper/                  ← whisper model binaries
  temp/                     ← raw PCM during encode; always cleaned up
```

## `LocalFileManager.kt`

```kotlin
@Singleton
class LocalFileManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val recordingsDir = File(context.filesDir, "recordings").also { it.mkdirs() }
    val voiceDir      = File(context.filesDir, "voice").also { it.mkdirs() }
    val notesDir      = File(context.filesDir, "notes").also { it.mkdirs() }
    val tempDir       = File(context.filesDir, "temp").also { it.mkdirs() }

    fun newFieldRecordingFile(extension: String = "flac"): File {
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return File(recordingsDir, "field_$ts.$extension")
    }

    fun newVoiceNoteFile(): File {
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return File(voiceDir, "voice_$ts.m4a")
    }

    fun newTempFile(name: String): File = File(tempDir, name)

    fun noteFile(filename: String): File {
        // Sanitise: ensure filename ends in .md, no path traversal
        val safe = filename
            .replace(Regex("[/\\\\:*?\"<>|]"), "_")
            .let { if (it.endsWith(".md")) it else "$it.md" }
        return File(notesDir, safe)
    }

    fun listNoteFilenames(): List<String> =
        notesDir.listFiles { f -> f.extension == "md" }
            ?.map { it.name }
            ?.sorted()
            ?: emptyList()

    fun listRecordings(): List<File> =
        (recordingsDir.listFiles() ?: emptyArray()).toList() +
        (voiceDir.listFiles() ?: emptyArray()).toList()

    fun cleanupTempFiles() {
        tempDir.listFiles()?.forEach { it.delete() }
    }
    
    /** Total storage used by FieldNotes in bytes */
    fun totalStorageUsed(): Long =
        sequenceOf(recordingsDir, voiceDir, notesDir)
            .flatMap { it.walkTopDown() }
            .filter { it.isFile }
            .sumOf { it.length() }
}
```

## `MarkdownManager.kt`

Handles the creation and modification of Markdown note files. Transcriptions are **prepended** (added at the top, after any title) rather than appended, so the most recent entry is always at the top.

### File format

```markdown
# organiser.md (filename shown as title if no H1 exists)

## 2025-01-15 — 14:32

This is the transcribed text from the most recent voice note.
It may span multiple sentences and paragraphs.

---

## 2025-01-14 — 09:17

Earlier note text here.
```

### Implementation

```kotlin
@Singleton
class MarkdownManager @Inject constructor(
    private val localFileManager: LocalFileManager
) {
    /**
     * Prepend a transcription to a note file.
     * Creates the file if it doesn't exist.
     * The new entry appears at the top (below any H1 title if present).
     */
    suspend fun prependEntry(
        filename: String,
        transcriptionText: String,
        timestamp: Long = System.currentTimeMillis()
    ): File = withContext(Dispatchers.IO) {
        val file = localFileManager.noteFile(filename)
        val dateHeading = formatDateHeading(timestamp)
        val newEntry = buildString {
            appendLine("## $dateHeading")
            appendLine()
            appendLine(transcriptionText.trim())
            appendLine()
            appendLine("---")
            appendLine()
        }
        
        if (!file.exists()) {
            // New file: add a title heading
            val titleName = filename.removeSuffix(".md")
                .replaceFirstChar { it.uppercase() }
            file.writeText("# $titleName\n\n$newEntry")
        } else {
            val existing = file.readText()
            // If file starts with H1, insert after it; otherwise prepend
            val insertionPoint = if (existing.startsWith("# ")) {
                val firstNewline = existing.indexOf('\n')
                firstNewline + 1 // after the H1 line
            } else 0
            
            val before = existing.substring(0, insertionPoint)
            val after = existing.substring(insertionPoint).trimStart('\n')
            file.writeText("$before\n$newEntry$after")
        }
        file
    }

    private fun formatDateHeading(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd — HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    /** Read a note file, return null if it doesn't exist */
    suspend fun readNote(filename: String): String? = withContext(Dispatchers.IO) {
        val file = localFileManager.noteFile(filename)
        if (file.exists()) file.readText() else null
    }
    
    /** List all note names (without .md extension for display) */
    fun listNotes(): List<NoteInfo> =
        localFileManager.listNoteFilenames().map { filename ->
            val file = localFileManager.noteFile(filename)
            NoteInfo(
                filename = filename,
                displayName = filename.removeSuffix(".md"),
                lastModified = file.lastModified(),
                sizeBytes = file.length()
            )
        }
}

data class NoteInfo(
    val filename: String,
    val displayName: String,
    val lastModified: Long,
    val sizeBytes: Long
)
```

## Room entities

### `RecordingEntity.kt`

```kotlin
@Entity(tableName = "recordings")
data class RecordingEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val filePath: String,
    val mode: String,           // "FIELD" or "VOICE_NOTE"
    val createdAt: Long,
    val durationMs: Long,
    val fileSizeBytes: Long,
    val sampleRate: Int,
    val labels: String = "[]",  // JSON array: ["nature","birds"]
    val syncStatus: String = "PENDING",
    val driveFileId: String? = null,
    val noteFilename: String? = null  // which .md file this voice note was saved to
)
```

### `NoteEntity.kt`

```kotlin
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey val filename: String,
    val filePath: String,
    val updatedAt: Long,
    val syncStatus: String = "PENDING",
    val driveFileId: String? = null
)
```

### `SyncQueueEntity.kt`

```kotlin
@Entity(tableName = "sync_queue")
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val filePath: String,
    val fileType: String,       // "FIELD_RECORDING" | "VOICE_NOTE" | "NOTE"
    val driveFolderName: String, // e.g. "recordings" | "voice" | "notes"
    val attempts: Int = 0,
    val lastError: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
```

## DAOs

```kotlin
@Dao
interface RecordingDao {
    @Query("SELECT * FROM recordings ORDER BY createdAt DESC")
    fun getAllRecordings(): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings WHERE mode = :mode ORDER BY createdAt DESC")
    fun getRecordingsByMode(mode: String): Flow<List<RecordingEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(recording: RecordingEntity)

    @Query("UPDATE recordings SET syncStatus = :status, driveFileId = :driveId WHERE id = :id")
    suspend fun updateSyncStatus(id: String, status: String, driveId: String?)
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY updatedAt DESC")
    fun getAllNotes(): Flow<List<NoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(note: NoteEntity)

    @Query("UPDATE notes SET syncStatus = :status, driveFileId = :driveId WHERE filename = :filename")
    suspend fun updateSyncStatus(filename: String, status: String, driveId: String?)
}

@Dao
interface SyncQueueDao {
    @Query("SELECT * FROM sync_queue ORDER BY createdAt ASC LIMIT 20")
    suspend fun getPendingItems(): List<SyncQueueEntity>

    @Insert
    suspend fun enqueue(item: SyncQueueEntity)

    @Delete
    suspend fun delete(item: SyncQueueEntity)

    @Query("UPDATE sync_queue SET attempts = attempts + 1, lastError = :error WHERE id = :id")
    suspend fun markAttempt(id: Int, error: String?)
}
```

## `FieldNotesDatabase.kt`

```kotlin
@Database(
    entities = [RecordingEntity::class, NoteEntity::class, SyncQueueEntity::class],
    version = 1,
    exportSchema = true
)
abstract class FieldNotesDatabase : RoomDatabase() {
    abstract fun recordingDao(): RecordingDao
    abstract fun noteDao(): NoteDao
    abstract fun syncQueueDao(): SyncQueueDao
}
```

## Label management

Labels are stored as a JSON array string in `RecordingEntity.labels`. No separate label table in v1.

```kotlin
// Parse labels
fun RecordingEntity.labelList(): List<String> =
    Json.decodeFromString<List<String>>(labels)

// Serialise labels
fun List<String>.toLabelsJson(): String =
    Json.encodeToString(this)

// Get all unique labels across all recordings
suspend fun RecordingRepository.allLabels(): List<String> =
    db.recordingDao().getAllRecordingsOnce()
        .flatMap { it.labelList() }
        .distinct()
        .sorted()
```
