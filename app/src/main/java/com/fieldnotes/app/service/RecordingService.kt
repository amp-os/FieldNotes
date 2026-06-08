// FieldNotes — RecordingService.kt
// Authored by: audio module | Implements: 04_AUDIO_MODULE.md / 09_WIDGET_MODULE.md (notification)
package com.fieldnotes.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.Manifest
import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.fieldnotes.app.MainActivity
import com.fieldnotes.app.R
import com.fieldnotes.app.core.audio.RecordingMode
import com.fieldnotes.app.core.audio.RecordingSessionManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class RecordingService : Service() {

    @Inject lateinit var sessionManager: RecordingSessionManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var tickerJob: Job? = null
    private var startElapsed = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_FIELD -> startRecording(RecordingMode.FIELD)
            ACTION_START_VOICE -> startRecording(RecordingMode.VOICE_NOTE)
            ACTION_STOP -> stopRecording()
        }
        return START_STICKY
    }

    @SuppressLint("MissingPermission") // guarded by the checkSelfPermission below
    private fun startRecording(mode: RecordingMode) {
        // A microphone foreground service can't be started without RECORD_AUDIO. The in-app buttons
        // request it, but the widget / Quick Settings tile can reach here first — bounce to the app
        // to request the permission rather than crashing with a SecurityException.
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            startActivity(
                Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            stopSelf()
            return
        }

        try {
            ensureChannel()
            startElapsed = SystemClock.elapsedRealtime()
            startForegroundCompat(buildNotification(mode, "00:00:00"))
            sessionManager.start(mode)
        } catch (e: Exception) {
            // e.g. ForegroundServiceStartNotAllowedException / SecurityException when not in an
            // eligible state to start a mic FGS. Don't crash; just stop.
            Log.e(TAG, "Could not start recording foreground service", e)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive) {
                val notif = buildNotification(mode, formatElapsed(SystemClock.elapsedRealtime() - startElapsed))
                getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notif)
                delay(1000)
            }
        }
    }

    private fun stopRecording() {
        tickerJob?.cancel()
        scope.launch {
            sessionManager.stop()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.recording_channel_name),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply { description = getString(R.string.recording_channel_desc) },
            )
        }
    }

    private fun buildNotification(mode: RecordingMode, elapsed: String): Notification {
        val title = if (mode == RecordingMode.FIELD) "● Field Recording" else "● Voice Note"
        val contentIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stopIntent = PendingIntent.getService(
            this, 1, Intent(this, RecordingService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FieldNotes")
            .setContentText("$title  $elapsed")
            .setSmallIcon(R.drawable.ic_mic)
            .setContentIntent(contentIntent)
            .addAction(R.drawable.ic_stop, "Stop", stopIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun formatElapsed(ms: Long): String {
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return "%02d:%02d:%02d".format(h, m, s)
    }

    override fun onDestroy() {
        tickerJob?.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START_FIELD = "com.fieldnotes.app.action.START_FIELD"
        const val ACTION_START_VOICE = "com.fieldnotes.app.action.START_VOICE"
        const val ACTION_STOP = "com.fieldnotes.app.action.STOP"

        private const val TAG = "RecordingService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "recording_channel"

        fun startIntent(context: Context, action: String): Intent =
            Intent(context, RecordingService::class.java).apply { this.action = action }
    }
}
