package com.privateplayer.media.playback

import com.privateplayer.domain.usecase.PlaybackStopper
import com.privateplayer.feature.player.PlaybackCoordinator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 把领域层的播放停止能力接到唯一播放协调器（F-21）。
 *
 * 文件操作（首页/详情页的重命名、移动、删除）经 [PlaybackStopper] 在操作前
 * 结束引用目标文件的播放会话；播放实现细节不进入 domain 与 feature 页面。
 */
class CoordinatorPlaybackStopper @Inject constructor(
    private val coordinator: PlaybackCoordinator,
) : PlaybackStopper {
    override suspend fun stopIfAffected(path: String) = coordinator.stopForFileOperation(path)
}

@Module
@InstallIn(SingletonComponent::class)
object PlaybackStopperModule {
    @Provides
    @Singleton
    fun providePlaybackStopper(stopper: CoordinatorPlaybackStopper): PlaybackStopper = stopper
}
