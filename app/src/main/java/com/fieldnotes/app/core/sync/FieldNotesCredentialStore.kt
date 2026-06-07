// FieldNotes — FieldNotesCredentialStore.kt
// Authored by: drive-sync module | Implements: 07_DRIVE_SYNC_MODULE.md
package com.fieldnotes.app.core.sync

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.driveCredentialStore: DataStore<Preferences> by preferencesDataStore(name = "drive_credentials")

/** DataStore-backed OAuth token storage for Google Drive. */
class FieldNotesCredentialStore(private val context: Context) {
    private val store = context.driveCredentialStore

    val hasValidTokenFlow: Flow<Boolean> = store.data.map { it[KEY_REFRESH_TOKEN] != null }
    val emailFlow: Flow<String?> = store.data.map { it[KEY_EMAIL] }

    suspend fun getAccessToken(): String? = store.data.first()[KEY_ACCESS_TOKEN]
    suspend fun getRefreshToken(): String? = store.data.first()[KEY_REFRESH_TOKEN]
    suspend fun getExpiryEpochMs(): Long = store.data.first()[KEY_EXPIRY] ?: 0L
    suspend fun hasValidToken(): Boolean = store.data.first()[KEY_REFRESH_TOKEN] != null

    suspend fun saveTokens(accessToken: String?, refreshToken: String?, expiresInSec: Long, email: String?) {
        store.edit { prefs ->
            accessToken?.let { prefs[KEY_ACCESS_TOKEN] = it }
            refreshToken?.let { prefs[KEY_REFRESH_TOKEN] = it }
            if (expiresInSec > 0) prefs[KEY_EXPIRY] = System.currentTimeMillis() + expiresInSec * 1000
            email?.let { prefs[KEY_EMAIL] = it }
        }
    }

    suspend fun saveCodeVerifier(verifier: String) = store.edit { it[KEY_CODE_VERIFIER] = verifier }
    suspend fun getCodeVerifier(): String? = store.data.first()[KEY_CODE_VERIFIER]

    suspend fun clear() = store.edit { it.clear() }

    companion object {
        private val KEY_ACCESS_TOKEN = stringPreferencesKey("access_token")
        private val KEY_REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        private val KEY_EMAIL = stringPreferencesKey("email")
        private val KEY_EXPIRY = longPreferencesKey("expiry_epoch_ms")
        private val KEY_CODE_VERIFIER = stringPreferencesKey("pkce_code_verifier")
    }
}
