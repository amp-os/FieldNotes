// FieldNotes — Theme.kt
// Authored by: lead architect | Implements: 08_UI_MODULE.md (dark-by-default Material 3 theme)
package com.fieldnotes.app.ui.common

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily

private val FieldNotesColors = darkColorScheme(
    primary = FieldAmber,
    onPrimary = FieldBlack,
    secondary = FieldAmber,
    error = FieldRed,
    background = FieldBlack,
    onBackground = FieldWarmWhite,
    surface = FieldSurface,
    onSurface = FieldWarmWhite,
    surfaceVariant = FieldSurfaceVariant,
    onSurfaceVariant = FieldWarmWhite,
)

/** Monospace style for filenames / timestamps (08_UI_MODULE.md). */
val MonoTextStyle = TextStyle(fontFamily = FontFamily.Monospace)

@Composable
fun FieldNotesTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    // FieldNotes is dark-by-default regardless of system setting.
    MaterialTheme(
        colorScheme = FieldNotesColors,
        typography = Typography(),
        content = content,
    )
}
