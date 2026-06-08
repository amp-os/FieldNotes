// FieldNotes — FieldNotesCredentialStore.kt
// Authored by: drive-sync module | Implements: 07_DRIVE_SYNC_MODULE.md
// Persists the AppAuth AuthState (tokens + config + refresh token) as JSON, plus the account email.
package com.fieldnotes.app.core.sync

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import net.openid.appauth.AuthState

private val Context.driveCredentialStore: DataStore<Preferences> by preferencesDataStore(name = "drive_credentials")

/** DataStore-backed persistence for the Google Drive OAuth session (AppAuth AuthState). */
class FieldNotesCredentialStore(private val context: Context) {
    private val store = context.driveCredentialStore

    val isAuthorizedFlow: Flow<Boolean> = store.data.map { prefs ->
        prefs[KEY_AUTH_STATE]?.let { deserialize(it)?.isAuthorized } ?: false
    }

    val emailFlow: Flow<String?> = store.data.map { it[KEY_EMAIL] }

    suspend fun readAuthState(): AuthState? =
        store.data.first()[KEY_AUTH_STATE]?.let { deserialize(it) }

    suspend fun saveAuthState(state: AuthState) {
        store.edit { it[KEY_AUTH_STATE] = state.jsonSerializeString() }
    }

    suspend fun saveEmail(email: String?) {
        store.edit { prefs -> if (email != null) prefs[KEY_EMAIL] = email else prefs.remove(KEY_EMAIL) }
    }

    suspend fun clear() = store.edit { it.clear() }

    private fun deserialize(json: String): AuthState? =
        runCatching { AuthState.jsonDeserialize(json) }.getOrNull()

    companion object {
        private val KEY_AUTH_STATE = stringPreferencesKey("auth_state")
        private val KEY_EMAIL = stringPreferencesKey("email")
    }
}
