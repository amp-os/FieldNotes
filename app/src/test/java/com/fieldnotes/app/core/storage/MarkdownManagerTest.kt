// FieldNotes — MarkdownManagerTest.kt
// Authored by: testing | Implements: 10_TESTING_PLAN.md (MarkdownManager — 100% line coverage target)
package com.fieldnotes.app.core.storage

import android.content.Context
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class MarkdownManagerTest {

    private lateinit var notesDir: File
    private lateinit var manager: MarkdownManager
    private lateinit var localFileManager: LocalFileManager

    @BeforeEach
    fun setup(@TempDir tempDir: File) {
        val context = mockk<Context>()
        every { context.filesDir } returns tempDir
        localFileManager = LocalFileManager(context)
        manager = MarkdownManager(localFileManager)
        notesDir = localFileManager.notesDir
    }

    @Test
    fun `creates new file with title and entry`() = runTest {
        manager.prependEntry("organiser", "Hello world", timestamp = 1_700_000_000_000L)
        val content = File(notesDir, "organiser.md").readText()
        assertThat(content).startsWith("# Organiser")
        assertThat(content).contains("Hello world")
        assertThat(content).contains("## ")
        assertThat(content).contains("---")
    }

    @Test
    fun `prepends to existing file below H1`() = runTest {
        val file = File(notesDir, "organiser.md")
        file.writeText("# Organiser\n\n## 2024-01-01 — 10:00\n\nOld note.\n")
        manager.prependEntry("organiser", "New note", timestamp = 1_700_000_000_000L)
        val content = file.readText()
        assertThat(content).startsWith("# Organiser")
        assertThat(content.indexOf("New note")).isLessThan(content.indexOf("Old note"))
    }

    @Test
    fun `prepends at top when existing file has no H1`() = runTest {
        val file = File(notesDir, "plain.md")
        file.writeText("## 2024-01-01 — 10:00\n\nOld note.\n")
        manager.prependEntry("plain", "New note")
        val content = file.readText()
        assertThat(content.indexOf("New note")).isLessThan(content.indexOf("Old note"))
        assertThat(content).doesNotContain("# Plain")
    }

    @Test
    fun `handles H1 with no trailing newline`() = runTest {
        val file = File(notesDir, "edge.md")
        file.writeText("# Edge") // no newline at all
        manager.prependEntry("edge", "Body")
        val content = file.readText()
        assertThat(content).startsWith("# Edge")
        assertThat(content).contains("Body")
    }

    @Test
    fun `sanitises dangerous filenames`() = runTest {
        manager.prependEntry("../../../etc/passwd", "Attack", timestamp = 0L)
        assertThat(File("/etc/passwd").readText()).doesNotContain("Attack")
        // The entry must land safely inside notesDir instead.
        assertThat(File(notesDir, "passwd.md").exists()).isTrue()
    }

    @Test
    fun `appends dot md if missing`() = runTest {
        manager.prependEntry("myfile", "text", 0L)
        assertThat(File(notesDir, "myfile.md").exists()).isTrue()
    }

    @Test
    fun `keeps existing dot md extension`() = runTest {
        manager.prependEntry("already.md", "text", 0L)
        assertThat(File(notesDir, "already.md").exists()).isTrue()
        assertThat(File(notesDir, "already.md.md").exists()).isFalse()
    }

    @Test
    fun `readNote returns content and null`() = runTest {
        assertThat(manager.readNote("missing")).isNull()
        manager.prependEntry("present", "hello", 0L)
        assertThat(manager.readNote("present")).contains("hello")
    }

    @Test
    fun `listNotes reports created notes`() = runTest {
        manager.prependEntry("a", "x", 0L)
        manager.prependEntry("b", "y", 0L)
        val notes = manager.listNotes()
        assertThat(notes.map { it.displayName }).containsExactly("a", "b")
        assertThat(notes.first().filename).endsWith(".md")
        assertThat(notes.first().sizeBytes).isGreaterThan(0L)
    }
}
