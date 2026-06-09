// FieldNotes — WhisperEngine.kt
// Authored by: whisper module | Implements: 05_WHISPER_MODULE.md
// Fully offline: audio is decoded on-device and passed to whisper.cpp via JNI; nothing leaves the device.
package com.fieldnotes.app.core.whisper

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import com.fieldnotes.app.core.storage.LocalFileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

/** Kotlin wrapper around the whisper.cpp JNI bridge (whisper_jni.cpp). */
@Singleton
class WhisperEngine @Inject constructor(
    @Suppress("unused") private val localFileManager: LocalFileManager,
    private val modelManager: WhisperModelManager,
) {
    private var contextPtr: Long = 0L
    private var loadedModel: String? = null
    private val initMutex = Mutex()
    // whisper_context is single-threaded and there is only one shared context, so a second
    // transcription started while one is in progress must wait rather than race on it.
    private val transcribeMutex = Mutex()

    private external fun initContext(modelPath: String): Long
    private external fun transcribeAudio(ctxPtr: Long, audioData: FloatArray, numSamples: Int): String
    private external fun currentProgress(): Int
    private external fun freeContext(ctxPtr: Long)

    /** 0–100 progress of the in-flight transcription (transcribe() is serialised, so one at a time). */
    fun progress(): Int = currentProgress()

    /** True once the native model context is loaded. */
    val isReady: Boolean get() = contextPtr != 0L

    /**
     * Load the given model, reloading if a different model was previously loaded. The selected
     * model can change in Settings, so the engine must not assume the default base model.
     */
    suspend fun ensureInitialised(modelName: String = WhisperModelManager.BASE_MODEL) = initMutex.withLock {
        if (contextPtr != 0L && loadedModel == modelName) return@withLock
        if (contextPtr != 0L) {
            freeContext(contextPtr); contextPtr = 0L; loadedModel = null
        }
        val modelFile = modelManager.getModelFile(modelName)
        contextPtr = initContext(modelFile.absolutePath)
        check(contextPtr != 0L) { "Failed to load Whisper model (whisper.cpp not integrated or model invalid)" }
        loadedModel = modelName
    }

    /** Transcribe an audio file. Decodes/resamples to 16kHz mono float32 then runs whisper. */
    suspend fun transcribe(
        audioFile: File,
        modelName: String = WhisperModelManager.BASE_MODEL,
    ): TranscriptionResult = withContext(Dispatchers.Default) {
        // Serialise the whole run (model load + inference) on the shared native context so concurrent
        // transcriptions queue instead of corrupting each other.
        transcribeMutex.withLock {
            ensureInitialised(modelName)
            val t0 = System.currentTimeMillis()
            val pcm = decodeToFloat32Pcm(audioFile)
            Log.i(TAG, "decode: ${System.currentTimeMillis() - t0}ms → ${pcm.size} samples (${pcm.size / 16000f}s)")
            val text = transcribeAudio(contextPtr, pcm, pcm.size)
            TranscriptionResult(
                text = text.trim(),
                audioFile = audioFile,
                processedAt = System.currentTimeMillis(),
            )
        }
    }

    fun release() {
        if (contextPtr != 0L) {
            freeContext(contextPtr)
            contextPtr = 0L
            loadedModel = null
        }
    }

    /** Decode any supported audio file to 16kHz mono float32 PCM. */
    private suspend fun decodeToFloat32Pcm(file: File): FloatArray = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        extractor.setDataSource(file.absolutePath)

        var trackIndex = -1
        var inputFormat: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val fmt = extractor.getTrackFormat(i)
            if (fmt.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                trackIndex = i; inputFormat = fmt; break
            }
        }
        val format = requireNotNull(inputFormat) { "No audio track in ${file.name}" }
        extractor.selectTrack(trackIndex)

        val inputSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val inputChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val mime = format.getString(MediaFormat.KEY_MIME)!!

        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()

        val pcm = ArrayList<Short>(1 shl 20)
        val bufferInfo = MediaCodec.BufferInfo()
        var sawInputEos = false
        var sawOutputEos = false

        try {
            while (!sawOutputEos) {
                if (!sawInputEos) {
                    val inIndex = codec.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val inBuf = codec.getInputBuffer(inIndex)!!
                        val sampleSize = extractor.readSampleData(inBuf, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            sawInputEos = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
                if (outIndex >= 0) {
                    if (bufferInfo.size > 0) {
                        val outBuf = codec.getOutputBuffer(outIndex)!!
                        outBuf.position(bufferInfo.offset)
                        outBuf.limit(bufferInfo.offset + bufferInfo.size)
                        val shorts = outBuf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                        while (shorts.hasRemaining()) pcm.add(shorts.get())
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) sawOutputEos = true
                }
            }
        } finally {
            runCatching { codec.stop() }
            codec.release()
            extractor.release()
        }

        val mono = if (inputChannels > 1) downmixToMono(pcm, inputChannels) else pcm
        val resampled = if (inputSampleRate != TARGET_RATE) resample(mono, inputSampleRate, TARGET_RATE) else mono
        FloatArray(resampled.size) { resampled[it] / 32768f }
    }

    private fun downmixToMono(interleaved: List<Short>, channels: Int): List<Short> {
        val out = ArrayList<Short>(interleaved.size / channels)
        var i = 0
        while (i + channels <= interleaved.size) {
            var sum = 0
            for (c in 0 until channels) sum += interleaved[i + c]
            out.add((sum / channels).toShort())
            i += channels
        }
        return out
    }

    private fun resample(samples: List<Short>, fromRate: Int, toRate: Int): List<Short> {
        val ratio = fromRate.toDouble() / toRate.toDouble()
        val outSize = (samples.size / ratio).toInt()
        if (samples.isEmpty()) return emptyList()
        return List(outSize) { i ->
            val src = i * ratio
            val lo = src.toInt().coerceIn(0, samples.size - 1)
            val hi = (lo + 1).coerceIn(0, samples.size - 1)
            val frac = src - lo
            (samples[lo] * (1 - frac) + samples[hi] * frac).toInt().toShort()
        }
    }

    companion object {
        private const val TAG = "WhisperEngine"
        private const val TARGET_RATE = 16000

        init {
            System.loadLibrary("fieldnotes-jni")
        }
    }
}
