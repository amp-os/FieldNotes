# FieldNotes — Google Drive Sync Module

## Overview

Files are uploaded to Google Drive in the background using WorkManager. The user authenticates once via OAuth 2.0. All uploads are queued in Room and retried on failure.

## Folder structure on Drive

```
My Drive/
  FieldNotes/
    recordings/    ← .flac / .wav field recordings
    voice/         ← .m4a voice note audio
    notes/         ← .md transcription notes
```

## Authentication

FieldNotes uses the Google Drive REST API v3 via the `google-api-client-android` library with OAuth 2.0 installed-app flow.

### `DriveAuthManager.kt`

```kotlin
@Singleton
class DriveAuthManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val credentialStore = FieldNotesCredentialStore(context) // DataStore-backed
    
    /** Returns an authorised Drive service, or null if not authenticated */
    suspend fun getDriveService(): Drive? {
        val token = credentialStore.getAccessToken() ?: return null
        val credential = GoogleCredential().setAccessToken(token)
        return Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        ).setApplicationName("FieldNotes").build()
    }
    
    /** Launch the OAuth consent flow. Call from Activity context. */
    fun launchOAuthFlow(activity: Activity) {
        val clientId = BuildConfig.DRIVE_CLIENT_ID
        val redirectUri = "com.fieldnotes.app:/oauth2callback"
        val authUrl = "https://accounts.google.com/o/oauth2/auth" +
            "?client_id=$clientId" +
            "&redirect_uri=${Uri.encode(redirectUri)}" +
            "&response_type=code" +
            "&scope=${Uri.encode("https://www.googleapis.com/auth/drive.file")}" +
            "&access_type=offline"
        activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(authUrl)))
    }
    
    /** Handle the OAuth redirect in MainActivity */
    suspend fun handleOAuthCallback(code: String) {
        // Exchange code for tokens
        // Store access_token and refresh_token in DataStore
        // ... HTTP POST to https://oauth2.googleapis.com/token
    }
    
    fun isAuthenticated(): Flow<Boolean> = credentialStore.hasValidToken()
}
```

### OAuth redirect handling

In `AndroidManifest.xml`, register an intent filter on `MainActivity` or a dedicated `OAuthCallbackActivity`:

```xml
<activity android:name=".OAuthCallbackActivity">
    <intent-filter>
        <action android:name="android.intent.action.VIEW"/>
        <category android:name="android.intent.category.DEFAULT"/>
        <category android:name="android.intent.category.BROWSABLE"/>
        <data
            android:scheme="com.fieldnotes.app"
            android:host="oauth2callback"/>
    </intent-filter>
</activity>
```

### Scope

Use `https://www.googleapis.com/auth/drive.file` — this grants access **only** to files created by FieldNotes. It does not give access to the user's other Drive files. This is the correct, minimal scope.

## `DriveSync.kt`

```kotlin
@Singleton
class DriveSync @Inject constructor(
    private val authManager: DriveAuthManager,
    private val syncQueueDao: SyncQueueDao
) {
    /** Upload a single file to the given Drive folder. Creates folder if needed. */
    suspend fun uploadFile(
        localFile: File,
        driveFolderName: String,
        existingDriveFileId: String? = null
    ): String = withContext(Dispatchers.IO) {
        val drive = authManager.getDriveService()
            ?: throw IllegalStateException("Not authenticated with Drive")
        
        // Find or create the FieldNotes root folder
        val rootFolderId = findOrCreateFolder(drive, "FieldNotes", null)
        val targetFolderId = findOrCreateFolder(drive, driveFolderName, rootFolderId)
        
        val mimeType = when (localFile.extension.lowercase()) {
            "flac" -> "audio/flac"
            "wav"  -> "audio/wav"
            "m4a"  -> "audio/mp4"
            "md"   -> "text/markdown"
            else   -> "application/octet-stream"
        }
        
        val metadata = com.google.api.services.drive.model.File().apply {
            name = localFile.name
            parents = listOf(targetFolderId)
        }
        
        val content = FileContent(mimeType, localFile)
        
        return@withContext if (existingDriveFileId != null) {
            // Update existing file
            drive.files().update(existingDriveFileId, metadata, content)
                .execute().id
        } else {
            // Create new file
            drive.files().create(metadata, content)
                .setFields("id")
                .execute().id
        }
    }
    
    private fun findOrCreateFolder(drive: Drive, name: String, parentId: String?): String {
        val query = buildString {
            append("name='$name' and mimeType='application/vnd.google-apps.folder'")
            append(" and trashed=false")
            if (parentId != null) append(" and '$parentId' in parents")
        }
        val result = drive.files().list()
            .setQ(query)
            .setFields("files(id)")
            .execute()
        
        return result.files.firstOrNull()?.id ?: run {
            val folderMetadata = com.google.api.services.drive.model.File().apply {
                this.name = name
                mimeType = "application/vnd.google-apps.folder"
                if (parentId != null) parents = listOf(parentId)
            }
            drive.files().create(folderMetadata).setFields("id").execute().id
        }
    }
}
```

## `SyncWorker.kt`

```kotlin
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val driveSync: DriveSync,
    private val syncQueueDao: SyncQueueDao,
    private val recordingDao: RecordingDao,
    private val noteDao: NoteDao,
    private val settingsRepo: SettingsRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val pending = syncQueueDao.getPendingItems()
        if (pending.isEmpty()) return Result.success()
        
        var allSucceeded = true
        for (item in pending) {
            try {
                val file = File(item.filePath)
                if (!file.exists()) {
                    syncQueueDao.delete(item)
                    continue
                }
                val driveId = driveSync.uploadFile(file, item.driveFolderName)
                // Update status in the appropriate DAO
                when (item.fileType) {
                    "FIELD_RECORDING", "VOICE_NOTE" -> 
                        recordingDao.updateSyncStatus(file.nameWithoutExtension, "SYNCED", driveId)
                    "NOTE" ->
                        noteDao.updateSyncStatus(file.name, "SYNCED", driveId)
                }
                syncQueueDao.delete(item)
            } catch (e: Exception) {
                syncQueueDao.markAttempt(item.id, e.message)
                allSucceeded = false
            }
        }
        return if (allSucceeded) Result.success() else Result.retry()
    }
}
```

## Scheduling sync

```kotlin
// In RecordingRepository, after a recording is saved:
fun scheduleSync(wifiOnly: Boolean) {
    val constraints = Constraints.Builder()
        .apply {
            if (wifiOnly) setRequiredNetworkType(NetworkType.UNMETERED)
            else setRequiredNetworkType(NetworkType.CONNECTED)
        }
        .build()
    
    val request = OneTimeWorkRequestBuilder<SyncWorker>()
        .setConstraints(constraints)
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
        .build()
    
    WorkManager.getInstance(context)
        .enqueueUniqueWork("drive_sync", ExistingWorkPolicy.KEEP, request)
}
```

## Graceful degradation (no credentials)

If `BuildConfig.DRIVE_CLIENT_ID == "UNCONFIGURED"` or the user has not authenticated:
- All recordings are saved locally as normal.
- `SyncQueue` entries accumulate but `SyncWorker` returns `Result.success()` immediately without attempting upload.
- A banner is shown on the recordings list: "Sync not configured — tap to set up Google Drive."
- All app functionality except sync works fully.

## Settings for sync

Exposed in `SettingsScreen`:
- **Sync over WiFi only** (default: true) — toggle
- **Google Drive account** — shows connected email or "Connect Drive" button
- **Pending uploads** — count of items in sync queue
- **Sync now** button — triggers `SyncWorker` immediately (respects WiFi setting)
