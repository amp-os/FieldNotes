// FieldNotes — SyncStatusIcon.kt
// Authored by: ui module | Implements: 08_UI_MODULE.md (per-file sync status)
package com.fieldnotes.app.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.fieldnotes.app.core.sync.SyncStatus

@Composable
fun SyncStatusIcon(status: SyncStatus, modifier: Modifier = Modifier) {
    val (icon, tint, desc) = when (status) {
        SyncStatus.PENDING -> Triple(Icons.Default.CloudQueue, FieldGray, "Pending upload")
        SyncStatus.UPLOADING -> Triple(Icons.Default.Cloud, FieldAmber, "Uploading")
        SyncStatus.SYNCED -> Triple(Icons.Default.CloudDone, Color(0xFF7FB069), "Synced")
        SyncStatus.ERROR -> Triple(Icons.Default.CloudOff, FieldRed, "Sync error")
    }
    Icon(icon, contentDescription = desc, tint = tint, modifier = modifier)
}
