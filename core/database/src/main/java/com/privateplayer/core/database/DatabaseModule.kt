package com.privateplayer.core.database

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import com.privateplayer.core.database.dao.FolderDao
import com.privateplayer.core.database.dao.ImportTaskDao
import com.privateplayer.core.database.dao.MediaDao
import com.privateplayer.core.database.dao.PlaybackHistoryDao
import com.privateplayer.core.database.dao.PlaylistDao
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
    fun provideDatabase(@ApplicationContext context: Context): PrivatePlayerDatabase =
        Room.databaseBuilder(context, PrivatePlayerDatabase::class.java, PrivatePlayerDatabase.NAME)
            // WAL：导入任务在后台写、UI 同时读，避免互相阻塞。
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .build()

    @Provides
    fun provideMediaDao(database: PrivatePlayerDatabase): MediaDao = database.mediaDao()

    @Provides
    fun provideFolderDao(database: PrivatePlayerDatabase): FolderDao = database.folderDao()

    @Provides
    fun providePlaylistDao(database: PrivatePlayerDatabase): PlaylistDao = database.playlistDao()

    @Provides
    fun providePlaybackHistoryDao(database: PrivatePlayerDatabase): PlaybackHistoryDao =
        database.playbackHistoryDao()

    @Provides
    fun provideImportTaskDao(database: PrivatePlayerDatabase): ImportTaskDao = database.importTaskDao()
}
