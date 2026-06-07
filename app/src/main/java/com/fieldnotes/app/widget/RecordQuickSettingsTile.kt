// FieldNotes — RecordQuickSettingsTile.kt
// Authored by: widget module | Implements: 09_WIDGET_MODULE.md (Quick Settings tile — fastest capture)
package com.fieldnotes.app.widget

import android.content.Intent
import android.service.quicksettings.TileService
import com.fieldnotes.app.service.RecordingService

class RecordQuickSettingsTile : TileService() {
    override fun onClick() {
        super.onClick()
        // Voice note is the common quick-capture case.
        startForegroundService(
            Intent(this, RecordingService::class.java).apply {
                action = RecordingService.ACTION_START_VOICE
            },
        )
    }
}
