// FieldNotes — RecordWidgetActionActivity.kt
// Authored by: widget module | Implements: 09_WIDGET_MODULE.md
// Transparent, invisible activity: widgets/tiles cannot start a microphone foreground service from
// the background on Android 12+, but an Activity can (it grants while-in-use access). Starts the
// RecordingService, then opens the app on the Recorder screen so the user sees the live recording.
package com.fieldnotes.app.widget

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.fieldnotes.app.MainActivity
import com.fieldnotes.app.service.RecordingService

class RecordWidgetActionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val action = intent.action
        if (action == RecordingService.ACTION_START_FIELD || action == RecordingService.ACTION_START_VOICE) {
            startForegroundService(RecordingService.startIntent(this, action))
            // Bring the app to the foreground so the recorder UI (waveform + timer) is visible.
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            )
        }
        finish()
    }
}
