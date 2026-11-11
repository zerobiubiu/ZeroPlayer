package com.privateplayer.core.storage.di

import com.privateplayer.core.storage.LocalSubtitleStore
import com.privateplayer.core.storage.StorageMediaFileLocator
import com.privateplayer.domain.repository.MediaFileLocator
import com.privateplayer.domain.repository.SubtitleStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class StorageModule {

    @Binds
    @Singleton
    abstract fun bindMediaFileLocator(impl: StorageMediaFileLocator): MediaFileLocator

    @Binds
    @Singleton
    abstract fun bindSubtitleStore(impl: LocalSubtitleStore): SubtitleStore
}
