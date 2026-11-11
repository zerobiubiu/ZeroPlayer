package com.privateplayer.data.di

import com.privateplayer.data.importer.ImportWorkScheduler
import com.privateplayer.data.importer.WorkManagerImportScheduler
import com.privateplayer.data.repository.FolderRepositoryImpl
import com.privateplayer.data.repository.FileTreeRepositoryImpl
import com.privateplayer.data.repository.ImportRepositoryImpl
import com.privateplayer.data.repository.MediaRepositoryImpl
import com.privateplayer.data.repository.PlaybackHistoryRepositoryImpl
import com.privateplayer.data.repository.PlaylistRepositoryImpl
import com.privateplayer.data.repository.StorageRepositoryImpl
import com.privateplayer.data.webupload.WebUploadServiceController
import com.privateplayer.domain.repository.FileTreeRepository
import com.privateplayer.domain.repository.FolderRepository
import com.privateplayer.domain.repository.ImportRepository
import com.privateplayer.domain.repository.MediaRepository
import com.privateplayer.domain.repository.PlaybackHistoryRepository
import com.privateplayer.domain.repository.PlaylistRepository
import com.privateplayer.domain.repository.StorageRepository
import com.privateplayer.domain.repository.WebUploadController
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindFileTreeRepository(impl: FileTreeRepositoryImpl): FileTreeRepository

    @Binds
    @Singleton
    abstract fun bindMediaRepository(impl: MediaRepositoryImpl): MediaRepository

    @Binds
    @Singleton
    abstract fun bindFolderRepository(impl: FolderRepositoryImpl): FolderRepository

    @Binds
    @Singleton
    abstract fun bindPlaylistRepository(impl: PlaylistRepositoryImpl): PlaylistRepository

    @Binds
    @Singleton
    abstract fun bindPlaybackHistoryRepository(impl: PlaybackHistoryRepositoryImpl): PlaybackHistoryRepository

    @Binds
    @Singleton
    abstract fun bindStorageRepository(impl: StorageRepositoryImpl): StorageRepository

    @Binds
    @Singleton
    abstract fun bindImportRepository(impl: ImportRepositoryImpl): ImportRepository

    @Binds
    @Singleton
    abstract fun bindImportWorkScheduler(impl: WorkManagerImportScheduler): ImportWorkScheduler

    @Binds
    @Singleton
    abstract fun bindWebUploadController(impl: WebUploadServiceController): WebUploadController
}
