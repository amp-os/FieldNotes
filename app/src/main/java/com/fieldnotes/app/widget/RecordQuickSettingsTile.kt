// FieldNotes — RecordQuickSettingsTile.kt
// Authored by: widget module | Implements: 09_WIDGET_MODULE.md (Quick Settings tile — fastest capture)
package com.fieldnotes.app.widget

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import com.fieldnotes.app.service.RecordingService

class RecordQuickSettingsTile : TileService() {
    override fun onClick() {
        super.onClick()
        // A microphone foreground service started directly from a tile (background) is denied
        // while-in-use mic access on Android 12+. Launch the transparent trampoline Activity instead
        // — starting the FGS from a visible Activity grants mic access (same path as the widget).
        val intent = Intent(this, RecordWidgetActionActivity::class.java).apply {
            action = RecordingService.ACTION_START_VOICE // voice note = common quick-capture case
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(intent)
        }
    }
}
