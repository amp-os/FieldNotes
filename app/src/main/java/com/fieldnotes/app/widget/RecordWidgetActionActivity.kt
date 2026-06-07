// FieldNotes — RecordWidgetActionActivity.kt
// Authored by: widget module | Implements: 09_WIDGET_MODULE.md
// Transparent, invisible activity: widgets cannot start a foreground service directly on Android 12+,
// but an activity can. Starts the RecordingService then finishes immediately.
package com.fieldnotes.app.widget

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.fieldnotes.app.service.RecordingService

class RecordWidgetActionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val action = intent.action
        if (action == RecordingService.ACTION_START_FIELD || action == RecordingService.ACTION_START_VOICE) {
            startForegroundService(RecordingService.startIntent(this, action))
        }
        finish()
    }
}
