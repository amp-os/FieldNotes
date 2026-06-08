// FieldNotes — RecordQuickSettingsTile.kt
// Authored by: widget module | Implements: 09_WIDGET_MODULE.md (voice-note Quick Settings tile)
package com.fieldnotes.app.widget

import com.fieldnotes.app.service.RecordingService

/** Quick Settings tile that starts a voice note (the common quick-capture case). */
class RecordQuickSettingsTile : RecordTileService() {
    override val recordAction = RecordingService.ACTION_START_VOICE
}
