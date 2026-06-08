// FieldNotes — RecordFieldQuickSettingsTile.kt
// Authored by: widget module | Implements: 09_WIDGET_MODULE.md (field-recording Quick Settings tile)
package com.fieldnotes.app.widget

import com.fieldnotes.app.service.RecordingService

/** Quick Settings tile that starts a lossless field recording. */
class RecordFieldQuickSettingsTile : RecordTileService() {
    override val recordAction = RecordingService.ACTION_START_FIELD
}
