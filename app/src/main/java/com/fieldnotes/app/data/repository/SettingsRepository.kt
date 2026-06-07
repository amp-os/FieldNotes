// FieldNotes — SettingsRepository.kt
// Authored by: repositories | Implements: 08_UI_MODULE.md (settings) / 07_DRIVE_SYNC_MODULE.md (wifi-only)
package com.fieldnotes.app.data.repository

import android.content.Context
import android.media.MediaRecorder.AudioSource
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.fieldnotes.app.core.audio.CompletedRecording
import com.fieldnotes.app.core.audio.RecordingMode
import com.fieldnotes.app.core.whisper.WhisperModelManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val store = context.settingsDataStore

    val wifiOnlySync: Flow<Boolean> = store.data.map { it[KEY_WIFI_ONLY] ?: true }
    val fieldFormat: Flow<String> = store.data.map { it[KEY_FIELD_FORMAT] ?: FORMAT_FLAC }
    val selectedModel: Flow<String> = store.data.map { it[KEY_MODEL] ?: WhisperModelManager.BASE_MODEL }
    val audioSourceFlow: Flow<Int> = store.data.map { it[KEY_AUDIO_SOURCE] ?: AudioSource.MIC }

    /**
     * A just-finished recording awaiting its post-stop screen (transcription for voice, detail for
     * field). Persisted so a capture started from the widget/QS tile is still routed when the app
     * next becomes visible. Encoded as "MODE|id".
     */
    val pendingCompletion: Flow<CompletedRecording?> = store.data.map { prefs ->
        prefs[KEY_PENDING_COMPLETION]?.let { encoded ->
            val mode = encoded.substringBefore('|', "")
            val id = encoded.substringAfter('|', "")
            if (id.isBlank()) null
            else runCatching { CompletedRecording(id, RecordingMode.valueOf(mode)) }.getOrNull()
        }
    }

    suspend fun fieldFormatIsWav(): Boolean = fieldFormat.first() == FORMAT_WAV
    suspend fun audioSource(): Int = audioSourceFlow.first()
    suspend fun isWifiOnly(): Boolean = wifiOnlySync.first()

    suspend fun setWifiOnly(value: Boolean) = store.edit { it[KEY_WIFI_ONLY] = value }
    suspend fun setFieldFormat(format: String) = store.edit { it[KEY_FIELD_FORMAT] = format }
    suspend fun setSelectedModel(model: String) = store.edit { it[KEY_MODEL] = model }
    suspend fun setAudioSource(source: Int) = store.edit { it[KEY_AUDIO_SOURCE] = source }

    suspend fun setPendingCompletion(rec: CompletedRecording) =
        store.edit { it[KEY_PENDING_COMPLETION] = "${rec.mode.name}|${rec.recordingId}" }

    suspend fun clearPendingCompletion() = store.edit { it.remove(KEY_PENDING_COMPLETION) }

    companion object {
        const val FORMAT_FLAC = "FLAC"
        const val FORMAT_WAV = "WAV"

        private val KEY_WIFI_ONLY = booleanPreferencesKey("wifi_only_sync")
        private val KEY_FIELD_FORMAT = stringPreferencesKey("field_format")
        private val KEY_MODEL = stringPreferencesKey("whisper_model")
        private val KEY_AUDIO_SOURCE = intPreferencesKey("audio_source")
        private val KEY_PENDING_COMPLETION = stringPreferencesKey("pending_completion")
    }
}
