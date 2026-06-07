# FieldNotes — Architecture

## Tech stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin |
| UI | Jetpack Compose (Material 3) |
| Architecture pattern | MVVM + Repository |
| DI | Hilt |
| Async | Coroutines + StateFlow/SharedFlow |
| Local DB | Room (metadata only; audio files on disk) |
| Audio capture | `AudioRecord` API (raw PCM) |
| Audio encoding | libFLAC (via JNI) for field recordings; `MediaRecorder` with AAC-LC for voice notes |
| Transcription | whisper.cpp via JNI (`whispercpp` Android wrapper) |
| Drive sync | Google Drive Android SDK (REST v3 via `google-api-client-android`) |
| Widget | Jetpack Glance |
| Notifications | NotificationCompat (foreground service) |
| Min SDK | 26 (Android 8.0) |
| Target SDK | 36 (Android 16) |

## Module / package structure

```
com.fieldnotes.app/
  core/
    audio/
      AudioRecorder.kt          ← AudioRecord wrapper, raw PCM output
      AudioEncoder.kt           ← FLAC or AAC encoding
      AudioInputRouter.kt       ← Selects mic source, monitors headset events
      RecordingMode.kt          ← Enum: FIELD | VOICE_NOTE
    whisper/
      WhisperEngine.kt          ← JNI bridge to whisper.cpp
      WhisperModelManager.kt    ← Model file management, first-run download/copy
      TranscriptionResult.kt    ← Data class
    storage/
      LocalFileManager.kt       ← Creates/manages file paths, directory structure
      MarkdownManager.kt        ← Prepend/create .md files, date heading logic
      RecordingMetadata.kt      ← Data class for Room entity
    sync/
      DriveSync.kt              ← Upload logic, retry, queue management
      SyncQueue.kt              ← Room entity for pending uploads
      SyncWorker.kt             ← WorkManager worker for background sync
      SyncStatus.kt             ← Enum: PENDING | UPLOADING | SYNCED | ERROR
  data/
    db/
      FieldNotesDatabase.kt     ← Room database
      RecordingDao.kt
      SyncQueueDao.kt
    repository/
      RecordingRepository.kt    ← Combines audio, storage, sync
      TranscriptionRepository.kt
      NoteRepository.kt
  ui/
    recorder/
      RecorderScreen.kt         ← Main screen: waveform, two record buttons
      RecorderViewModel.kt
    recordings/
      RecordingsListScreen.kt   ← Browse/filter recordings
      RecordingsViewModel.kt
    transcription/
      TranscriptionScreen.kt    ← Review/edit transcription, assign to note
      TranscriptionViewModel.kt
    notes/
      NotesListScreen.kt        ← Browse markdown notes
    settings/
      SettingsScreen.kt         ← WiFi-only sync, Drive account, audio prefs
      SettingsViewModel.kt
    common/
      WaveformView.kt           ← Compose canvas waveform animation
      SyncStatusIcon.kt
  widget/
    RecordWidget.kt             ← Glance widget
    WidgetReceiver.kt
  service/
    RecordingService.kt         ← Foreground service for recording
  di/
    AudioModule.kt
    DatabaseModule.kt
    SyncModule.kt
    WhisperModule.kt
  MainActivity.kt
  FieldNotesApplication.kt
```

## Data flow: Voice Note recording

```
User taps "Voice Note" button (UI or Widget)
  → RecordingService starts (foreground, notification shown)
  → AudioRecorder.start(VOICE_NOTE)
      → AudioRecord at 16kHz mono (optimal for Whisper)
      → PCM frames buffered in memory
  → MediaRecorder encodes to AAC-LC .m4a concurrently, saved to /voice/
User taps Stop
  → RecordingService stops
  → RecordingMetadata saved to Room (file path, duration, labels, sync=PENDING)
  → SyncQueue entry created for audio file
  → WhisperEngine.transcribe(audioFile) launched in background coroutine
      → Returns TranscriptionResult(text, durationMs)
  → TranscriptionScreen launched: user reviews text, picks/types note filename
  → MarkdownManager.prepend(filename, date heading + transcription text)
  → SyncQueue entry created for .md file
  → SyncWorker triggered (respects WiFi-only setting)
```

## Data flow: Field Recording

```
User taps "Field Rec" button
  → RecordingService starts
  → AudioRecorder.start(FIELD)
      → AudioRecord at 48kHz stereo (if device supports; fallback 44.1kHz mono)
      → Raw PCM streamed to disk via buffered output stream
User taps Stop
  → PCM file closed
  → AudioEncoder.encodeFLAC(pcmFile) → .flac file (or .wav — see note below)
  → RecordingMetadata saved, SyncQueue entry created
  → Optionally: TranscriptionScreen offered ("Transcribe this recording?")
```

## Audio format decisions

### Field recordings
- **Format: FLAC** (Free Lossless Audio Codec)
  - Rationale: ~50-60% size reduction vs WAV with zero quality loss. FLAC is open, patent-free, and supported on macOS, Windows (via codec), Linux, VLC, Audacity, and all DAWs natively or via plugin.
  - WAV concern (noted by user): WAV is marginally more universal for very simple playback, but for archival audio work, FLAC is the correct choice. Any serious audio tool handles it.
  - **Setting exposed:** User can switch to WAV in Settings for maximum compatibility.
- **Sample rate:** 48kHz (device permitting). Fallback: 44100Hz.
- **Bit depth:** 24-bit if `AudioRecord` supports it; fallback 16-bit.
- **Channels:** Stereo if device has stereo mics; else mono.

### Voice notes
- **Format: AAC-LC in .m4a container** via `MediaRecorder`
  - Bitrate: 128kbps (excellent intelligibility, tiny files ~1MB/min)
  - Sample rate: 16kHz (matches Whisper's native input — no resampling needed for transcription)
  - Mono
- Rationale: Voice notes don't need archival quality. AAC-LC is natively supported on all Android, iOS, macOS, Windows platforms. 

### Whisper audio preprocessing
- Whisper requires 16kHz mono float32 PCM.
- For voice notes: recorded at 16kHz, so conversion is minimal (decode AAC → float32 PCM).
- For field recordings: resample from 48kHz to 16kHz using a simple sinc resampler before passing to Whisper.

## Local storage paths

```
/data/data/com.fieldnotes.app/files/
  recordings/   ← field recording .flac files
  voice/        ← voice note .m4a files
  notes/        ← .md markdown files
  whisper/      ← whisper model files (ggml-*.bin)
  temp/         ← raw PCM during capture, deleted after encoding
```

## Room database schema

### recordings
| Column | Type | Notes |
|--------|------|-------|
| id | TEXT (UUID) | PK |
| file_path | TEXT | Absolute local path |
| mode | TEXT | FIELD or VOICE_NOTE |
| created_at | INTEGER | Unix timestamp ms |
| duration_ms | INTEGER | |
| file_size_bytes | INTEGER | |
| sample_rate | INTEGER | |
| labels | TEXT | JSON array of strings |
| sync_status | TEXT | PENDING/UPLOADING/SYNCED/ERROR |
| drive_file_id | TEXT | nullable |

### notes
| Column | Type | Notes |
|--------|------|-------|
| filename | TEXT | PK e.g. "organiser.md" |
| file_path | TEXT | |
| updated_at | INTEGER | |
| sync_status | TEXT | |
| drive_file_id | TEXT | nullable |

### sync_queue
| Column | Type | Notes |
|--------|------|-------|
| id | INTEGER | PK autoincrement |
| file_path | TEXT | |
| file_type | TEXT | RECORDING/VOICE/NOTE |
| drive_folder | TEXT | Target Drive folder path |
| attempts | INTEGER | default 0 |
| last_error | TEXT | nullable |
| created_at | INTEGER | |
