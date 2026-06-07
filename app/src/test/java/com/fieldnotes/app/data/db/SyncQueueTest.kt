// FieldNotes — SyncQueueTest.kt
// Authored by: testing | Implements: 10_TESTING_PLAN.md (Room integration; Robolectric)
// Runs on the JUnit Platform via the vintage engine (JUnit4 @RunWith).
package com.fieldnotes.app.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.fieldnotes.app.core.sync.SyncWorker
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncQueueTest {

    private lateinit var db: FieldNotesDatabase

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            FieldNotesDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun enqueue_and_retrieve_pending_items() = runTest {
        db.syncQueueDao().enqueue(
            SyncQueueEntity(filePath = "/tmp/test.flac", fileType = SyncWorker.TYPE_FIELD, driveFolderName = "recordings"),
        )
        val pending = db.syncQueueDao().getPendingItems()
        assertThat(pending).hasSize(1)
        assertThat(pending[0].filePath).isEqualTo("/tmp/test.flac")
    }

    @Test
    fun recording_round_trip_and_sync_status_update() = runTest {
        val dao = db.recordingDao()
        val rec = RecordingEntity(
            filePath = "/tmp/a.m4a", mode = "VOICE_NOTE", createdAt = 1L,
            durationMs = 1000, fileSizeBytes = 10, sampleRate = 16000,
        )
        dao.insert(rec)
        dao.updateSyncStatusByPath("/tmp/a.m4a", "SYNCED", "drive123")
        val loaded = dao.getById(rec.id)
        assertThat(loaded?.syncStatus).isEqualTo("SYNCED")
        assertThat(loaded?.driveFileId).isEqualTo("drive123")
    }

    @Test
    fun mark_attempt_increments_and_records_error() = runTest {
        val dao = db.syncQueueDao()
        dao.enqueue(SyncQueueEntity(filePath = "/tmp/x.md", fileType = SyncWorker.TYPE_NOTE, driveFolderName = "notes"))
        val item = dao.getPendingItems().first()
        dao.markAttempt(item.id, "boom")
        val after = dao.getPendingItems().first()
        assertThat(after.attempts).isEqualTo(1)
        assertThat(after.lastError).isEqualTo("boom")
    }
}
