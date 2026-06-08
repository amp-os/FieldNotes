// FieldNotes — RecordTileService.kt
// Authored by: widget module | Implements: 09_WIDGET_MODULE.md (Quick Settings tiles)
package com.fieldnotes.app.widget

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService

/**
 * Shared launch logic for the recording Quick Settings tiles. Routes through the transparent
 * trampoline Activity so the microphone foreground service starts from a visible Activity — a
 * background (tile) start is denied while-in-use mic access on Android 12+.
 */
abstract class RecordTileService : TileService() {

    /** The RecordingService.ACTION_START_* this tile triggers. */
    protected abstract val recordAction: String

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, RecordWidgetActionActivity::class.java).apply {
            action = recordAction
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this, recordAction.hashCode(), intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(intent)
        }
    }
}
