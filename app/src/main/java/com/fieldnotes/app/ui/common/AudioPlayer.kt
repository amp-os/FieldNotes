// FieldNotes — AudioPlayer.kt
// Authored by: ui module | Implements: 08_UI_MODULE.md (recording playback)
// A self-contained play/pause + seek control backed by Android MediaPlayer. Plays the
// app's FLAC/WAV/AAC outputs (all natively supported) directly from the local file.
package com.fieldnotes.app.ui.common

import android.media.MediaPlayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.io.File
import java.util.Locale

/** Play/pause + seek control for a single local audio [file]. */
@Composable
fun AudioPlayer(file: File, modifier: Modifier = Modifier) {
    var isPlaying by remember { mutableStateOf(false) }
    var prepared by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var durationMs by remember { mutableIntStateOf(0) }
    var positionMs by remember { mutableIntStateOf(0) }

    val player = remember(file.absolutePath) {
        MediaPlayer().apply {
            setOnPreparedListener { prepared = true; durationMs = duration }
            setOnCompletionListener { isPlaying = false; positionMs = 0; seekTo(0) }
            setOnErrorListener { _, _, _ -> failed = true; isPlaying = false; true }
            try {
                setDataSource(file.absolutePath)
                prepareAsync()
            } catch (_: Exception) {
                failed = true
            }
        }
    }

    DisposableEffect(player) {
        onDispose { runCatching { player.release() } }
    }

    // Advance the position display while playing.
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            positionMs = runCatching { player.currentPosition }.getOrDefault(positionMs)
            delay(200)
        }
    }

    if (failed) {
        Text("Unable to play this file", color = MaterialTheme.colorScheme.error, modifier = modifier)
        return
    }

    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconButton(
            enabled = prepared,
            onClick = {
                if (isPlaying) {
                    player.pause(); isPlaying = false
                } else {
                    player.start(); isPlaying = true
                }
            },
        ) {
            Icon(
                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
            )
        }
        Slider(
            value = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f,
            onValueChange = { frac ->
                val p = (frac * durationMs).toInt()
                runCatching { player.seekTo(p) }
                positionMs = p
            },
            enabled = prepared,
            modifier = Modifier.weight(1f),
        )
        Text(
            "${formatTime(positionMs)} / ${formatTime(durationMs)}",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private fun formatTime(ms: Int): String {
    val totalSec = ms / 1000
    return String.format(Locale.getDefault(), "%d:%02d", totalSec / 60, totalSec % 60)
}
