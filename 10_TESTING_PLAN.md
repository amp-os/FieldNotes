# FieldNotes — Testing Plan

## Test strategy

| Layer | Framework | Location |
|-------|-----------|---------|
| Unit tests (pure logic) | JUnit 5 + MockK | `src/test/` |
| Integration tests (Room, coroutines) | JUnit 5 + Hilt Test + Robolectric | `src/test/` |
| UI tests | Compose Testing + Espresso | `src/androidTest/` |
| Manual / device tests | Physical Pixel 8 | n/a |

## Unit tests

### `MarkdownManagerTest`

The most critical unit to test — the prepend logic must be correct.

```kotlin
class MarkdownManagerTest {
    private lateinit var tempDir: File
    private lateinit var manager: MarkdownManager

    @BeforeEach fun setup() {
        tempDir = createTempDir()
        // inject mock LocalFileManager pointing to tempDir
    }

    @Test fun `creates new file with title and entry`() {
        manager.prependEntry("organiser", "Hello world", timestamp = 1700000000000L)
        val content = File(tempDir, "notes/organiser.md").readText()
        assertThat(content).startsWith("# Organiser")
        assertThat(content).contains("Hello world")
    }

    @Test fun `prepends to existing file below H1`() {
        val file = File(tempDir, "notes/organiser.md")
        file.writeText("# Organiser\n\n## 2024-01-01 — 10:00\n\nOld note.\n")
        manager.prependEntry("organiser", "New note", timestamp = 1700000000000L)
        val content = file.readText()
        // New entry should appear before old entry
        val newPos = content.indexOf("New note")
        val oldPos = content.indexOf("Old note")
        assertThat(newPos).isLessThan(oldPos)
    }

    @Test fun `sanitises dangerous filenames`() {
        manager.prependEntry("../../../etc/passwd", "Attack", timestamp = 0L)
        // Should not write outside notesDir
        assertThat(File("/etc/passwd")).doesNotExist()
    }

    @Test fun `appends dot md if missing`() {
        manager.prependEntry("myfile", "text", 0L)
        assertThat(File(tempDir, "notes/myfile.md")).exists()
    }
}
```

### `AudioInputRouterTest`

```kotlin
class AudioInputRouterTest {
    @Test fun `returns built-in mic always`() {
        val router = AudioInputRouter(mockContext)
        assertThat(router.availableSources().map { it.second })
            .contains("Built-in Microphone")
    }
}
```

### `SyncQueueTest` (Room + Robolectric)

```kotlin
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
class SyncQueueTest {
    @get:Rule val hiltRule = HiltAndroidRule(this)
    
    @Inject lateinit var db: FieldNotesDatabase
    
    @BeforeEach fun setup() { hiltRule.inject() }
    
    @Test fun `enqueue and retrieve pending items`() = runTest {
        db.syncQueueDao().enqueue(SyncQueueEntity(
            filePath = "/tmp/test.flac",
            fileType = "FIELD_RECORDING",
            driveFolderName = "recordings"
        ))
        val pending = db.syncQueueDao().getPendingItems()
        assertThat(pending).hasSize(1)
        assertThat(pending[0].filePath).isEqualTo("/tmp/test.flac")
    }
}
```

## Integration tests

### `RecordingRepositoryTest`

Test the full recording save flow without Drive (DriveSync stubbed out):

```kotlin
@Test fun `saving voice note creates metadata and queues sync`() = runTest {
    // Arrange: stub DriveSync, real Room DB
    // Act: repository.saveVoiceNote(mockAudioFile, duration = 60_000)
    // Assert:
    //   - RecordingEntity exists in DB with mode=VOICE_NOTE
    //   - SyncQueueEntity created with fileType=VOICE_NOTE
    //   - Audio file exists on disk
}
```

### `WhisperEngineTest`

Smoke test using a known audio fixture. This test is slow (~10s) and should be tagged `@Tag("slow")` to exclude from fast test runs.

```kotlin
@Tag("slow")
@Test fun `transcribes short english phrase`() = runTest {
    // Requires the base model to be present at test/resources/ggml-base.en.bin
    // or skip if model unavailable
    assumeTrue(modelFile.exists())
    val result = whisperEngine.transcribe(File("src/test/resources/test_hello.wav"))
    assertThat(result.text.lowercase()).contains("hello")
}
```

Ship a test WAV file (`src/test/resources/test_hello.wav`) — a 2-second clip saying "hello world".

## UI tests (Compose)

### `RecorderScreenTest`

```kotlin
@HiltAndroidTest
class RecorderScreenTest {
    @get:Rule val composeTestRule = createAndroidComposeRule<MainActivity>()
    
    @Test fun `two record buttons visible on launch`() {
        composeTestRule.onNodeWithText("FIELD REC").assertIsDisplayed()
        composeTestRule.onNodeWithText("VOICE NOTE").assertIsDisplayed()
    }
    
    @Test fun `tapping record button requests microphone permission`() {
        // Grant permission first via UiAutomator
        composeTestRule.onNodeWithText("FIELD REC").performClick()
        // Assert permission request dialog appeared OR recording started
    }
}
```

### `TranscriptionScreenTest`

```kotlin
@Test fun `save button disabled until note filename entered`() {
    // Navigate to transcription screen with mock data
    composeTestRule.onNodeWithText("SAVE NOTE").assertIsNotEnabled()
    composeTestRule.onNodeWithTag("new_note_input").performTextInput("test")
    composeTestRule.onNodeWithText("SAVE NOTE").assertIsEnabled()
}
```

## Running tests

```bash
# Fast unit + integration tests
./gradlew test

# Slow tests only (Whisper)
./gradlew test -Dtag=slow

# UI tests on emulator
./gradlew connectedAndroidTest

# All tests
./gradlew test connectedAndroidTest
```

## Test coverage target

- `MarkdownManager`: 100% line coverage
- `LocalFileManager`: 80%+
- `RecordingRepository`: 70%+
- UI screens: smoke tests (at least one test per screen)

## Manual test checklist (on Pixel 8)

Before each release, verify:

- [ ] Field recording starts and stops; .flac file exists in files/recordings/
- [ ] Voice note starts and stops; .m4a file exists in files/voice/
- [ ] Transcription completes within 30s for a 1-minute recording
- [ ] Transcription is prepended correctly to an existing .md file
- [ ] A new .md file is created when a new name is entered
- [ ] Home screen widget appears after adding; both buttons start recording
- [ ] Quick Settings tile appears and starts voice note recording
- [ ] Notification Stop button correctly stops recording
- [ ] Headset mic source selector appears when headset plugged in
- [ ] Drive sync uploads field recording to Drive/FieldNotes/recordings/
- [ ] Drive sync uploads note to Drive/FieldNotes/notes/
- [ ] WiFi-only mode: sync queued when on mobile data, fires when WiFi connects
- [ ] App works fully with no Drive credentials configured
