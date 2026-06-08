// FieldNotes — SettingsViewModel.kt
// Authored by: ui module | Implements: 08_UI_MODULE.md / 07_DRIVE_SYNC_MODULE.md / 05_WHISPER_MODULE.md
package com.fieldnotes.app.ui.settings

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fieldnotes.app.core.storage.LocalFileManager
import com.fieldnotes.app.core.sync.DriveAuthManager
import com.fieldnotes.app.core.sync.SyncScheduler
import com.fieldnotes.app.core.whisper.WhisperModelManager
import com.fieldnotes.app.data.db.SyncQueueDao
import com.fieldnotes.app.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
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

    private val _storageUsed = MutableStateFlow(0L)
    val storageUsed: StateFlow<Long> = _storageUsed.asStateFlow()

    private val _modelProgress = MutableStateFlow<Float?>(null)
    val modelProgress: StateFlow<Float?> = _modelProgress.asStateFlow()

    val baseModelDownloaded: Boolean get() = modelManager.isModelDownloaded(WhisperModelManager.BASE_MODEL)

    init { refreshStorage() }

    fun setWifiOnly(v: Boolean) = viewModelScope.launch { settingsRepository.setWifiOnly(v) }
    fun setFieldFormat(format: String) = viewModelScope.launch { settingsRepository.setFieldFormat(format) }
    fun setModel(model: String) = viewModelScope.launch { settingsRepository.setSelectedModel(model) }

    /** Intent that launches the OAuth consent flow; launch via an ActivityResult contract. */
    fun buildAuthIntent(): Intent? = driveAuthManager.authorizationRequestIntent()

    /** Feed the ActivityResult data back to complete the token exchange. */
    fun onAuthResult(data: Intent?) = viewModelScope.launch { driveAuthManager.handleAuthorizationResponse(data) }

    fun signOut() = viewModelScope.launch { driveAuthManager.signOut() }

    fun syncNow() = viewModelScope.launch { syncScheduler.scheduleSync(settingsRepository.isWifiOnly()) }

    fun clearTempFiles() = viewModelScope.launch(Dispatchers.IO) {
        localFileManager.cleanupTempFiles(); refreshStorage()
    }

    fun downloadModel(model: String = WhisperModelManager.BASE_MODEL) = viewModelScope.launch {
        val url = if (model == WhisperModelManager.SMALL_MODEL) WhisperModelManager.SMALL_MODEL_URL else WhisperModelManager.BASE_MODEL_URL
        modelManager.downloadModel(model, url).collect { progress ->
            _modelProgress.value = if (progress.complete) null else progress.fraction
        }
    }

    private fun refreshStorage() = viewModelScope.launch {
        _storageUsed.value = withContext(Dispatchers.IO) { localFileManager.totalStorageUsed() }
    }
}
