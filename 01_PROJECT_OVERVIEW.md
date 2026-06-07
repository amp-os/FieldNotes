# FieldNotes — Project Overview

## What is FieldNotes?

FieldNotes is a privacy-focused Android application for two complementary recording workflows:

1. **Field Recording** — Capture high-quality, lossless environmental audio (nature, ambience, sound design source material). Files are archived and backed up to Google Drive.
2. **Voice Notes** — Capture spoken notes with fully on-device transcription (Whisper). Transcriptions are appended to user-named Markdown files and backed up to Drive.

## Core design principles

- **Privacy by default** — No audio ever leaves the device for processing. Transcription is 100% on-device.
- **Speed of capture** — The primary recording action must be reachable in ≤2 taps from any context (home screen widget, lock screen notification, or app foreground).
- **Unified interface** — A single recorder surface with two modes, not two separate apps or deeply nested menus.
- **Plain text output** — Voice note transcriptions are stored as Markdown. No proprietary format lock-in.
- **Graceful offline** — The app is fully functional with no internet. Drive sync is opportunistic.

## User stories

### Recording
- As a user, I can open the app (or tap a widget) and immediately start a **high-quality** or **voice note** recording with a single tap.
- As a user, I can see a live waveform/level meter while recording so I know the mic is active.
- As a user, I can stop a recording and immediately see its duration and file size.
- As a user, I can name/label a recording before or after capture.
- As a user, I can select the audio input source (built-in mic vs wired headset mic) when a headset is connected.

### Voice notes & transcription
- As a user, after stopping a voice note recording, transcription starts automatically in the background.
- As a user, I can see transcription progress and the result when complete.
- As a user, I can select an existing Markdown filename from a dropdown, and the transcription (with a date/time heading) will be **prepended** to that file.
- As a user, I can type a new filename to create a new Markdown note.
- As a user, I can edit the transcription before saving it.

### Organisation
- As a user, I can assign one or more flat **labels** to any recording or transcription.
- As a user, I can browse recordings filtered by label.

### Sync
- As a user, field recordings are automatically uploaded to a `FieldNotes/recordings/` folder in Google Drive.
- As a user, voice note audio files are uploaded to `FieldNotes/voice/`.
- As a user, Markdown transcription files are uploaded/synced to `FieldNotes/notes/`.
- As a user, I can configure sync to only happen over WiFi.
- As a user, I can see a sync status indicator (pending / syncing / synced / error) per file.

### Widget & quick access
- As a user, I can add a home screen widget with two buttons: **Field Rec** and **Voice Note**, that immediately start recording in the respective mode.
- As a user, a persistent foreground-service notification appears while recording, with a stop button.

## Out of scope (v1)
- Real-time/live transcription
- Editing audio files
- Sharing to anything other than Google Drive
- Multi-device sync or collaboration
- Importing existing Markdown notes from Drive (future v2)

## App name & package
- **App name:** FieldNotes
- **Package:** `com.fieldnotes.app`

## Folder structure on Google Drive

```
FieldNotes/
  recordings/        ← high-quality field recordings (.flac or .wav)
  voice/             ← voice note audio (.m4a / AAC-LC)
  notes/             ← markdown transcription files (.md)
```
