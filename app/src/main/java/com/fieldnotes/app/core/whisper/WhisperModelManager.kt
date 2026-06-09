// FieldNotes — WhisperModelManager.kt
// Authored by: whisper module | Implements: 05_WHISPER_MODULE.md
package com.fieldnotes.app.core.whisper

import com.fieldnotes.app.core.storage.LocalFileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.FileNotFoundException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/** A downloadable whisper.cpp ggml model. */
data class WhisperModel(
    val fileName: String,
    val displayName: String,
    val sizeLabel: String,
    val url: String,
) {
    /** Quantized models (q5_1) are ~2-3x faster and use about half the RAM. */
    val isQuantized: Boolean get() = fileName.contains("-q")
}

/** Manages whisper.cpp ggml model files: catalog, presence checks, download and deletion. */
@Singleton
class WhisperModelManager @Inject constructor(
    localFileManager: LocalFileManager,
) {
    private val modelDir: File = localFileManager.whisperDir

    fun getModelFile(modelName: String = BASE_MODEL): File {
        val file = File(modelDir, modelName)
        if (!file.exists()) {
            throw FileNotFoundException("Whisper model not found: $modelName. Download it first.")
        }
        return file
    }

    fun isModelDownloaded(modelName: String = BASE_MODEL): Boolean =
        File(modelDir, modelName).exists()

    /** Size on disk of a downloaded model, or 0 if not present. */
    fun modelSizeBytes(modelName: String): Long =
        File(modelDir, modelName).let { if (it.exists()) it.length() else 0L }

    /** Delete a downloaded model (and any stale .part file). Returns true if anything was removed. */
    fun deleteModel(modelName: String): Boolean {
        val removed = File(modelDir, modelName).delete()
        File(modelDir, "$modelName.part").delete()
        return removed
    }

    /** Download a model, emitting progress. Writes to a .part file then renames on success. */
    fun downloadModel(
        modelName: String = BASE_MODEL,
        url: String = catalogUrl(modelName),
    ): Flow<DownloadProgress> = flow {
        modelDir.mkdirs()
        val dest = File(modelDir, modelName)
        val partial = File(modelDir, "$modelName.part")
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 30_000
        }
        try {
            val total = connection.contentLengthLong
            var downloaded = 0L
            connection.inputStream.use { input ->
                partial.outputStream().buffered().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        emit(DownloadProgress(downloaded, total))
                    }
                }
            }
            if (!partial.renameTo(dest)) {
                partial.copyTo(dest, overwrite = true); partial.delete()
            }
            emit(DownloadProgress(dest.length(), dest.length(), complete = true))
        } catch (e: Exception) {
            // A dropped connection (SocketException) etc. must not leave a truncated .part behind,
            // and must propagate as a normal flow error for the collector to handle — not crash.
            partial.delete()
            throw e
        } finally {
            connection.disconnect()
        }
    }.flowOn(Dispatchers.IO)

    companion object {
        const val TINY_MODEL = "ggml-tiny.en.bin"
        const val BASE_MODEL = "ggml-base.en.bin"
        const val SMALL_MODEL = "ggml-small.en.bin"
        const val TINY_MODEL_Q5 = "ggml-tiny.en-q5_1.bin"
        const val BASE_MODEL_Q5 = "ggml-base.en-q5_1.bin"
        const val SMALL_MODEL_Q5 = "ggml-small.en-q5_1.bin"

        private const val REPO =
            "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/"

        // Fastest first. NOTE: on this device (Pixel 8 / Tensor G3) the q5_1 quantized variants run
        // ~5x SLOWER than the F16 models — measured 2026-06-09 (tiny.en 57s vs tiny.en-q5_1 271s for
        // the same 62s clip). So F16 leads each size; quantized is offered only to save disk space.
        val MODELS: List<WhisperModel> = listOf(
            WhisperModel(TINY_MODEL, "Tiny", "75 MB", REPO + TINY_MODEL),
            WhisperModel(BASE_MODEL, "Base", "142 MB", REPO + BASE_MODEL),
            WhisperModel(SMALL_MODEL, "Small", "466 MB", REPO + SMALL_MODEL),
            WhisperModel(TINY_MODEL_Q5, "Tiny · quantized (slower)", "31 MB", REPO + TINY_MODEL_Q5),
            WhisperModel(BASE_MODEL_Q5, "Base · quantized (slower)", "57 MB", REPO + BASE_MODEL_Q5),
            WhisperModel(SMALL_MODEL_Q5, "Small · quantized (slower)", "182 MB", REPO + SMALL_MODEL_Q5),
        )

        fun modelFor(fileName: String): WhisperModel? = MODELS.find { it.fileName == fileName }

        private fun catalogUrl(fileName: String): String =
            modelFor(fileName)?.url ?: (REPO + fileName)

        // Retained for backwards compatibility with older callers.
        const val BASE_MODEL_URL = REPO + BASE_MODEL
        const val SMALL_MODEL_URL = REPO + SMALL_MODEL
    }
}

data class DownloadProgress(
    val downloaded: Long,
    val total: Long,
    val complete: Boolean = false,
) {
    val fraction: Float get() = if (total > 0) downloaded.toFloat() / total else 0f
}
