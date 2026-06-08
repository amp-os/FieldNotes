// FieldNotes — DriveAuthManager.kt
// Authored by: drive-sync module | Implements: 07_DRIVE_SYNC_MODULE.md / 11_GOOGLE_CLOUD_SETUP.md
// OAuth 2.0 native-app flow via AppAuth (RFC 8252): Custom Tabs + PKCE + reverse-client-ID redirect.
// AppAuth's AuthState (persisted in FieldNotesCredentialStore) owns the access/refresh tokens and
// performs token refresh automatically.
package com.fieldnotes.app.core.sync

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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues
import net.openid.appauth.TokenResponse
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class DriveAuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val credentialStore = FieldNotesCredentialStore(context)

    private val serviceConfig = AuthorizationServiceConfiguration(
        Uri.parse("https://accounts.google.com/o/oauth2/v2/auth"),
        Uri.parse("https://oauth2.googleapis.com/token"),
    )

    private val redirectUri: Uri = Uri.parse("${BuildConfig.DRIVE_REDIRECT_SCHEME}:/oauth2redirect")

    /** False when no OAuth client id is configured in local.properties. */
    val isConfigured: Boolean
        get() = BuildConfig.DRIVE_CLIENT_ID.isNotBlank() && BuildConfig.DRIVE_CLIENT_ID != "UNCONFIGURED"

    fun isAuthenticated(): Flow<Boolean> = credentialStore.isAuthorizedFlow
    fun connectedEmail(): Flow<String?> = credentialStore.emailFlow

    /**
     * Build the Intent that launches the OAuth consent flow (Custom Tab). Launch it with an
     * ActivityResult contract; pass the result Intent to [handleAuthorizationResponse].
     * Returns null when Drive is not configured.
     */
    fun authorizationRequestIntent(): Intent? {
        if (!isConfigured) return null
        val request = AuthorizationRequest.Builder(
            serviceConfig,
            BuildConfig.DRIVE_CLIENT_ID,
            ResponseTypeValues.CODE,
            redirectUri,
        )
            .setScopes(DriveScopes.DRIVE_FILE, "openid", "email")
            .setPromptValues(AuthorizationRequest.Prompt.CONSENT) // force refresh-token re-issue
            .setAdditionalParameters(mapOf("access_type" to "offline"))
            .build()
        val service = AuthorizationService(context)
        return try {
            service.getAuthorizationRequestIntent(request)
        } finally {
            service.dispose()
        }
    }

    /** Exchange the auth code from the redirect result for tokens and persist the session. */
    suspend fun handleAuthorizationResponse(data: Intent?): Boolean = withContext(Dispatchers.IO) {
        if (data == null) return@withContext false
        val response = AuthorizationResponse.fromIntent(data) ?: return@withContext false
        val authState = AuthState(response, AuthorizationException.fromIntent(data))
        val service = AuthorizationService(context)
        try {
            val tokenResponse = suspendCancellableCoroutine { cont ->
                service.performTokenRequest(response.createTokenExchangeRequest()) { resp, ex ->
                    cont.resume(resp to ex)
                }
            }
            val (resp, ex) = tokenResponse
            if (resp == null) return@withContext false
            authState.update(resp, ex)
            credentialStore.saveAuthState(authState)
            credentialStore.saveEmail(extractEmail(resp))
            true
        } catch (e: Exception) {
            false
        } finally {
            service.dispose()
        }
    }

    /** Build an authorised Drive service, or null if unconfigured / not signed in. */
    suspend fun getDriveService(): Drive? = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext null
        val authState = credentialStore.readAuthState() ?: return@withContext null
        if (!authState.isAuthorized) return@withContext null
        val token = freshAccessToken(authState) ?: return@withContext null
        val initializer = HttpRequestInitializer { request ->
            request.headers.authorization = "Bearer $token"
        }
        Drive.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), initializer)
            .setApplicationName("FieldNotes")
            .build()
    }

    suspend fun signOut() = credentialStore.clear()

    /** Get a non-expired access token, letting AppAuth refresh if needed, and persist any update. */
    private suspend fun freshAccessToken(authState: AuthState): String? {
        val service = AuthorizationService(context)
        return try {
            val token = suspendCancellableCoroutine { cont ->
                authState.performActionWithFreshTokens(service) { accessToken, _, _ ->
                    cont.resume(accessToken)
                }
            }
            credentialStore.saveAuthState(authState) // may have refreshed
            token
        } catch (e: Exception) {
            null
        } finally {
            service.dispose()
        }
    }

    /** Pull the account email from the id_token (requires the "email"/"openid" scopes). */
    private fun extractEmail(tokenResponse: TokenResponse): String? {
        val idToken = tokenResponse.idToken ?: return null
        val parts = idToken.split(".")
        if (parts.size < 2) return null
        return runCatching {
            val payload = String(Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING))
            JSONObject(payload).optString("email").ifBlank { null }
        }.getOrNull()
    }
}
