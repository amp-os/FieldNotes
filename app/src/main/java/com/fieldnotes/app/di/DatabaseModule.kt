// FieldNotes — DatabaseModule.kt
// Authored by: DI | Implements: 02_ARCHITECTURE.md (Hilt). Other classes are constructor-injected.
package com.fieldnotes.app.di

import android.content.Context
import androidx.room.Room
import com.fieldnotes.app.data.db.FieldNotesDatabase
import com.fieldnotes.app.data.db.NoteDao
import com.fieldnotes.app.data.db.RecordingDao
import com.fieldnotes.app.data.db.SyncQueueDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): FieldNotesDatabase =
        Room.databaseBuilder(context, FieldNotesDatabase::class.java, "fieldnotes.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideRecordingDao(db: FieldNotesDatabase): RecordingDao = db.recordingDao()
    @Provides fun provideNoteDao(db: FieldNotesDatabase): NoteDao = db.noteDao()
    @Provides fun provideSyncQueueDao(db: FieldNotesDatabase): SyncQueueDao = db.syncQueueDao()
}
