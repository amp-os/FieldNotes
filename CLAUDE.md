# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project status

The Android project has been scaffolded and builds (see `BUILD_NOTES.md` for the toolchain and the spec corrections that were required to compile). The original specification documents live in `planning/`; `planning/00_MASTER_PROMPT.md` describes the intended build orchestration strategy. Outstanding ideas/backlog live in `FUTURE_WORK.md`.

## What is being built

**FieldNotes** — an Android app (`com.fieldnotes.app`, min SDK 26, target SDK 36) with two recording modes:
- **Field Recording**: lossless FLAC/WAV audio at 48kHz, archived to Google Drive
- **Voice Notes**: AAC-LC at 16kHz, with fully on-device transcription via whisper.cpp, output as Markdown files synced to Drive

Privacy-first: no audio ever leaves the device for processing.

## Build commands

Building requires **JDK 17** (the system default JDK 26 is unsupported by AGP) and `ANDROID_HOME` set. The build JDK is pinned via `org.gradle.java.home` in `gradle.properties` to a machine-specific path — update/remove it if building elsewhere. See `README.md` / `BUILD_NOTES.md`.

```bash
# Debug build (verifies compilation)
./gradlew assembleDebug

# Fast unit + integration tests
./gradlew test

# Slow tests only (Whisper transcription — requires model file)
./gradlew test -Dtag=slow

# UI tests (requires emulator or device)
./gradlew connectedAndroidTest
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

## One-time setup

whisper.cpp is already a submodule (`app/src/main/cpp/whisper.cpp`) — after cloning, just initialise it:

```bash
git submodule update --init --recursive
```

Add to `local.properties` (never commit this file):
```properties
sdk.dir=/path/to/Android/Sdk
drive.client.id=YOUR_OAUTH_CLIENT_ID.apps.googleusercontent.com
```
See `planning/11_GOOGLE_CLOUD_SETUP.md` for how to obtain the OAuth client ID.

## Architecture

**Pattern:** MVVM + Repository, Hilt DI, Coroutines + StateFlow/SharedFlow throughout (no RxJava).

**Key design decisions in the specs:**
- whisper.cpp is integrated via JNI (CMake submodule at `app/src/main/cpp/whisper.cpp/`, currently v1.8.6). The JNI bridge is `whisper_jni.cpp`. For x86_64 emulator compatibility the CMake build passes `-DGGML_AVX=OFF -DGGML_AVX2=OFF -DGGML_FMA=OFF -DGGML_F16C=OFF -DGGML_NATIVE=OFF` (set in `app/build.gradle.kts`). NOTE: the spec's `WHISPER_NO_AVX*` flags are obsolete and silently ignored by current whisper.cpp.
- **The whisper native build is page-size- and ABI-sensitive (on-device lessons):**
  - **16KB page alignment is mandatory** on Pixel 8 / Android 15+ (16KB-page kernel). whisper.cpp's own libs (`libggml-*.so`, `libwhisper.so`) otherwise link 4KB-aligned and **SIGSEGV in `ggml_compute_forward_mul_mat`**. Fix: `-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON` in `build.gradle.kts` **and** a global `add_link_options("-Wl,-z,max-page-size=16384")` placed *before* `add_subdirectory(whisper.cpp)`. A per-target link option only aligns `libfieldnotes-jni.so`. Verify: `llvm-readelf -lW <so>` → all LOAD `Align 0x4000`.
  - Quantized models need ARM int8 kernels: with `GGML_NATIVE=OFF` the build defaults to generic armv8-a, so q5_1 falls back to scalar (slower than f16). `-march=armv8.2-a+dotprod+i8mm+fp16` (arm64-v8a only) fixes it; `i8mm` needs ~armv8.6 (Pixel 8 ok, would SIGILL on older arm64).
  - `n_threads=4` (the four A715 cores), not 6 — 6 pulls in the slow A510 cores and OpenMP spin-wait barriers stall the fast ones.
  - Short clips set `params.audio_ctx` ≈ audio length (whisper otherwise always runs a full 30s window) — the biggest short-clip speedup.
- `WhisperEngine` holds a **single shared `whisper_context`**: it loads the user's *selected* model, reloads on change, and serializes `transcribe()` with a mutex (concurrent runs corrupt the context). Transcription runs in the app-scoped `TranscriptionManager` (survives navigation); saves signal completion via a `saved` SharedFlow, not via the job map.
- Drive sync degrades gracefully: the app must fully function without OAuth credentials configured. All pending uploads queue in Room (`sync_queue` table) and fire via WorkManager (`SyncWorker`).
- **Drive OAuth uses AppAuth** (Custom Tabs + PKCE; `DriveAuthManager`, reverse-client-ID redirect). Two non-obvious requirements: (1) the app's XML theme must descend from **`Theme.AppCompat`** — AppAuth's `RedirectUriReceiverActivity` is an AppCompat activity and crashes under a plain Material theme; (2) the Android OAuth client in Google Cloud must have **"Custom URI scheme" enabled** (Advanced Settings), or Google blocks the request ("Access blocked: request is invalid"). See `BUILD_NOTES.md`.
- `DRIVE_CLIENT_ID` is injected as a `BuildConfig` field from `local.properties` at build time.
- Field recordings use `AudioRecord` (raw PCM → FLAC via Android `MediaCodec`, not JNI); voice notes use `MediaRecorder` (AAC-LC directly). The two paths are intentionally different. (JNI is used only for whisper transcription.)
- Voice notes are recorded at 16kHz mono to match Whisper's native input — no resampling needed for transcription.
- Markdown files use a prepend pattern: new transcription entries go *above* existing entries, below the H1 title.

**Package layout:** `com.fieldnotes.app/{core/{audio,whisper,storage,sync}, data/{db,repository}, ui/{recorder,recordings,transcription,notes,settings,common}, widget, service, di}`

**Room tables:** `recordings`, `notes`, `sync_queue` — see `planning/02_ARCHITECTURE.md` for full schema.

**Local file storage:** `app.filesDir/{recordings/, voice/, notes/, whisper/, temp/}`

**Drive folder structure:** `FieldNotes/{recordings/, voice/, notes/}`

## Critical constraints

- All code Kotlin only. UI is Jetpack Compose (Material 3) — no XML layouts.
- A **microphone foreground service cannot be started from the background** (Android 12+). The home-screen widget and Quick Settings tiles launch recording via the `RecordWidgetActionActivity` trampoline (a visible Activity), not by calling `startForegroundService` directly — don't bypass it.
- ABI targets: `arm64-v8a` (Pixel 8) + `x86_64` (emulator).
- Every source file should include a header comment naming which spec document it implements.
- `MarkdownManager.prependEntry()` must sanitise filenames (path traversal prevention) — see test cases in `planning/10_TESTING_PLAN.md`.
- `MarkdownManager` has a 100% line coverage target; it is the most critical unit to test.
- The Room DB uses `fallbackToDestructiveMigration()` — **any schema change wipes all user data**. Add real migrations before changing entities (this is why local-folder notes avoid a schema change).
