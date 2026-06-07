// FieldNotes — MainActivity.kt
// Authored by: ui module | Implements: 08_UI_MODULE.md (host activity + nav graph)
package com.fieldnotes.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.fieldnotes.app.ui.FieldNotesApp
import com.fieldnotes.app.ui.common.FieldNotesTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            FieldNotesTheme {
                FieldNotesApp()
            }
        }
    }
}
