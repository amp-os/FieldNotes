// FieldNotes — TranscriptionScreen.kt
// Authored by: ui module | Implements: 08_UI_MODULE.md (issue 2: non-blocking transcription)
@file:OptIn(ExperimentalMaterial3Api::class)

package com.fieldnotes.app.ui.transcription

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fieldnotes.app.ui.common.FieldRed
import com.fieldnotes.app.ui.common.LabelEditor

@Composable
fun TranscriptionScreen(
    onSaved: () -> Unit,
    onDiscarded: () -> Unit,
    viewModel: TranscriptionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val allLabels by viewModel.allLabels.collectAsStateWithLifecycle()

    // Close the screen once the note is saved or auto-save is armed.
    LaunchedEffect(Unit) {
        viewModel.navigateSaved.collect { onSaved() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transcription") },
                navigationIcon = {
                    IconButton(onClick = onDiscarded) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                !state.loaded -> Centered { CircularProgressIndicator() }
                state.notFound -> Centered { Text("Recording not found") }
                else -> Content(state, allLabels, viewModel, onDiscarded)
            }
        }
    }
}

@Composable
private fun Content(
    state: TranscriptionUiState,
    allLabels: List<String>,
    viewModel: TranscriptionViewModel,
    onDiscarded: () -> Unit,
) {
    var newFilename by remember { mutableStateOf("") }
    var selectedNote by remember { mutableStateOf<String?>(null) }
    var dropdownExpanded by remember { mutableStateOf(false) }

    val effectiveFilename = when {
        newFilename.isNotBlank() -> newFilename
        selectedNote != null -> selectedNote!!
        else -> ""
    }
    val hasDestination = effectiveFilename.isNotBlank()

    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
    ) {
        Text(state.audioFileName, fontFamily = FontFamily.Monospace)
        Text("Duration: ${state.durationLabel}")
        Spacer(Modifier.height(12.dp))

        // Transcription region: progress while running, editable text once ready.
        if (state.transcribing) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth().height(120.dp),
            ) {
                Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("Transcribing… pick a note and labels below in the meantime.")
                }
            }
        } else {
            if (state.modelMissing) {
                Text(
                    "Transcription model not downloaded — type your note below " +
                        "(Settings → download a model to enable automatic transcription).",
                    color = FieldRed,
                )
                Spacer(Modifier.height(8.dp))
            } else if (state.failed) {
                Text("Transcription failed${state.error?.let { ": $it" } ?: ""}. You can still type a note.", color = FieldRed)
                Spacer(Modifier.height(8.dp))
            }
            OutlinedTextField(
                value = state.text,
                onValueChange = viewModel::updateText,
                label = { Text("Transcription") },
                modifier = Modifier.fillMaxWidth().height(200.dp).testTag("transcription_text"),
            )
        }

        Spacer(Modifier.height(16.dp))
        Text("Save to note:")
        Box {
            OutlinedButton(onClick = { dropdownExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(selectedNote ?: "Select existing note", modifier = Modifier.weight(1f))
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = dropdownExpanded, onDismissRequest = { dropdownExpanded = false }) {
                if (state.existingNotes.isEmpty()) {
                    DropdownMenuItem(text = { Text("No notes yet") }, onClick = { dropdownExpanded = false })
                }
                state.existingNotes.forEach { note ->
                    DropdownMenuItem(
                        text = { Text(note) },
                        onClick = {
                            selectedNote = note
                            newFilename = ""
                            dropdownExpanded = false
                        },
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = newFilename,
            onValueChange = { newFilename = it; if (it.isNotBlank()) selectedNote = null },
            label = { Text("Or create new (.md added automatically)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("new_note_input"),
        )

        Spacer(Modifier.height(16.dp))
        Text("Labels:")
        LabelEditor(
            labels = state.labels,
            suggestions = allLabels,
            onAdd = { viewModel.updateLabels(state.labels + it) },
            onRemove = { viewModel.updateLabels(state.labels - it) },
        )

        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onDiscarded, modifier = Modifier.weight(1f)) { Text("DISCARD") }
            if (state.transcribing) {
                // No need to wait: append automatically as soon as the transcription is ready.
                Button(
                    onClick = { viewModel.saveWhenReady(effectiveFilename) },
                    enabled = hasDestination,
                    modifier = Modifier.weight(1f),
                ) { Text("SAVE WHEN READY") }
            } else {
                Button(
                    onClick = { viewModel.saveNow(effectiveFilename) },
                    enabled = hasDestination,
                    modifier = Modifier.weight(1f),
                ) { Text("SAVE NOTE") }
            }
        }
        if (state.transcribing && hasDestination) {
            Spacer(Modifier.height(8.dp))
            Text(
                "“Save when ready” appends the transcription to “$effectiveFilename” as soon as it finishes — you can leave now.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}
