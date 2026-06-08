// FieldNotes — LabelEditor.kt
// Authored by: ui module | Implements: 08_UI_MODULE.md (label management)
// Shared label editor: shows current labels as removable chips, offers one-touch
// suggestion chips for existing labels, and a text field for new ones.
@file:OptIn(ExperimentalLayoutApi::class)

package com.fieldnotes.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * @param labels      labels currently applied
 * @param suggestions all existing labels (those already applied are filtered out)
 */
@Composable
fun LabelEditor(
    labels: List<String>,
    suggestions: List<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var newLabel by remember { mutableStateOf("") }
    val available = suggestions.filter { it !in labels }

    Column(modifier.fillMaxWidth()) {
        if (labels.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                labels.forEach { label ->
                    InputChip(
                        selected = false,
                        onClick = { onRemove(label) },
                        label = { Text(label) },
                        trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Remove $label") },
                    )
                }
            }
        }
        if (available.isNotEmpty()) {
            Text(
                "Quick add",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                available.forEach { label ->
                    SuggestionChip(
                        onClick = { onAdd(label) },
                        label = { Text(label) },
                    )
                }
            }
        }
        OutlinedTextField(
            value = newLabel,
            onValueChange = { newLabel = it },
            label = { Text("Add label") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(onClick = {
            val trimmed = newLabel.trim()
            if (trimmed.isNotEmpty()) onAdd(trimmed)
            newLabel = ""
        }) {
            Icon(Icons.Default.Add, contentDescription = null)
            Text(" Add label")
        }
    }
}
