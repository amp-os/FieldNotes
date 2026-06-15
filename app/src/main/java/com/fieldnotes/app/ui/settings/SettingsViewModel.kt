// FieldNotes — SettingsViewModel.kt
// Authored by: ui module | Implements: 08_UI_MODULE.md / 07_DRIVE_SYNC_MODULE.md / 05_WHISPER_MODULE.md
package com.fieldnotes.app.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fieldnotes.app.core.storage.LocalFileManager
import com.fieldnotes.app.core.sync.DriveAuthManager
import com.fieldnotes.app.core.sync.SyncScheduler
import com.fieldnotes.app.core.whisper.WhisperModel
import com.fieldnotes.app.core.whisper.WhisperModelManager
import com.fieldnotes.app.data.db.SyncQueueDao
import com.fieldnotes.app.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** UI state for a single whisper model row in Settings. */
data class ModelUiState(
    val model: WhisperModel,
    val downloaded: Boolean,
    val selected: Boolean,
    val downloadProgress: Float?,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val driveAuthManager: DriveAuthManager,
    private val syncScheduler: SyncScheduler,
    private val modelManager: WhisperModelManager,
    private val localFileManager: LocalFileManager,
    syncQueueDao: SyncQueueDao,
) : ViewModel() {

    val wifiOnly = settingsRepository.wifiOnlySync.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    val fieldFormat = settingsRepository.fieldFormat.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsRepository.FORMAT_FLAC)
    val selectedModel = settingsRepository.selectedModel.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WhisperModelManager.BASE_MODEL)
    val connectedEmail = driveAuthManager.connectedEmail().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val pendingUploads = syncQueueDao.pendingCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val isDriveConfigured: Boolean get() = driveAuthManager.isConfigured

    /** On-device notes folder (issue 5): display name (null if unset) and default-here preference. */
    val localNotesFolderName: StateFlow<String?> = settingsRepository.localNotesFolderUri
        .map { uri -> uri?.let { folderDisplayName(it) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val preferLocalNotes: StateFlow<Boolean> = settingsRepository.preferLocalNotes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setLocalNotesFolder(uri: Uri) = viewModelScope.launch {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        settingsRepository.setLocalNotesFolderUri(uri.toString())
    }

    fun clearLocalNotesFolder() = viewModelScope.launch {
        settingsRepository.localNotesFolderUri.first()?.let { existing ->
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    Uri.parse(existing),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
        }
        settingsRepository.setLocalNotesFolderUri(null)
        settingsRepository.setPreferLocalNotes(false)
    }

    fun setPreferLocalNotes(value: Boolean) = viewModelScope.launch {
        settingsRepository.setPreferLocalNotes(value)
    }

    private fun folderDisplayName(uri: String): String =
        runCatching { DocumentFile.fromTreeUri(context, Uri.parse(uri))?.name }.getOrNull()
            ?: Uri.parse(uri).lastPathSegment
            ?: uri

    private val _storageUsed = MutableStateFlow(0L)
    val storageUsed: StateFlow<Long> = _storageUsed.asStateFlow()

    // Bumped after a download/delete so the (non-reactive) filesystem presence is re-read.
    private val _modelRefresh = MutableStateFlow(0)
    // fileName -> download fraction while a download is in flight.
    private val _downloading = MutableStateFlow<Map<String, Float>>(emptyMap())
    // Last model-download failure (e.g. lost connection), for the UI to surface; cleared on retry.
    private val _downloadError = MutableStateFlow<String?>(null)
    val downloadError: StateFlow<String?> = _downloadError.asStateFlow()
    fun clearDownloadError() { _downloadError.value = null }

    /** One row per catalog model, reflecting downloaded/selected/in-progress state. */
    val models: StateFlow<List<ModelUiState>> =
        combine(selectedModel, _modelRefresh, _downloading) { selected, _, downloading ->
            WhisperModelManager.MODELS.map { m ->
                ModelUiState(
                    model = m,
                    downloaded = modelManager.isModelDownloaded(m.fileName),
                    selected = m.fileName == selected,
                    downloadProgress = downloading[m.fileName],
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init { refreshStorage() }

    fun setWifiOnly(v: Boolean) = viewModelScope.launch { settingsRepository.setWifiOnly(v) }
    fun setFieldFormat(format: String) = viewModelScope.launch { settingsRepository.setFieldFormat(format) }
    fun setModel(model: String) = viewModelScope.launch { settingsRepository.setSelectedModel(model) }

    /** Intent that launches the OAuth consent flow; launch via an ActivityResult contract. */
    fun buildAuthIntent(): Intent? = driveAuthManager.authorizationRequestIntent()

    /** Feed the ActivityResult data back to complete the token exchange. */
    fun onAuthResult(data: Intent?) = viewModelScope.launch {
        driveAuthManager.handleAuthorizationResponse(data)
        // On (re)connect, pull existing Drive notes back into the app (issue 7).
        syncScheduler.scheduleSync(settingsRepository.isWifiOnly())
    }

    fun signOut() = viewModelScope.launch { driveAuthManager.signOut() }

    fun syncNow() = viewModelScope.launch { syncScheduler.scheduleSync(settingsRepository.isWifiOnly()) }

    fun clearTempFiles() = viewModelScope.launch(Dispatchers.IO) {
        localFileManager.cleanupTempFiles(); refreshStorage()
    }

    fun downloadModel(fileName: String) = viewModelScope.launch {
        _downloadError.value = null
        modelManager.downloadModel(fileName)
            .catch { e ->
                // A network drop mid-download used to propagate uncaught and crash the app. Surface it.
                _downloading.update { it - fileName }
                _downloadError.value = "Download failed: ${e.message ?: "connection lost"}"
            }
            .collect { progress ->
                if (progress.complete) {
                    _downloading.update { it - fileName }
                    _modelRefresh.update { it + 1 }
                    refreshStorage()
                } else {
                    _downloading.update { it + (fileName to progress.fraction) }
                }
            }
    }

    fun deleteModel(fileName: String) = viewModelScope.launch(Dispatchers.IO) {
        modelManager.deleteModel(fileName)
        // If the deleted model was selected, fall back to the first remaining downloaded model.
        if (settingsRepository.selectedModel.first() == fileName) {
            WhisperModelManager.MODELS.firstOrNull { modelManager.isModelDownloaded(it.fileName) }
                ?.let { settingsRepository.setSelectedModel(it.fileName) }
        }
        _modelRefresh.update { it + 1 }
        refreshStorage()
    }

    private fun refreshStorage() = viewModelScope.launch {
        _storageUsed.value = withContext(Dispatchers.IO) { localFileManager.totalStorageUsed() }
    }
}
