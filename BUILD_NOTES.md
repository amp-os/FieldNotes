# FieldNotes — Build Notes & Spec Corrections

This file records how the project was built from the planning specs, the toolchain that was
installed, and the deviations from the spec documents that were necessary to get a green build.
Authored by the lead-architect build session.

## Toolchain installed on this machine

The machine had no Android toolchain. The following were installed (Apple Silicon, macOS):

| Component | Version | Location |
|-----------|---------|----------|
| JDK (build) | Temurin/OpenJDK **17** | `/opt/homebrew/opt/openjdk@17` (Homebrew formula, no sudo) |
| Android cmdline-tools | latest | `/opt/homebrew/share/android-commandlinetools` |
| SDK Platform | android-36 | via `sdkmanager` |
| Build-Tools | 36.0.0 | |
| NDK | 27.2.12479018 | |
| CMake | 3.22.1 | |
| Gradle (wrapper) | 8.11.1 | `./gradlew` |

> The system default JDK is 26, which AGP/Gradle do not support. The build JDK is pinned to 17 via
> `org.gradle.java.home` in `gradle.properties`. If you move the project, update that path (or remove
> the line and set `JAVA_HOME` to a JDK 17 before building).

`local.properties` must contain `sdk.dir` and `drive.client.id` (not committed).

## How to build

```bash
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
git submodule update --init --recursive   # whisper.cpp
./gradlew assembleDebug                    # -> app/build/outputs/apk/debug/app-debug.apk
./gradlew test                             # fast unit tests (JUnit5 + Robolectric)
./gradlew connectedAndroidTest             # Compose UI tests (needs device/emulator)
```

## Corrections vs the spec documents

These were required because the spec versions/APIs do not build as written:

1. **AGP 8.5.0 → 8.9.1** (`planning/03_SETUP_ENVIRONMENT.md`). AGP 8.5 cannot compile against `compileSdk 36`
   (Android 16). 8.9.x adds API-36 support. Gradle bumped to 8.11.1 to match.
2. **Kotlin 2.0.0 + Compose compiler plugin** (`planning/03_SETUP_ENVIRONMENT.md`). Under Kotlin 2.0 the
   Compose compiler is a Gradle plugin (`org.jetbrains.kotlin.plugin.compose`). The spec's
   `composeOptions.kotlinCompilerExtensionVersion` is obsolete and was removed.
3. **`gradleLocalProperties(...)` internal API → plain `Properties` loader** for `DRIVE_CLIENT_ID`.
4. **whisper.cpp CMake flags `WHISPER_NO_AVX/AVX2/FMA` → `GGML_*`** (`-DGGML_AVX=OFF` etc.).
   Modern whisper.cpp (v1.8.x) renamed these; the old names were silently ignored.
5. **`Labels.toLabelsJson()`** needed an explicit type argument for `Json.encodeToString`.
6. **`AudioRecorder.stop()` bug**: the spec stored an empty `File("")` in `RecorderState.Recording`,
   so `stop()` returned an invalid path. Fixed to carry the real PCM file.
7. **`SyncWorker` status update** keyed recordings by `file.nameWithoutExtension` (never matches the
   UUID primary key). Changed to update by `filePath`.
8. **Drive OAuth uses AppAuth** (`net.openid:appauth`), not the spec's hand-rolled flow. The original
   manual `HttpURLConnection` flow used the redirect `com.fieldnotes.app:/oauth2callback`, which Google
   rejects for a native client. AppAuth handles Custom Tabs + PKCE + token exchange/refresh and uses
   Google's **reverse-client-ID** redirect scheme (`com.googleusercontent.apps.<id>:/oauth2redirect`),
   derived from `drive.client.id` at build time and injected as the `appAuthRedirectScheme` manifest
   placeholder + `BuildConfig.DRIVE_REDIRECT_SCHEME`. Tokens are persisted as an AppAuth `AuthState`.
   The Google Cloud client must be type **Android** (package name + debug/release SHA-1).
9. **Glance widget** uses clickable `Box`/`Text` instead of `Button(colors=…)` for cross-version
   stability of the Glance API.
10. **Encoder**: the spec left the FLAC `MediaCodec` loop and Whisper decode loop as `…`. Both are
    implemented here (FLAC writes a valid raw stream: `fLaC` marker + STREAMINFO + frames, with a WAV
    fallback; decode uses `MediaExtractor`+`MediaCodec` → downmix → linear resample → float32).

## Known limitations / things needing a device

- **Drive sync** requires a real OAuth client id in `local.properties` and on-device sign-in. Without
  it the app degrades gracefully: everything works locally and uploads queue in Room (verified by
  `SyncWorker` returning success when `canSync()` is false).
- **Transcription** needs the `ggml-base.en.bin` model, downloaded on first use (Settings → Download
  base model, or the model-missing prompt on the transcription screen).
- **Robolectric** unit test (`SyncQueueTest`) is pinned to `@Config(sdk = [34])` — Robolectric 4.13
  does not ship an android-all image for API 36.
- The OAuth `drive.client.id` originally in `local.properties` had a duplicated
  `.apps.googleusercontent.com` suffix; it was corrected to a single suffix. **Verify it matches your
  Google Cloud client id.**
