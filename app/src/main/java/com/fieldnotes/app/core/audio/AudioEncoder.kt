// FieldNotes — AudioEncoder.kt
// Authored by: audio module | Implements: 04_AUDIO_MODULE.md
package com.fieldnotes.app.core.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import com.fieldnotes.app.core.storage.LocalFileManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encodes captured PCM to the target field-recording format, and provides the MediaRecorder-based
 * voice-note path. Voice notes (AAC-LC) are produced directly by MediaRecorder — no PCM step.
 */
@Singleton
class AudioEncoder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val localFileManager: LocalFileManager,
) {
    /**
     * Produce a field recording from [pcmFile]. Tries FLAC unless [preferWav] is set; falls back to
     * WAV if the platform FLAC encoder is unavailable. Returns the file actually written and deletes
     * the temp PCM. The returned file lives in the recordings dir.
     */
    suspend fun encodeField(
        pcmFile: File,
        sampleRate: Int,
        channels: Int,
        preferWav: Boolean,
    ): File = withContext(Dispatchers.IO) {
        val result = if (preferWav) {
            writeWav(pcmFile, localFileManager.newFieldRecordingFile("wav"), sampleRate, channels)
        } else {
            runCatching {
                encodeToFlac(pcmFile, localFileManager.newFieldRecordingFile("flac"), sampleRate, channels)
            }.getOrElse {
                Log.w(TAG, "FLAC encode failed, falling back to WAV", it)
                writeWav(pcmFile, localFileManager.newFieldRecordingFile("wav"), sampleRate, channels)
            }
        }
        pcmFile.delete()
        result
    }

    /** Encode raw 16-bit PCM to a raw .flac stream via MediaCodec. */
    fun encodeToFlac(pcmFile: File, outputFile: File, sampleRate: Int, channels: Int): File {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_FLAC, sampleRate, channels).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, 0) // lossless — bitrate ignored
            setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            setInteger(MediaFormat.KEY_FLAC_COMPRESSION_LEVEL, 5) // 0-8; 5 balanced
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_FLAC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()

        val input = pcmFile.inputStream().buffered()
        val out = outputFile.outputStream().buffered()
        out.write(FLAC_STREAM_MARKER) // "fLaC"

        val bufferInfo = MediaCodec.BufferInfo()
        val chunk = ByteArray(16 * 1024)
        var inputDone = false
        var outputDone = false
        var presentationUs = 0L
        val bytesPerFrame = 2 * channels

        try {
            while (!outputDone) {
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val inBuf = codec.getInputBuffer(inIndex)!!
                        inBuf.clear()
                        val toRead = minOf(chunk.size, inBuf.capacity())
                        val read = input.read(chunk, 0, toRead)
                        if (read <= 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, presentationUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            inBuf.put(chunk, 0, read)
                            codec.queueInputBuffer(inIndex, 0, read, presentationUs, 0)
                            val frames = read / bytesPerFrame
                            presentationUs += frames * 1_000_000L / sampleRate
                        }
                    }
                }
                val outIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
                if (outIndex >= 0) {
                    val outBuf = codec.getOutputBuffer(outIndex)!!
                    if (bufferInfo.size > 0) {
                        val bytes = ByteArray(bufferInfo.size)
                        outBuf.position(bufferInfo.offset)
                        outBuf.get(bytes, 0, bufferInfo.size)
                        out.write(bytes)
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                }
            }
        } finally {
            input.close()
            out.flush()
            out.close()
            runCatching { codec.stop() }
            codec.release()
        }
        return outputFile
    }

    /** Write a 16-bit PCM stream as a .wav file (44-byte header + data). */
    fun writeWav(pcmFile: File, outputFile: File, sampleRate: Int, channels: Int): File {
        val bitsPerSample = 16
        val dataSize = pcmFile.length().toInt()
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8

        RandomAccessFile(outputFile, "rw").use { raf ->
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray(Charsets.US_ASCII))
            header.putInt(36 + dataSize)
            header.put("WAVE".toByteArray(Charsets.US_ASCII))
            header.put("fmt ".toByteArray(Charsets.US_ASCII))
            header.putInt(16)                 // PCM fmt chunk size
            header.putShort(1)                // audio format = PCM
            header.putShort(channels.toShort())
            header.putInt(sampleRate)
            header.putInt(byteRate)
            header.putShort(blockAlign.toShort())
            header.putShort(bitsPerSample.toShort())
            header.put("data".toByteArray(Charsets.US_ASCII))
            header.putInt(dataSize)
            raf.write(header.array())
            pcmFile.inputStream().buffered().use { ins ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val r = ins.read(buf)
                    if (r <= 0) break
                    raf.write(buf, 0, r)
                }
            }
        }
        return outputFile
    }

    /** Voice notes: AAC-LC @ 16kHz mono, 128kbps, directly via MediaRecorder. */
    fun startVoiceRecording(outputFile: File): MediaRecorder {
        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION") MediaRecorder()
        }
        return recorder.apply {
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

    companion object {
        private const val TAG = "AudioEncoder"
        private val FLAC_STREAM_MARKER = byteArrayOf(0x66, 0x4C, 0x61, 0x43) // "fLaC"
    }
}
