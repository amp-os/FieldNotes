// FieldNotes — RecordWidget.kt
// Authored by: widget module | Implements: 09_WIDGET_MODULE.md (Jetpack Glance widget)
package com.fieldnotes.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.fieldnotes.app.service.RecordingService

class RecordWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { RecordWidgetContent(context) }
    }
}

private fun actionIntent(context: Context, action: String): Intent =
    Intent(context, RecordWidgetActionActivity::class.java).setAction(action)

@androidx.compose.runtime.Composable
private fun RecordWidgetContent(context: Context) {
    GlanceTheme {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(Color(0xFF0D0D0D))
                .cornerRadius(16.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(modifier = GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                WidgetButton(
                    label = "🎙 Field Rec",
                    onClick = actionStartActivity(actionIntent(context, RecordingService.ACTION_START_FIELD)),
                    modifier = GlanceModifier.defaultWeight().padding(end = 4.dp),
                )
                WidgetButton(
                    label = "📝 Voice Note",
                    onClick = actionStartActivity(actionIntent(context, RecordingService.ACTION_START_VOICE)),
                    modifier = GlanceModifier.defaultWeight().padding(start = 4.dp),
                )
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun WidgetButton(
    label: String,
    onClick: Action,
    modifier: GlanceModifier = GlanceModifier,
) {
    Box(
        modifier = modifier
            .background(Color(0xFF1A1A1A))
            .cornerRadius(12.dp)
            .padding(vertical = 14.dp)
            .clickable(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = TextStyle(
                color = ColorProvider(Color(0xFFE8A838)),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            ),
        )
    }
}
