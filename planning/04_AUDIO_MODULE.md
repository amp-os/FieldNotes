# FieldNotes — Audio Module

## Overview

The audio module handles:
1. Capturing raw PCM audio from the device microphone
2. Encoding to the target format (FLAC or WAV for field; AAC-LC for voice)
3. Routing audio input (built-in mic vs wired headset mic)
4. Exposing amplitude data for the waveform UI

## `RecordingMode.kt`

```kotlin
enum class RecordingMode {
    FIELD,       // High-quality, lossless, 48kHz
    VOICE_NOTE   // Compressed, 16kHz, optimised for Whisper
}
```

## `AudioInputRouter.kt`

Monitors `AudioManager` for headset connect/disconnect events and exposes available input sources.

```kotlin
@Singleton
class AudioInputRouter @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val audioManager = context.getSystemService(AudioManager::class.java)

    /** Returns available audio sources as display-friendly pairs */
    fun availableSources(): List<Pair<Int, String>> {
        val sources = mutableListOf(
            AudioSource.MIC to "Built-in Microphone"
        )
        // Check if wired headset with mic is connected
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
        val hasWiredMic = devices.any { 
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET || 
            it.type == AudioDeviceInfo.TYPE_USB_HEADSET 
        }
        if (hasWiredMic) {
            sources.add(AudioSource.UNPROCESSED to "Headset Microphone")
        }
        return sources
    }

    /** Returns a Flow that emits true when a mic-capable headset is connected */
    fun headsetMicConnected(): Flow<Boolean> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val state = intent.getIntExtra("state", -1)
                val hasMic = intent.getIntExtra("microphone", 0) == 1
                trySend(state == 1 && hasMic)
            }
        }
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_HEADSET_PLUG))
        awaitClose { context.unregisterReceiver(receiver) }
    }
}
```

## `AudioRecorder.kt`

Core recording engine using `AudioRecord`. Writes raw PCM to a temp file and emits amplitude for waveform display.

```kotlin
@Singleton
class AudioRecorder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val localFileManager: LocalFileManager
) {
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    
    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()
    
    private val _state = MutableStateFlow<RecorderState>(RecorderState.Idle)
    val state: StateFlow<RecorderState> = _state.asStateFlow()

    // Field recording: 48000Hz stereo 16-bit
    // Voice note: 16000Hz mono 16-bit (Whisper native)
    fun start(
        mode: RecordingMode,
        audioSource: Int = AudioSource.MIC,
        scope: CoroutineScope
    ): File {
        val (sampleRate, channels, channelConfig) = when (mode) {
            RecordingMode.FIELD -> Triple(48000, 2, AudioFormat.CHANNEL_IN_STEREO)
            RecordingMode.VOICE_NOTE -> Triple(16000, 1, AudioFormat.CHANNEL_IN_MONO)
        }
        
        val bufferSize = AudioRecord.getMinBufferSize(
            sampleRate, channelConfig, AudioFormat.ENCODING_PCM_16BIT
        ) * 4 // 4x for safety margin
        
        val pcmFile = localFileManager.newTempFile("recording_${System.currentTimeMillis()}.pcm")

        val record = AudioRecord(
            audioSource, sampleRate, channelConfig,
            AudioFormat.ENCODING_PCM_16BIT, bufferSize
        )
        audioRecord = record
        record.startRecording()
        _state.value = RecorderState.Recording(mode, sampleRate, channels)
        
        recordingJob = scope.launch(Dispatchers.IO) {
            val buffer = ShortArray(bufferSize / 2)
            pcmFile.outputStream().buffered().use { out ->
                while (isActive) {
                    val read = record.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        // Write PCM bytes
                        val bytes = ByteArray(read * 2)
                        for (i in 0 until read) {
                            bytes[i * 2] = (buffer[i].toInt() and 0xFF).toByte()
                            bytes[i * 2 + 1] = (buffer[i].toInt() shr 8).toByte()
                        }
                        out.write(bytes)
                        // Amplitude: RMS of buffer
                        val rms = sqrt(buffer.take(read).map { it.toDouble().pow(2) }
                            .average()).toFloat()
                        _amplitude.value = rms / Short.MAX_VALUE
                    }
                }
            }
        }
        return pcmFile
    }

    fun stop(): File {
        recordingJob?.cancel()
        audioRecord?.apply {
            stop()
            release()
        }
        audioRecord = null
        val state = _state.value
        _state.value = RecorderState.Idle
        return (state as RecorderState.Recording).pcmFile
    }
}

sealed class RecorderState {
    object Idle : RecorderState()
    data class Recording(
        val mode: RecordingMode,
        val sampleRate: Int,
        val channels: Int,
        val pcmFile: File = File("") // set after start()
    ) : RecorderState()
}
```

## `AudioEncoder.kt`

### Field recording: PCM → FLAC

Use Android's `MediaCodec` with FLAC codec (supported API 21+):

```kotlin
suspend fun encodeToFlac(
    pcmFile: File,
    outputFile: File,
    sampleRate: Int,
    channels: Int
): File = withContext(Dispatchers.IO) {
    // Use MediaMuxer + MediaCodec FLAC encoder
    val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_FLAC, sampleRate, channels)
    format.setInteger(MediaFormat.KEY_BIT_RATE, 0) // lossless — bitrate ignored
    format.setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
    format.setInteger(MediaFormat.KEY_FLAC_COMPRESSION_LEVEL, 5) // 0-8; 5 is balanced

    val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_FLAC)
    codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
    codec.start()
    
    // Feed PCM data from pcmFile into codec, write output to outputFile
    // ... standard MediaCodec encode loop with BUFFER_FLAG_END_OF_STREAM
    // Full implementation follows standard MediaCodec async or sync pattern
    
    codec.stop()
    codec.release()
    pcmFile.delete() // clean up temp file
    outputFile
}
```

> **WAV fallback:** If user has selected WAV in Settings, skip encoding entirely — just write a WAV header (44 bytes) prepended to the raw PCM data. This is fast and trivial. Provide `fun writeWavHeader(pcmFile: File, outputFile: File, sampleRate: Int, channels: Int)`.

### Voice note: Use `MediaRecorder` directly

For voice notes, avoid `AudioRecord` altogether and use `MediaRecorder` which handles AAC encoding natively with no JNI:

```kotlin
fun startVoiceRecording(outputFile: File): MediaRecorder {
    return MediaRecorder(context).apply {
        setAudioSource(MediaRecorder.AudioSource.MIC)
        setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        setAudioSamplingRate(16000)
        setAudioChannels(1)
        setAudioEncodingBitRate(128000)
        setOutputFile(outputFile.absolutePath)
        prepare()
        start()
    }
}
```

> Note: `MediaRecorder` doesn't give us raw PCM for amplitude display. Use `getMaxAmplitude()` polled every 100ms for the waveform.

## `RecordingService.kt` (Foreground Service)

The recording must survive app backgrounding. Use a foreground service.

```kotlin
@AndroidEntryPoint
class RecordingService : Service() {
    private val NOTIFICATION_ID = 1001
    private val CHANNEL_ID = "recording_channel"
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_FIELD -> startRecording(RecordingMode.FIELD)
            ACTION_START_VOICE -> startRecording(RecordingMode.VOICE_NOTE)
            ACTION_STOP -> stopRecording()
        }
        return START_STICKY
    }
    
    private fun startRecording(mode: RecordingMode) {
        startForeground(NOTIFICATION_ID, buildNotification(mode, recording = true))
        // ... delegate to AudioRecorder or MediaRecorder
    }
    
    private fun buildNotification(mode: RecordingMode, recording: Boolean): Notification {
        // Shows: "● Recording — Field" or "● Recording — Voice Note"
        // Action button: Stop
        val stopIntent = PendingIntent.getService(...)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FieldNotes")
            .setContentText(if (mode == RecordingMode.FIELD) "● Field Recording" else "● Voice Note")
            .setSmallIcon(R.drawable.ic_mic)
            .addAction(R.drawable.ic_stop, "Stop", stopIntent)
            .setOngoing(true)
            .build()
    }
    
    companion object {
        const val ACTION_START_FIELD = "START_FIELD"
        const val ACTION_START_VOICE = "START_VOICE"  
        const val ACTION_STOP = "STOP"
    }
}
```

## Input source selection UI

When a headset mic is detected (via `AudioInputRouter.headsetMicConnected()`), the `RecorderScreen` shows a small dropdown or chip selector:

```
[🎙 Built-in]  [🎧 Headset]
```

Tapping selects the source for the next recording. Selection persists in DataStore preferences.

## Permissions

Request `RECORD_AUDIO` at runtime before first recording attempt using the Accompanist Permissions library or the `rememberPermissionState` Compose API.

For foreground service: `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_MICROPHONE` are declared in manifest; no runtime request needed on API 34+ if the foreground service type is `microphone`.
