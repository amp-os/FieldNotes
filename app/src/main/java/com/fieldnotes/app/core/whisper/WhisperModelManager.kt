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

/** Manages whisper.cpp ggml model files: presence checks and first-run download. */
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

    /** Download a model, emitting progress. Writes to a .part file then renames on success. */
    fun downloadModel(
        modelName: String = BASE_MODEL,
        url: String = BASE_MODEL_URL,
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
        } finally {
            connection.disconnect()
        }
    }.flowOn(Dispatchers.IO)

    companion object {
        const val BASE_MODEL = "ggml-base.en.bin"
        const val SMALL_MODEL = "ggml-small.en.bin"
        const val BASE_MODEL_URL =
            "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.en.bin"
        const val SMALL_MODEL_URL =
            "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small.en.bin"
    }
}

data class DownloadProgress(
    val downloaded: Long,
    val total: Long,
    val complete: Boolean = false,
) {
    val fraction: Float get() = if (total > 0) downloaded.toFloat() / total else 0f
}
