# FieldNotes

A privacy-first Android app for **field recording** (lossless FLAC/WAV) and **voice notes** with
fully on-device transcription (whisper.cpp) and optional Google Drive sync. Package
`com.fieldnotes.app`, min SDK 26, target SDK 36. Built and tested against a **Pixel 8**.

> Implementation/architecture details: see [`CLAUDE.md`](CLAUDE.md).
> Toolchain setup and the spec corrections that were required to compile: see [`BUILD_NOTES.md`](BUILD_NOTES.md).
> Original design specs: [`planning/`](planning/).

---

## Prerequisites

This project was set up on macOS (Apple Silicon) with Homebrew. You need:

- **JDK 17** — the system default JDK 26 is **not** supported by the Android Gradle Plugin.
- **Android SDK** with platform 36, build-tools 36, NDK 27.x, CMake 3.22.1.
- **adb** (ships with the SDK platform-tools).

On this machine they live here:

```bash
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export PATH="$ANDROID_HOME/platform-tools:$PATH"   # for adb
```

> The build JDK is also pinned via `org.gradle.java.home` in `gradle.properties` to a
> machine-specific path. If you build on a different machine, update or remove that line.

---

## 1. Build

```bash
# First time after cloning: pull the whisper.cpp submodule
git submodule update --init --recursive

# local.properties (NOT committed) — at minimum:
#   sdk.dir=/opt/homebrew/share/android-commandlinetools
#   drive.client.id=YOUR_CLIENT_ID.apps.googleusercontent.com   # optional; see planning/11_GOOGLE_CLOUD_SETUP.md

# Build the debug APK
./gradlew assembleDebug

# Run the unit tests
./gradlew test
```

APK output: **`app/build/outputs/apk/debug/app-debug.apk`**

Drive sync is optional — without `drive.client.id` the app works fully and queues uploads locally.

**If the build fails**, re-run with diagnostics and send the output to Claude:

```bash
./gradlew assembleDebug --stacktrace 2>&1 | tee build-error.txt
```

---

## 2. Install on your Pixel 8

### One-time phone setup
1. **Settings → About phone →** tap **Build number** 7 times to enable Developer options.
2. **Settings → System → Developer options →** turn on **USB debugging**.

### Install over USB
1. Plug the phone into the Mac. On the phone, tap **Allow** on the "Allow USB debugging?" prompt.
2. Confirm it's connected, then install:

```bash
adb devices                 # should list your Pixel 8 as "device" (not "unauthorized")
./gradlew installDebug      # builds (if needed) and installs
# — or install an already-built APK directly:
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Install over Wi-Fi (no cable, Android 11+)
```bash
# On the phone: Developer options → Wireless debugging → Pair device with pairing code
adb pair <phone-ip>:<pair-port>     # enter the 6-digit code shown on the phone
adb connect <phone-ip>:<debug-port> # use the IP:port from the Wireless debugging screen
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### First launch
- Grant **Microphone** and **Notifications** permissions when prompted (required to record).
- For transcription, open **Settings → Download base model** (≈142 MB, one-time). Field recording
  and voice capture work before the model is downloaded; only transcription waits on it.
- Optional extras: add the **home-screen widget** (Field Rec / Voice Note buttons) and the
  **Quick Settings tile** (a one-tap voice note from the pull-down shade).

---

## 3. What to test
- **Field Rec** → records lossless audio to `files/recordings/` (FLAC, or WAV if set in Settings).
- **Voice Note** → records AAC, then opens the transcription screen; pick/create a `.md` note to
  prepend the transcription into.
- **Library** tab → browse recordings and notes, with per-file sync status.
- **Widget / Quick Settings tile** → start a recording without opening the app (uses the
  foreground-service notification, which has a **Stop** button).
- **Settings** → format (FLAC/WAV), model choice, Wi-Fi-only sync, Drive connect, storage usage.

---

## 4. Sending logs back to Claude

If something misbehaves on the phone, capture a log and paste it (or the file) back into the chat.
The most reliable recipe — **clear, reproduce, dump**:

```bash
adb logcat -c                          # 1. clear the log buffer
# 2. reproduce the problem in the app (start a recording, hit the bug, etc.)
adb logcat -d > fieldnotes-log.txt     # 3. dump everything since the clear
```

Then send `fieldnotes-log.txt`. Useful narrower captures:

```bash
# Only this app's process (best signal; run while the app is in the foreground):
adb logcat --pid="$(adb shell pidof -s com.fieldnotes.app)" > fieldnotes-log.txt

# Only crashes / fatal exceptions:
adb logcat -b crash -d > fieldnotes-crash.txt

# This app's own log tags (verbose+) plus all errors:
adb logcat -d WhisperJNI:V AudioEncoder:V RecordingSessionManager:V *:E > fieldnotes-tags.txt
```

When reporting, please include:
- **What you did** and **what happened** vs. what you expected.
- The **log file** (or the relevant excerpt — look for lines containing `FATAL EXCEPTION`,
  `E/AndroidRuntime`, or one of the tags above).
- Whether it was a **crash**, a **hang**, or **wrong behaviour**.

> Logs may contain transcribed note text and file paths — skim before sharing if any note content
> is sensitive.
