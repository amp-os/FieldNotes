# FieldNotes — Whisper Transcription Module

## Overview

Transcription uses [whisper.cpp](https://github.com/ggerganov/whisper.cpp) compiled for Android ARM64 via JNI. This is fully offline — no audio ever leaves the device.

## Model selection

| Model | Size on disk | RAM usage | Speed on Pixel 8 | Accuracy |
|-------|-------------|-----------|-------------------|----------|
| tiny | 75 MB | ~125 MB | ~3x realtime | Fair |
| base | 142 MB | ~210 MB | ~6x realtime | Good |
| small | 466 MB | ~600 MB | ~15x realtime | Excellent |
| medium | 1.5 GB | ~1.7 GB | too slow | — |

**Recommendation:** Ship with `ggml-base.en.bin` (English-only base model, 142MB). Allow user to download `small.en` (466MB) in Settings for better accuracy. The `.en` variants are faster than multilingual equivalents.

> "6x realtime" means a 60-second recording transcribes in ~10 seconds. Acceptable for a post-recording workflow.

## Model distribution

Models cannot be bundled in the APK (too large). Options:
1. **Bundled download on first launch** — Download `base.en` from Hugging Face on first run over any connection. Show a "Downloading transcription model (142 MB)..." progress screen.
2. **Pre-loaded via assets** — Not viable at this size.

**Use option 1.** Model URL:
```
https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.en.bin
```

Store model at: `/data/data/com.fieldnotes.app/files/whisper/ggml-base.en.bin`

## JNI bridge

### `app/src/main/cpp/whisper_jni.cpp`

```cpp
#include <jni.h>
#include <string>
#include <vector>
#include "whisper.cpp/whisper.h"
#include <android/log.h>

#define TAG "WhisperJNI"

static whisper_context* g_ctx = nullptr;

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_fieldnotes_app_core_whisper_WhisperEngine_initContext(
        JNIEnv* env, jobject /* obj */, jstring modelPath) {
    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    whisper_context_params cparams = whisper_context_default_params();
    cparams.use_gpu = false; // Android GPU not supported in whisper.cpp
    auto* ctx = whisper_init_from_file_with_params(path, cparams);
    env->ReleaseStringUTFChars(modelPath, path);
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT jstring JNICALL
Java_com_fieldnotes_app_core_whisper_WhisperEngine_transcribeAudio(
        JNIEnv* env, jobject /* obj */, jlong ctxPtr,
        jfloatArray audioData, jint numSamples) {
    auto* ctx = reinterpret_cast<whisper_context*>(ctxPtr);
    jfloat* samples = env->GetFloatArrayElements(audioData, nullptr);
    
    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_realtime = false;
    params.print_progress = false;
    params.language = "en";
    params.n_threads = 4; // Pixel 8 has 8 cores
    params.single_segment = false;
    params.token_timestamps = false;
    
    int result = whisper_full(ctx, params, samples, numSamples);
    env->ReleaseFloatArrayElements(audioData, samples, JNI_ABORT);
    
    if (result != 0) {
        return env->NewStringUTF("[Transcription failed]");
    }
    
    std::string output;
    int n_segments = whisper_full_n_segments(ctx);
    for (int i = 0; i < n_segments; ++i) {
        output += whisper_full_get_segment_text(ctx, i);
        if (i < n_segments - 1) output += " ";
    }
    
    return env->NewStringUTF(output.c_str());
}

JNIEXPORT void JNICALL
Java_com_fieldnotes_app_core_whisper_WhisperEngine_freeContext(
        JNIEnv* env, jobject /* obj */, jlong ctxPtr) {
    auto* ctx = reinterpret_cast<whisper_context*>(ctxPtr);
    if (ctx) whisper_free(ctx);
}

} // extern "C"
```

## `WhisperEngine.kt` (Kotlin JNI wrapper)

```kotlin
@Singleton
class WhisperEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelManager: WhisperModelManager
) {
    private var contextPtr: Long = 0L
    private val initMutex = Mutex()

    init {
        System.loadLibrary("fieldnotes-jni")
    }

    private external fun initContext(modelPath: String): Long
    private external fun transcribeAudio(ctxPtr: Long, audioData: FloatArray, numSamples: Int): String
    private external fun freeContext(ctxPtr: Long)

    suspend fun ensureInitialised() = initMutex.withLock {
        if (contextPtr == 0L) {
            val modelFile = modelManager.getModelFile()
            contextPtr = initContext(modelFile.absolutePath)
            if (contextPtr == 0L) throw IllegalStateException("Failed to load Whisper model")
        }
    }

    /**
     * Transcribe an audio file. Supports .m4a (AAC) and .flac/.wav (PCM).
     * Internally resamples to 16kHz mono float32 as required by Whisper.
     */
    suspend fun transcribe(audioFile: File): TranscriptionResult = withContext(Dispatchers.Default) {
        ensureInitialised()
        val pcmSamples = decodeToFloat32PCM(audioFile) // See below
        val text = transcribeAudio(contextPtr, pcmSamples, pcmSamples.size)
        TranscriptionResult(
            text = text.trim(),
            audioFile = audioFile,
            processedAt = System.currentTimeMillis()
        )
    }

    /**
     * Decode any supported audio file to 16kHz mono float32 PCM for Whisper.
     * Uses MediaExtractor + MediaCodec for AAC; reads PCM directly for WAV/FLAC.
     */
    private suspend fun decodeToFloat32PCM(file: File): FloatArray = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        extractor.setDataSource(file.absolutePath)
        
        // Find audio track
        var audioTrackIndex = -1
        var inputFormat: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val fmt = extractor.getTrackFormat(i)
            if (fmt.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                audioTrackIndex = i
                inputFormat = fmt
                break
            }
        }
        requireNotNull(inputFormat) { "No audio track in file" }
        extractor.selectTrack(audioTrackIndex)
        
        val inputSampleRate = inputFormat!!.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val inputChannels = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        
        val codec = MediaCodec.createDecoderByType(
            inputFormat.getString(MediaFormat.KEY_MIME)!!
        )
        codec.configure(inputFormat, null, null, 0)
        codec.start()
        
        val rawPcm = mutableListOf<Short>()
        // ... standard MediaCodec decode loop
        // Converts compressed audio to raw 16-bit PCM
        
        codec.stop()
        codec.release()
        extractor.release()
        
        // Resample to 16kHz mono if needed
        val monoSamples = if (inputChannels > 1) downmixToMono(rawPcm) else rawPcm
        val resampledSamples = if (inputSampleRate != 16000) 
            resample(monoSamples, inputSampleRate, 16000) 
        else monoSamples
        
        // Normalise to float32 in [-1, 1]
        resampledSamples.map { it.toFloat() / Short.MAX_VALUE }.toFloatArray()
    }

    private fun downmixToMono(stereo: List<Short>): List<Short> =
        stereo.chunked(2) { (l, r) -> ((l + r) / 2).toShort() }

    private fun resample(samples: List<Short>, fromRate: Int, toRate: Int): List<Short> {
        // Linear interpolation resampler — sufficient for speech
        val ratio = fromRate.toDouble() / toRate.toDouble()
        val outputSize = (samples.size / ratio).toInt()
        return List(outputSize) { i ->
            val srcIdx = i * ratio
            val lo = srcIdx.toInt().coerceIn(0, samples.size - 1)
            val hi = (lo + 1).coerceIn(0, samples.size - 1)
            val frac = srcIdx - lo
            ((samples[lo] * (1 - frac) + samples[hi] * frac)).toInt().toShort()
        }
    }
}
```

## `WhisperModelManager.kt`

```kotlin
@Singleton
class WhisperModelManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val modelDir = File(context.filesDir, "whisper")
    
    val BASE_MODEL_URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.en.bin"
    val SMALL_MODEL_URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small.en.bin"
    
    fun getModelFile(modelName: String = "ggml-base.en.bin"): File {
        val file = File(modelDir, modelName)
        if (!file.exists()) throw FileNotFoundException("Whisper model not found: $modelName. Download it first.")
        return file
    }
    
    fun isModelDownloaded(modelName: String = "ggml-base.en.bin"): Boolean =
        File(modelDir, modelName).exists()
    
    /** Download model with progress reporting */
    fun downloadModel(
        modelName: String = "ggml-base.en.bin",
        url: String = BASE_MODEL_URL
    ): Flow<DownloadProgress> = flow {
        modelDir.mkdirs()
        val dest = File(modelDir, modelName)
        val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        val totalBytes = connection.contentLengthLong
        var downloadedBytes = 0L
        
        connection.inputStream.use { input ->
            dest.outputStream().buffered().use { output ->
                val buffer = ByteArray(8192)
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                    downloadedBytes += read
                    emit(DownloadProgress(downloadedBytes, totalBytes))
                }
            }
        }
        emit(DownloadProgress(totalBytes, totalBytes, complete = true))
    }
}

data class DownloadProgress(
    val downloaded: Long,
    val total: Long,
    val complete: Boolean = false
) {
    val fraction: Float get() = if (total > 0) downloaded.toFloat() / total else 0f
}
```

## `TranscriptionResult.kt`

```kotlin
data class TranscriptionResult(
    val text: String,
    val audioFile: File,
    val processedAt: Long
)
```

## First-run model download flow

On first launch, check `WhisperModelManager.isModelDownloaded()`. If false:
1. Show a `ModalBottomSheet` or dedicated screen: "FieldNotes needs to download the transcription model (142 MB). This is a one-time download."
2. Show progress bar using `downloadModel()` Flow.
3. Offer WiFi-only option (check `ConnectivityManager` for active network type).
4. On completion, dismiss and proceed to main app.

The app should be fully usable for field recording before the model is downloaded. Only the transcription button should be disabled with a tooltip: "Transcription model downloading…"

## Language support

The `base.en` model is English-only. If multilingual support is desired in future, use `ggml-base.bin` (same size, slightly lower accuracy). Add a language selector in Settings that changes the `params.language` value passed to `whisper_full`.
