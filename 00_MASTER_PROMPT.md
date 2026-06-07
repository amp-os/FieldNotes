# FieldNotes — Master Build Prompt for Claude Code

You are the lead architect for **FieldNotes**, an Android application. Your job is to read all documents in this directory and orchestrate a series of sub-agents to build the complete application. Do not begin writing code until you have read every document listed below.

## Documents in this project

| File | Purpose |
|------|---------|
| `01_PROJECT_OVERVIEW.md` | Goals, user stories, design principles |
| `02_ARCHITECTURE.md` | Module structure, tech stack decisions, data flow |
| `03_SETUP_ENVIRONMENT.md` | Android project init, Gradle config, dependencies |
| `04_AUDIO_MODULE.md` | Recording engine, codec selection, mic routing |
| `05_WHISPER_MODULE.md` | On-device transcription via whisper.cpp JNI |
| `06_STORAGE_MODULE.md` | Local file storage, markdown management |
| `07_DRIVE_SYNC_MODULE.md` | Google Drive API integration, sync logic |
| `08_UI_MODULE.md` | Jetpack Compose UI, screens, navigation |
| `09_WIDGET_MODULE.md` | Home screen widget via Jetpack Glance |
| `10_TESTING_PLAN.md` | Unit tests, integration tests, emulator setup |
| `11_GOOGLE_CLOUD_SETUP.md` | Human-facing: Google Cloud & OAuth setup instructions |

## Sub-agent task assignments

Spawn the following sub-agents **in this order**, waiting for each to complete before proceeding to the next unless stated as parallelisable:

### Phase 1 — Project scaffold (sequential)
1. **Agent: project-init** → Read `03_SETUP_ENVIRONMENT.md`. Create the Android project using Gradle, configure all dependencies, create the package structure, and commit an initial build that compiles with zero errors. Use `./gradlew assembleDebug` to verify.

### Phase 2 — Core modules (parallelisable after Phase 1)
2. **Agent: audio** → Read `02_ARCHITECTURE.md` + `04_AUDIO_MODULE.md`. Implement the recording engine.
3. **Agent: whisper** → Read `02_ARCHITECTURE.md` + `05_WHISPER_MODULE.md`. Integrate whisper.cpp via JNI and implement the transcription service.
4. **Agent: storage** → Read `02_ARCHITECTURE.md` + `06_STORAGE_MODULE.md`. Implement local file management and markdown operations.

### Phase 3 — Integration (sequential, after Phase 2)
5. **Agent: drive-sync** → Read `07_DRIVE_SYNC_MODULE.md`. Implement Google Drive sync. Note: OAuth client ID must be injected via `local.properties` (see `11_GOOGLE_CLOUD_SETUP.md`). Build a mock/stub mode that runs without credentials so UI can be tested independently.
6. **Agent: ui** → Read `08_UI_MODULE.md`. Implement all Compose screens, wire to modules from Phase 2.

### Phase 4 — Extras & polish (parallelisable)
7. **Agent: widget** → Read `09_WIDGET_MODULE.md`. Implement the Glance home screen widget.
8. **Agent: testing** → Read `10_TESTING_PLAN.md`. Write and run the test suite.

### Phase 5 — Final integration
9. **Agent: integrator** → Wire all modules together. Run `./gradlew assembleDebug` and resolve any remaining build errors. Produce a final APK at `app/build/outputs/apk/debug/app-debug.apk`.

## Key constraints for all agents

- **Min SDK: 26** (Android 8.0). **Target SDK: 36** (Android 16). Test device is a Pixel 8 running Android 16.
- All code in **Kotlin**. UI in **Jetpack Compose**. No XML layouts.
- All transcription must be **fully offline** — no network calls for audio data.
- The app must compile and run without a Google Cloud OAuth credential (Drive sync degrades gracefully to a "pending upload" queue).
- Follow **MVVM** with a repository pattern. Use **Hilt** for dependency injection.
- Use **coroutines + Flow** throughout — no RxJava.
- Every file should have a header comment stating which agent created it and which spec document it implements.
