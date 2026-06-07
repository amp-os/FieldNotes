// FieldNotes — LocalFileManagerTest.kt
// Authored by: testing | Implements: 10_TESTING_PLAN.md (LocalFileManager 80%+ target)
package com.fieldnotes.app.core.storage

import android.content.Context
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class LocalFileManagerTest {

    private lateinit var lfm: LocalFileManager

    @BeforeEach
    fun setup(@TempDir tempDir: File) {
        val context = mockk<Context>()
        every { context.filesDir } returns tempDir
        lfm = LocalFileManager(context)
    }

    @Test
    fun `creates directory structure`() {
        assertThat(lfm.recordingsDir.isDirectory).isTrue()
        assertThat(lfm.voiceDir.isDirectory).isTrue()
        assertThat(lfm.notesDir.isDirectory).isTrue()
        assertThat(lfm.whisperDir.isDirectory).isTrue()
        assertThat(lfm.tempDir.isDirectory).isTrue()
    }

    @Test
    fun `field and voice files land in their dirs with expected extensions`() {
        assertThat(lfm.newFieldRecordingFile("flac").parentFile).isEqualTo(lfm.recordingsDir)
        assertThat(lfm.newFieldRecordingFile("wav").name).endsWith(".wav")
        assertThat(lfm.newVoiceNoteFile().name).endsWith(".m4a")
        assertThat(lfm.newVoiceNoteFile().parentFile).isEqualTo(lfm.voiceDir)
    }

    @Test
    fun `noteFile prevents path traversal`() {
        val f = lfm.noteFile("../../evil")
        assertThat(f.parentFile).isEqualTo(lfm.notesDir)
        assertThat(f.name).isEqualTo("evil.md")
    }

    @Test
    fun `noteFile blank input becomes untitled`() {
        assertThat(lfm.noteFile("   ").name).isEqualTo("untitled.md")
    }

    @Test
    fun `listNoteFilenames returns only md files sorted`() {
        File(lfm.notesDir, "b.md").writeText("x")
        File(lfm.notesDir, "a.md").writeText("x")
        File(lfm.notesDir, "ignore.txt").writeText("x")
        assertThat(lfm.listNoteFilenames()).containsExactly("a.md", "b.md").inOrder()
    }

    @Test
    fun `cleanupTempFiles empties temp dir`() {
        File(lfm.tempDir, "scratch.pcm").writeText("data")
        lfm.cleanupTempFiles()
        assertThat(lfm.tempDir.listFiles()?.size ?: 0).isEqualTo(0)
    }

    @Test
    fun `totalStorageUsed sums recording and note bytes`() {
        File(lfm.recordingsDir, "f.flac").writeText("12345")
        File(lfm.notesDir, "n.md").writeText("678")
        assertThat(lfm.totalStorageUsed()).isEqualTo(8L)
    }

    @Test
    fun `listRecordings includes both recordings and voice`() {
        File(lfm.recordingsDir, "f.flac").writeText("x")
        File(lfm.voiceDir, "v.m4a").writeText("x")
        assertThat(lfm.listRecordings().map { it.name }).containsExactly("f.flac", "v.m4a")
    }
}
