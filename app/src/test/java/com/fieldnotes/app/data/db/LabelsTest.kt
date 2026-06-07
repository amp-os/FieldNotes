// FieldNotes — LabelsTest.kt
// Authored by: testing | Implements: 10_TESTING_PLAN.md (label round-trip + tolerance)
package com.fieldnotes.app.data.db

import com.fieldnotes.app.core.sync.SyncStatus
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class LabelsTest {

    private fun rec(labels: String) =
        RecordingEntity(filePath = "/x", mode = "FIELD", createdAt = 0, durationMs = 0, fileSizeBytes = 0, sampleRate = 0, labels = labels)

    @Test
    fun `round-trips labels`() {
        val json = listOf("nature", "birds").toLabelsJson()
        assertThat(rec(json).labelList()).containsExactly("nature", "birds").inOrder()
    }

    @Test
    fun `empty json yields empty list`() {
        assertThat(rec("[]").labelList()).isEmpty()
    }

    @Test
    fun `malformed json is tolerated`() {
        assertThat(rec("not json").labelList()).isEmpty()
    }

    @Test
    fun `sync status parsing is null-safe`() {
        assertThat(SyncStatus.fromString("SYNCED")).isEqualTo(SyncStatus.SYNCED)
        assertThat(SyncStatus.fromString(null)).isEqualTo(SyncStatus.PENDING)
        assertThat(SyncStatus.fromString("garbage")).isEqualTo(SyncStatus.PENDING)
    }
}
