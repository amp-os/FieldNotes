// FieldNotes — TranscriptionScreen.kt
// Authored by: ui module | Implements: 08_UI_MODULE.md
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

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
            when (val state = uiState) {
                TranscriptionUiState.Transcribing -> Centered {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("Transcribing…")
                }
                TranscriptionUiState.Saving -> Centered {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("Saving…")
                }
                is TranscriptionUiState.Error -> Centered { Text(state.message) }
                is TranscriptionUiState.Ready -> ReadyContent(state, viewModel, onSaved, onDiscarded)
            }
        }
    }
}

@Composable
private fun ReadyContent(
    state: TranscriptionUiState.Ready,
    viewModel: TranscriptionViewModel,
    onSaved: () -> Unit,
    onDiscarded: () -> Unit,
) {
    var newFilename by remember { mutableStateOf("") }
    var selectedNote by remember { mutableStateOf<String?>(null) }
    var dropdownExpanded by remember { mutableStateOf(false) }
    val allLabels by viewModel.allLabels.collectAsStateWithLifecycle()

    val effectiveFilename = when {
        newFilename.isNotBlank() -> newFilename
        selectedNote != null -> selectedNote!!
        else -> ""
    }

    Column(
        Modifier.fillMaxSize().padding(16.dp),
    ) {
        Text(state.audioFileName, fontFamily = FontFamily.Monospace)
        Text("Duration: ${state.durationLabel}")
        if (state.modelMissing) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Transcription model not downloaded — you can still type a note below " +
                    "(Settings → download the model to enable automatic transcription).",
                color = FieldRed,
            )
        }
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = state.text,
            onValueChange = viewModel::updateText,
            label = { Text("Transcription") },
            modifier = Modifier.fillMaxWidth().height(200.dp).testTag("transcription_text"),
        )

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

        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onDiscarded, modifier = Modifier.weight(1f)) { Text("DISCARD") }
            Button(
                onClick = { viewModel.save(effectiveFilename, onSaved) },
                enabled = effectiveFilename.isNotBlank(),
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(),
            ) { Text("SAVE NOTE") }
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
