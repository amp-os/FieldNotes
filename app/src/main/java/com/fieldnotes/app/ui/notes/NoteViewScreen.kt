// FieldNotes — NoteViewScreen.kt
// Authored by: ui module | Implements: 08_UI_MODULE.md (read-only markdown view)
@file:OptIn(ExperimentalMaterial3Api::class)

package com.fieldnotes.app.ui.notes

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.fieldnotes.app.data.repository.NoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class NoteViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val noteRepository: NoteRepository,
) : ViewModel() {
    val filename: String = checkNotNull(savedStateHandle["filename"])
    suspend fun load(): String = noteRepository.readNote(filename) ?: "(empty)"
}

@Composable
fun NoteViewScreen(
    onBack: () -> Unit,
    viewModel: NoteViewModel = hiltViewModel(),
) {
    val content by produceState(initialValue = "Loading…", viewModel) { value = viewModel.load() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(viewModel.filename, fontFamily = FontFamily.Monospace) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Text(
            content,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        )
    }
}
