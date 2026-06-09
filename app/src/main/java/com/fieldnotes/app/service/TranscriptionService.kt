// FieldNotes — TranscriptionService.kt
// Authored by: whisper module | Implements: 05_WHISPER_MODULE.md (durable background transcription)
// A long voice-note transcription can take minutes. The work runs in the app-scoped
// TranscriptionManager, but without a foreground service the OS may kill the process while the app
// is backgrounded and lose the job. This dataSync FGS keeps the process alive while a transcription
// is in flight; TranscriptionManager starts it on the first running job and stops it when none remain.
package com.fieldnotes.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.fieldnotes.app.MainActivity
import com.fieldnotes.app.R

class TranscriptionService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            ensureChannel()
            startForegroundCompat(buildNotification())
        } catch (e: Exception) {
            // e.g. ForegroundServiceStartNotAllowedException if we somehow weren't eligible. Don't
            // crash — the transcription still runs in the manager's app scope, just not protected.
            Log.e(TAG, "Could not start transcription foreground service", e)
            stopSelf()
        }
        return START_STICKY
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Transcription", NotificationManager.IMPORTANCE_LOW)
                    .apply { description = "Shown while a voice note is being transcribed on-device." },
            )
        }
    }

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FieldNotes")
            .setContentText("Transcribing voice note…")
            .setSmallIcon(R.drawable.ic_mic)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    companion object {
        const val ACTION_STOP = "com.fieldnotes.app.action.STOP_TRANSCRIPTION"
        private const val TAG = "TranscriptionService"
        private const val NOTIFICATION_ID = 1002
        private const val CHANNEL_ID = "transcription_channel"

        /** Keep the process alive while transcription runs. Safe to call repeatedly. */
        fun start(context: Context) {
            val intent = Intent(context, TranscriptionService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            // stopService tears down directly (→ onDestroy removes the FGS notification) and avoids
            // the background-service-start restriction that delivering a stop action would hit.
            context.stopService(Intent(context, TranscriptionService::class.java))
        }
    }
}
