# FieldNotes — UI Module

## Design language

FieldNotes has a utilitarian, focused aesthetic — optimised for quick capture, not decoration.

- **Theme:** Dark by default (field recording contexts are often low-light)
- **Colors:** Near-black background (`#0D0D0D`), warm white text, a single accent — deep amber (`#E8A838`) for active/recording states, red (`#E83838`) for stop actions
- **Typography:** System default `Roboto Mono` for filenames/timestamps; Material 3 `Typography` elsewhere
- **Icons:** Material Symbols Rounded
- **Recording state:** The waveform and record button transform visually when active — the button pulses, the waveform becomes animated

## Navigation structure

```
NavHost
  ├── RecorderScreen (start destination)
  ├── RecordingsListScreen
  │   └── RecordingDetailScreen
  ├── TranscriptionScreen (launched after voice recording stops)
  ├── NotesListScreen
  │   └── NoteViewScreen
  └── SettingsScreen
```

Bottom navigation bar with 3 tabs:
1. 🎙 **Record** (RecorderScreen)
2. 📁 **Library** (RecordingsListScreen / NotesListScreen — tabbed)
3. ⚙️ **Settings**

## `RecorderScreen.kt`

The primary screen. Everything important is on one surface.

```
┌─────────────────────────────────────┐
│  FieldNotes              [Settings] │
│                                     │
│  ┌─── Input Source ───────────────┐ │
│  │  [● Built-in]  [ Headset ]     │ │  ← only shown when headset detected
│  └─────────────────────────────────┘ │
│                                     │
│  ┌─────────────────────────────────┐ │
│  │  ≋≋≋≋≋≋≋≋≋≋≋≋≋≋≋≋≋≋≋≋≋≋≋≋≋≋≋ │ │  ← animated waveform canvas
│  │  ≋≋≋≋≋≋≋≋≋≋≋≋≋≋≋≋≋≋≋≋≋≋≋≋≋≋≋ │ │
│  └─────────────────────────────────┘ │
│                                     │
│  00:00:00                           │  ← recording timer, hidden when idle
│                                     │
│  ┌──────────────┐  ┌──────────────┐ │
│  │  FIELD REC   │  │  VOICE NOTE  │ │  ← two large buttons
│  │  (lossless)  │  │  (+ transcr) │ │
│  └──────────────┘  └──────────────┘ │
│                                     │
│  [████████████████████] STOP        │  ← replaces both buttons while recording
└─────────────────────────────────────┘
```

### Key UI behaviours

- Both buttons are equal size, filling the bottom half of the screen
- When recording starts, both buttons animate out and a single full-width **STOP** button with a pulsing red indicator slides in
- The waveform shows amplitude bars in real-time (60fps canvas draw)
- Timer ticks up from 00:00:00
- If headset is connected, input source chips appear above the waveform

### `WaveformView.kt`

```kotlin
@Composable
fun WaveformView(
    amplitude: Float,  // 0f to 1f
    isRecording: Boolean,
    modifier: Modifier = Modifier
) {
    val amplitudeHistory = remember { mutableStateListOf<Float>() }
    val bars = 60
    
    LaunchedEffect(amplitude) {
        if (isRecording) {
            amplitudeHistory.add(amplitude)
            if (amplitudeHistory.size > bars) amplitudeHistory.removeFirst()
        }
    }
    
    Canvas(modifier = modifier.fillMaxWidth().height(80.dp)) {
        val barWidth = size.width / bars
        val maxHeight = size.height
        val color = if (isRecording) Color(0xFFE8A838) else Color(0xFF444444)
        
        amplitudeHistory.forEachIndexed { i, amp ->
            val barHeight = (amp * maxHeight).coerceAtLeast(4.dp.toPx())
            val x = i * barWidth + barWidth / 2
            drawLine(
                color = color,
                start = Offset(x, (maxHeight - barHeight) / 2),
                end = Offset(x, (maxHeight + barHeight) / 2),
                strokeWidth = barWidth * 0.6f,
                cap = StrokeCap.Round
            )
        }
    }
}
```

## `TranscriptionScreen.kt`

Shown automatically after a voice note recording stops and transcription completes.

```
┌─────────────────────────────────────┐
│  ← Back            Transcription   │
│                                     │
│  voice_20250115_143200.m4a          │
│  Duration: 1m 23s                   │
│                                     │
│  ┌── Transcription ───────────────┐ │
│  │  This is the transcribed text  │ │  ← editable TextField
│  │  from the recording. User can  │ │
│  │  correct any errors here.      │ │
│  └─────────────────────────────────┘ │
│                                     │
│  Save to note:                      │
│  ┌─────────────────────────────────┐ │
│  │  organiser.md              ▾   │ │  ← DropdownMenu of existing .md files
│  └─────────────────────────────────┘ │
│  Or create new:  [______________.md] │  ← text input, auto-appends .md
│                                     │
│  Labels:  [+ Add label]             │
│  [nature] [field] [×]               │
│                                     │
│  [  DISCARD  ]    [  SAVE NOTE  ]   │
└─────────────────────────────────────┘
```

### Transcription in progress state

Show a `CircularProgressIndicator` with text "Transcribing…" while `WhisperEngine.transcribe()` is running. The waveform file is available immediately; transcription runs asynchronously.

```kotlin
@Composable
fun TranscriptionScreen(
    viewModel: TranscriptionViewModel = hiltViewModel(),
    onSaved: () -> Unit,
    onDiscarded: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    
    when (val state = uiState) {
        is TranscriptionUiState.Transcribing -> {
            // Show progress
        }
        is TranscriptionUiState.Ready -> {
            // Show editable text, note selector, labels, save/discard buttons
        }
        is TranscriptionUiState.Saving -> {
            // Show progress
        }
    }
}
```

## `RecordingsListScreen.kt`

Two tabs: **Recordings** (audio files) and **Notes** (.md files).

### Recordings tab

List of `RecordingCard` components showing:
- Mode icon (🎙 field or 📝 voice)
- Filename and date
- Duration, file size
- Sync status icon (cloud with check / clock / error)
- Labels as chips
- Tap to open detail; long-press for context menu (delete, share, re-label)

### Notes tab

List of notes showing filename (display name), last modified date, sync status. Tap to view the Markdown content in a scrollable read-only view.

## `SettingsScreen.kt`

```
Google Drive
  [Connect Google Drive]           ← or shows email if connected
  Sync over WiFi only  [toggle ON]
  Pending uploads: 3              
  [Sync Now]

Audio
  Field recording format: [FLAC ▾] ← FLAC / WAV
  Transcription model:    [Base (142MB) ▾]  ← Base / Small
  [Download Small model]

Storage
  Used: 247 MB
  [Clear temp files]

About
  Version 1.0
```

## Compose navigation setup

```kotlin
@Composable
fun FieldNotesNavGraph(navController: NavHostController) {
    NavHost(navController, startDestination = "recorder") {
        composable("recorder") { RecorderScreen(navController) }
        composable("library") { LibraryScreen(navController) }
        composable(
            "transcription/{recordingId}",
            arguments = listOf(navArgument("recordingId") { type = NavType.StringType })
        ) { backStackEntry ->
            TranscriptionScreen(
                recordingId = backStackEntry.arguments!!.getString("recordingId")!!,
                onSaved = { navController.popBackStack() },
                onDiscarded = { navController.popBackStack() }
            )
        }
        composable("settings") { SettingsScreen() }
    }
}
```

## Permissions UI

Before first recording, request `RECORD_AUDIO`. Use a rationale dialog if permission was previously denied:

```kotlin
val permissionState = rememberPermissionState(Manifest.permission.RECORD_AUDIO)

if (!permissionState.status.isGranted) {
    PermissionRationaleDialog(
        onRequest = { permissionState.launchPermissionRequest() }
    )
}
```

## Accessibility

- All interactive elements have `contentDescription` set
- Minimum touch target size: 48dp (Material 3 default)
- The waveform canvas has `semantics { contentDescription = "Audio level meter" }`
