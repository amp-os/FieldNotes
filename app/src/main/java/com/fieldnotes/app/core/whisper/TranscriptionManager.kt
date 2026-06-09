// FieldNotes — TranscriptionManager.kt
// Authored by: whisper module | Implements: 05_WHISPER_MODULE.md / 08_UI_MODULE.md (issue 2)
// App-scoped owner of in-flight transcription jobs. Decoupled from any screen so a job keeps
// running (and can auto-append to a note) even after the user navigates away — "add it when the
// transcription is done, no need to wait".
package com.fieldnotes.app.core.whisper

import android.content.Context
import com.fieldnotes.app.data.repository.NoteDestination
import com.fieldnotes.app.data.repository.NoteRepository
import com.fieldnotes.app.data.repository.RecordingRepository
import com.fieldnotes.app.data.repository.TranscriptionRepository
import com.fieldnotes.app.service.TranscriptionService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TranscriptionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val transcriptionRepository: TranscriptionRepository,
    private val noteRepository: NoteRepository,
    private val recordingRepository: RecordingRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Number of transcriptions actively running. A foreground service keeps the process alive while
    // any are in flight (a long job would otherwise be lost if the app is reclaimed in the background).
    private val activeRuns = java.util.concurrent.atomic.AtomicInteger(0)

    private fun beginForegroundRun() {
        if (activeRuns.getAndIncrement() == 0) TranscriptionService.start(context)
    }

    private fun endForegroundRun() {
        if (activeRuns.decrementAndGet() == 0) TranscriptionService.stop(context)
    }

    enum class Status { RUNNING, DONE, FAILED, MODEL_MISSING }

    data class Job(
        val recordingId: String,
        val status: Status,
        val text: String = "",
        val error: String? = null,
        /** 0–100 while RUNNING. Reflects the engine's current run (transcriptions are serialised). */
        val progress: Int = 0,
    )

    private data class AutoSave(
        val filename: String,
        val labels: List<String>,
        val destination: NoteDestination,
    )

    private val _jobs = MutableStateFlow<Map<String, Job>>(emptyMap())
    val jobs: StateFlow<Map<String, Job>> = _jobs.asStateFlow()

    // One-shot signal that a recording's note was saved (auto or manual). The job is removed from
    // the map at the same time, so a screen can't infer "saved" from the map — it listens here.
    private val _saved = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val saved: SharedFlow<String> = _saved.asSharedFlow()

    private val armed = HashMap<String, AutoSave>()
    private val armLock = Mutex()

    fun jobFor(recordingId: String): Job? = _jobs.value[recordingId]

    /** Begin transcribing, unless a job is already running or finished successfully for this id. */
    fun start(recordingId: String) {
        val existing = _jobs.value[recordingId]
        if (existing != null && existing.status != Status.FAILED) return
        put(recordingId, Job(recordingId, Status.RUNNING))
        scope.launch {
            if (!transcriptionRepository.isModelDownloaded()) {
                put(recordingId, Job(recordingId, Status.MODEL_MISSING))
                return@launch
            }
            // Poll native progress (0–100) so the UI shows a real bar instead of an endless spinner.
            val poller = launch {
                while (isActive) {
                    val p = transcriptionRepository.currentProgress()
                    _jobs.value[recordingId]?.let {
                        if (it.status == Status.RUNNING && p != it.progress) put(recordingId, it.copy(progress = p))
                    }
                    delay(400)
                }
            }
            beginForegroundRun()
            runCatching { transcriptionRepository.transcribeRecording(recordingId) }
                .also { poller.cancel(); endForegroundRun() }
                .onSuccess { result ->
                    put(recordingId, Job(recordingId, Status.DONE, text = result.text, progress = 100))
                    runAutoSaveIfArmed(recordingId, result.text)
                }
                .onFailure { e ->
                    put(recordingId, Job(recordingId, Status.FAILED, error = e.message))
                }
        }
    }

    /**
     * Append the result to [filename] as soon as it is ready (or immediately if already done),
     * using the raw transcription text. The user need not wait on the screen.
     */
    fun armAutoSave(
        recordingId: String,
        filename: String,
        labels: List<String>,
        destination: NoteDestination,
    ) {
        scope.launch {
            armLock.withLock { armed[recordingId] = AutoSave(filename, labels, destination) }
            val job = _jobs.value[recordingId]
            if (job?.status == Status.DONE) runAutoSaveIfArmed(recordingId, job.text)
        }
    }

    /** Save edited text now (transcription already finished); completion surfaces via [saved]. */
    fun saveNow(
        recordingId: String,
        filename: String,
        text: String,
        labels: List<String>,
        destination: NoteDestination,
    ) {
        scope.launch { persist(recordingId, filename, text, labels, destination) }
    }

    /** Forget a job (e.g. the user discarded it). */
    fun clear(recordingId: String) {
        _jobs.update { it - recordingId }
        scope.launch { armLock.withLock { armed.remove(recordingId) } }
    }

    private suspend fun runAutoSaveIfArmed(recordingId: String, text: String) {
        val request = armLock.withLock { armed.remove(recordingId) } ?: return
        persist(recordingId, request.filename, text, request.labels, request.destination)
    }

    private suspend fun persist(
        recordingId: String,
        filename: String,
        text: String,
        labels: List<String>,
        destination: NoteDestination,
    ) {
        runCatching {
            val savedName = noteRepository.saveTranscription(filename, text, labels, destination)
            recordingRepository.setNoteFilename(recordingId, savedName)
            if (labels.isNotEmpty()) recordingRepository.updateLabels(recordingId, labels)
            savedName
        }.onSuccess {
            // Remove the job and fire the one-shot saved event for any screen still watching this id.
            clear(recordingId)
            _saved.tryEmit(recordingId)
        }.onFailure { e ->
            _jobs.update { jobs ->
                val current = jobs[recordingId] ?: Job(recordingId, Status.DONE, text = text)
                jobs + (recordingId to current.copy(error = e.message ?: "Failed to save note"))
            }
        }
    }

    private fun put(recordingId: String, job: Job) {
        _jobs.update { it + (recordingId to job) }
    }
}
