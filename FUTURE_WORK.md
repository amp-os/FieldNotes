# FieldNotes — Future Work & Suggestions

Backlog captured 2026-06-08, after the transcription crash/perf fixes and the issues.md (0–8) work.
Not in priority order within a section; sections are roughly high→low impact.

## Data durability (do before further schema changes)

- **Replace `fallbackToDestructiveMigration()` with real Room migrations.** Today *any* schema
  change wipes the `recordings`/`notes`/`sync_queue` tables. The app now holds real user data, so
  the next entity change would lose the library. Add versioned migrations (see `DatabaseModule`,
  `FieldNotesDatabase`). This is why issue 5 (local notes) deliberately avoided a schema change.
- **Re-import *recordings* on reinstall/reconnect, not just notes.** Issue 7 re-imports notes from
  Drive, but after a reinstall the Library (recordings list) is empty even though the audio is in
  `FieldNotes/recordings|voice` on Drive. Mirror `NoteImporter` for recordings (download + reinsert
  `RecordingEntity` rows, relink `driveFileId`). Compounds with the destructive-migration wipe above.

## Transcription robustness

- **Make background transcription durable.** It currently runs in an app-scoped coroutine
  (`TranscriptionManager`), so an armed "Save when ready" job is lost if the OS kills the process
  while backgrounded. The robust fix is a WorkManager **expedited + foreground** worker keyed by
  recording id (survives process death/reboot, gets foreground CPU priority); the screen observes
  `WorkInfo`. See the issue-2 tradeoff discussion. Medium effort; only matters for the "arm and leave
  the app entirely" path.
- **Concurrency is serialized, not parallel.** A second transcription now queues behind the first
  (mutex in `WhisperEngine`). Fine for one shared context; revisit only if multi-context is ever
  wanted.

## Transcription performance / quality

- **Per-model thread tuning + OpenMP wait policy.** `n_threads=4` suits Base on the Tensor G3; Tiny
  may prefer fewer. Consider setting `OMP_WAIT_POLICY=passive` (or building ggml without OpenMP) to
  stop spin-wait barriers burning CPU/heat. A/B on a *cool* device — thermal confounds results.
- **Chunked / streaming transcription for long field recordings.** `audio_ctx` fixed short clips,
  but long recordings still process in full 30s windows with no incremental feedback. Stream partial
  results to the UI.
- **ARM build portability.** The native build targets `armv8.2-a+dotprod+i8mm+fp16` — i8mm needs
  ~armv8.6, fine for Pixel 8 but would `SIGILL` on older arm64 devices. For wider distribution, add
  runtime CPU-feature detection or ship a conservative-baseline fallback.
- **Cosmetic:** the JNI `"x realtime on 30s window"` log line is now misleading since `audio_ctx`
  is usually < 1500 (it still assumes a 30s window). Report against the real `audio_ctx` window.

## Local notes / SAF (issue 5 follow-ups)

- **Surface local-folder notes in the app.** They're written to the user's SAF folder but don't
  appear in the Notes tab and `NoteViewScreen` can't open them (it reads `app.filesDir`). Add a
  read-only SAF listing / viewer if in-app visibility is wanted (currently they're managed in the
  user's own file app — by design).
- **Handle revoked SAF permission gracefully.** If the persisted tree URI permission is lost, saving
  currently throws. Detect and re-prompt for the folder.

## UX polish

- **Preserve in-progress selections when returning via the banner.** The transcription screen's note
  choice / new-filename / label edits are local Compose state and reset when you leave and return.
  Lift them into the ViewModel (or `TranscriptionManager`) so a round-trip keeps them.
- **"Transcribe again" on a lingering DONE job.** If a finished-but-unsaved job is still in the map,
  `TranscriptionManager.start()` returns early and shows the old result instead of re-running. Let an
  explicit re-transcribe force a fresh run.

## Testing

- **Cover the logic that accreted this session.** Unit/integration tests for `TranscriptionManager`
  (arming, the `saved` event, concurrency serialization), `LocalFolderNoteWriter`, the `WhisperModel`
  catalog, and `NoteImporter`. Right now `MarkdownManager` is essentially the only well-tested unit.

## Drive sync

- **Note conflict handling.** If a note is edited both locally and on Drive, `NoteImporter` keeps the
  local copy and just links the Drive id — no merge or "newer wins". Define real conflict semantics
  if multi-device editing becomes a thing.
