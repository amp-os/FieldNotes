// FieldNotes — DriveAuthManager.kt
// Authored by: drive-sync module | Implements: 07_DRIVE_SYNC_MODULE.md / 11_GOOGLE_CLOUD_SETUP.md
// OAuth 2.0 installed-app flow with PKCE (required by Google for custom-scheme redirects).
package com.fieldnotes.app.core.sync

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import com.fieldnotes.app.BuildConfig
import com.google.api.client.http.HttpRequestInitializer
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DriveAuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val credentialStore = FieldNotesCredentialStore(context)

    /** False when no OAuth client id is configured in local.properties. */
    val isConfigured: Boolean
        get() = BuildConfig.DRIVE_CLIENT_ID.isNotBlank() && BuildConfig.DRIVE_CLIENT_ID != "UNCONFIGURED"

    fun isAuthenticated(): Flow<Boolean> = credentialStore.hasValidTokenFlow
    fun connectedEmail(): Flow<String?> = credentialStore.emailFlow

    /** Build an authorised Drive service, or null if unconfigured / not signed in. */
    suspend fun getDriveService(): Drive? = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext null
        val token = freshAccessToken() ?: return@withContext null
        val initializer = HttpRequestInitializer { request ->
            request.headers.authorization = "Bearer $token"
        }
        Drive.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), initializer)
            .setApplicationName("FieldNotes")
            .build()
    }

    /** Launch the system browser for OAuth consent (PKCE). */
    suspend fun launchOAuthFlow(activity: Activity) {
        val verifier = generateCodeVerifier()
        credentialStore.saveCodeVerifier(verifier)
        val challenge = codeChallenge(verifier)
        val authUrl = "https://accounts.google.com/o/oauth2/v2/auth" +
            "?client_id=${enc(BuildConfig.DRIVE_CLIENT_ID)}" +
            "&redirect_uri=${enc(REDIRECT_URI)}" +
            "&response_type=code" +
            "&scope=${enc(DriveScopes.DRIVE_FILE)}" +
            "&code_challenge=${enc(challenge)}" +
            "&code_challenge_method=S256" +
            "&access_type=offline" +
            "&prompt=consent"
        activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(authUrl)))
    }

    /** Exchange an authorization code (from the redirect) for tokens. */
    suspend fun handleOAuthCallback(code: String): Boolean = withContext(Dispatchers.IO) {
        val verifier = credentialStore.getCodeVerifier() ?: return@withContext false
        val body = formBody(
            "client_id" to BuildConfig.DRIVE_CLIENT_ID,
            "code" to code,
            "code_verifier" to verifier,
            "grant_type" to "authorization_code",
            "redirect_uri" to REDIRECT_URI,
        )
        val json = postForm(TOKEN_ENDPOINT, body) ?: return@withContext false
        val access = json.optString("access_token").ifBlank { null }
        val refresh = json.optString("refresh_token").ifBlank { null }
        val expires = json.optLong("expires_in", 3600)
        if (access == null) return@withContext false
        val email = fetchEmail(access)
        credentialStore.saveTokens(access, refresh, expires, email)
        true
    }

    suspend fun signOut() = credentialStore.clear()

    /** Return a non-expired access token, refreshing if necessary. */
    private suspend fun freshAccessToken(): String? {
        val current = credentialStore.getAccessToken()
        val expiry = credentialStore.getExpiryEpochMs()
        if (current != null && System.currentTimeMillis() < expiry - 60_000) return current
        val refresh = credentialStore.getRefreshToken() ?: return current
        val body = formBody(
            "client_id" to BuildConfig.DRIVE_CLIENT_ID,
            "refresh_token" to refresh,
            "grant_type" to "refresh_token",
        )
        val json = postForm(TOKEN_ENDPOINT, body) ?: return current
        val access = json.optString("access_token").ifBlank { null } ?: return current
        val expires = json.optLong("expires_in", 3600)
        credentialStore.saveTokens(access, null, expires, null)
        return access
    }

    private fun fetchEmail(accessToken: String): String? = runCatching {
        val conn = (URL("https://www.googleapis.com/oauth2/v3/userinfo").openConnection() as HttpURLConnection)
        conn.setRequestProperty("Authorization", "Bearer $accessToken")
        conn.inputStream.bufferedReader().use { JSONObject(it.readText()).optString("email") }
    }.getOrNull()

    private fun postForm(endpoint: String, body: String): JSONObject? = runCatching {
        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        }
        conn.outputStream.use { it.write(body.toByteArray()) }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() } ?: return null
        if (code in 200..299) JSONObject(text) else null
    }.getOrNull()

    private fun formBody(vararg pairs: Pair<String, String>): String =
        pairs.joinToString("&") { (k, v) -> "${enc(k)}=${enc(v)}" }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun generateCodeVerifier(): String {
        val bytes = ByteArray(64)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    private fun codeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray())
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    companion object {
        const val REDIRECT_URI = "com.fieldnotes.app:/oauth2callback"
        private const val TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"
    }
}
