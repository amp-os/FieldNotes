// FieldNotes — WaveformView.kt
// Authored by: ui module | Implements: 08_UI_MODULE.md
package com.fieldnotes.app.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

private const val BAR_COUNT = 60

@Composable
fun WaveformView(
    amplitude: Float, // 0f..1f
    isRecording: Boolean,
    modifier: Modifier = Modifier,
) {
    val history = remember { mutableStateListOf<Float>() }

    LaunchedEffect(amplitude, isRecording) {
        if (isRecording) {
            history.add(amplitude)
            while (history.size > BAR_COUNT) history.removeAt(0)
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
            .semantics { contentDescription = "Audio level meter" },
    ) {
        val barWidth = size.width / BAR_COUNT
        val maxHeight = size.height
        val color = if (isRecording) FieldAmber else FieldGray
        val minBar = 4.dp.toPx()
        history.forEachIndexed { i, amp ->
            val barHeight = (amp * maxHeight).coerceAtLeast(minBar)
            val x = i * barWidth + barWidth / 2
            drawLine(
                color = color,
                start = Offset(x, (maxHeight - barHeight) / 2),
                end = Offset(x, (maxHeight + barHeight) / 2),
                strokeWidth = barWidth * 0.6f,
                cap = StrokeCap.Round,
            )
        }
    }
}
