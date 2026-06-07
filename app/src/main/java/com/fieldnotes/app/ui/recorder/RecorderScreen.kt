// FieldNotes — RecorderScreen.kt
// Authored by: ui module | Implements: 08_UI_MODULE.md / 04_AUDIO_MODULE.md
@file:OptIn(ExperimentalPermissionsApi::class)

package com.fieldnotes.app.ui.recorder

import android.Manifest
import android.os.Build
import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.fieldnotes.app.core.audio.RecordingMode
import com.fieldnotes.app.ui.common.FieldAmber
import com.fieldnotes.app.ui.common.FieldRed
import com.fieldnotes.app.ui.common.WaveformView
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.delay

@Composable
fun RecorderScreen(
    onNavigateToTranscription: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecorderViewModel = hiltViewModel(),
) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val amplitude by viewModel.amplitude.collectAsStateWithLifecycle()
    val headsetConnected by viewModel.headsetConnected.collectAsStateWithLifecycle()
    val selectedSource by viewModel.selectedAudioSource.collectAsStateWithLifecycle()

    val permissions = buildList {
        add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
    }
    val permissionState = rememberMultiplePermissionsState(permissions)

    // Route completed recordings. This is driven by a *persisted* pending-completion (not a transient
    // event), so a voice note started from the widget/Quick-Settings tile — while this screen isn't
    // composed — still opens the transcription screen the next time the app is shown.
    val pendingCompletion by viewModel.pendingCompletion.collectAsStateWithLifecycle()
    LaunchedEffect(pendingCompletion) {
        val pending = pendingCompletion ?: return@LaunchedEffect
        viewModel.consumePendingCompletion()
        onNavigateToTranscription(pending.recordingId)
    }

    val isRecording = session != null

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "FieldNotes",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 12.dp),
        )

        AnimatedVisibility(visible = headsetConnected) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                viewModel.availableSources().forEach { (sourceId, label) ->
                    FilterChip(
                        selected = selectedSource == sourceId,
                        onClick = { viewModel.selectAudioSource(sourceId) },
                        label = { Text(label) },
                        enabled = !isRecording,
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        WaveformView(amplitude = amplitude, isRecording = isRecording)
        Spacer(Modifier.height(16.dp))

        RecordingTimer(isRecording = isRecording, startElapsed = session?.startElapsedRealtime ?: 0L)

        Spacer(Modifier.weight(1f))

        if (isRecording) {
            Button(
                onClick = { viewModel.stop() },
                modifier = Modifier.fillMaxWidth().height(96.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = FieldRed, contentColor = Color.White),
            ) {
                Text("STOP", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().height(160.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ModeButton(
                    title = "FIELD REC",
                    subtitle = "lossless",
                    // Always tappable: a tap either starts recording or triggers the permission
                    // request. Disabling it would leave no way to grant the permission.
                    enabled = true,
                    modifier = Modifier.weight(1f),
                ) {
                    if (permissionState.allPermissionsGranted) viewModel.startField()
                    else permissionState.launchMultiplePermissionRequest()
                }
                ModeButton(
                    title = "VOICE NOTE",
                    subtitle = "+ transcription",
                    enabled = true,
                    modifier = Modifier.weight(1f),
                ) {
                    if (permissionState.allPermissionsGranted) viewModel.startVoice()
                    else permissionState.launchMultiplePermissionRequest()
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ModeButton(
    title: String,
    subtitle: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxSize(),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = FieldAmber,
        ),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RecordingTimer(isRecording: Boolean, startElapsed: Long) {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(isRecording, startElapsed) {
        while (isRecording) {
            now = SystemClock.elapsedRealtime()
            delay(250)
        }
    }
    val elapsed = if (isRecording) ((now - startElapsed).coerceAtLeast(0)) / 1000 else 0
    val text = "%02d:%02d:%02d".format(elapsed / 3600, (elapsed % 3600) / 60, elapsed % 60)
    AnimatedVisibility(visible = isRecording) {
        Text(text, fontFamily = FontFamily.Monospace, fontSize = 28.sp, color = FieldAmber)
    }
}
