// FieldNotes — RecordWidgetReceiver.kt
// Authored by: widget module | Implements: 09_WIDGET_MODULE.md
package com.fieldnotes.app.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

class RecordWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RecordWidget()
}
