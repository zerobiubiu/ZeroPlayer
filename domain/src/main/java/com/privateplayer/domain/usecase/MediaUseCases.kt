package com.privateplayer.domain.usecase

import com.privateplayer.domain.model.LibraryQuery
import com.privateplayer.domain.model.Media
import com.privateplayer.domain.model.OperationOutcome
import com.privateplayer.domain.model.PlaybackRecord
import com.privateplayer.domain.repository.MediaRepository
import com.privateplayer.domain.repository.PlaybackHistoryRepository
import com.privateplayer.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/** 收藏切换（单个与批量）。 */
class ToggleFavoriteUseCase @Inject constructor(
    private val mediaRepository: MediaRepository,
) {
    suspend operator fun invoke(mediaId: Long, favorite: Boolean): OperationOutcome =
        mediaRepository.setFavorite(mediaId, favorite)

    suspend operator fun invoke(mediaIds: List<Long>, favorite: Boolean): OperationOutcome =
        mediaRepository.setFavorite(mediaIds, favorite)
}

class DeleteMediaUseCase @Inject constructor(
    private val mediaRepository: MediaRepository,
) {
    suspend operator fun invoke(mediaIds: List<Long>): OperationOutcome =
        mediaRepository.delete(mediaIds)
}

class MoveMediaUseCase @Inject constructor(
    private val mediaRepository: MediaRepository,
) {
    suspend operator fun invoke(mediaIds: List<Long>, folderId: Long?): OperationOutcome =
        mediaRepository.moveToFolder(mediaIds, folderId)
}

class RenameMediaUseCase @Inject constructor(
    private val mediaRepository: MediaRepository,
) {
    suspend operator fun invoke(mediaId: Long, newName: String): OperationOutcome {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return OperationOutcome.Failure("名称不能为空")
        if (trimmed.length > 120) return OperationOutcome.Failure("名称过长（最多 120 个字符）")
        return mediaRepository.rename(mediaId, trimmed)
    }
}

/** 媒体库查询。把“输入法拼写中的空白搜索”等 UI 噪音收敛在用例层。 */
class ObserveLibraryUseCase @Inject constructor(
    private val mediaRepository: MediaRepository,
) {
    operator fun invoke(query: LibraryQuery): Flow<List<Media>> =
        mediaRepository.observeLibrary(query)
}

/**
 * 记录播放进度。
 *
 * 规则：
 * - 关闭“记忆播放进度”时只写播放历史，不写断点；
 * - 位置必须落在 [0, duration] 区间，避免异常跳转让“继续观看”永久残留；
 * - 播放时长按实际增量累加，且不超过本次播放的墙钟时间估算上限。
 */
class SavePlaybackProgressUseCase @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val playbackHistoryRepository: PlaybackHistoryRepository,
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke(
        mediaId: Long,
        positionMs: Long,
        durationMs: Long,
        watchedMs: Long,
        playIncrement: Int,
    ): OperationOutcome {
        val settings = settingsRepository.observeSettings().first()
        val media = mediaRepository.findById(mediaId)
            ?: return OperationOutcome.Failure("媒体不存在或已删除")

        val boundedDuration = if (durationMs > 0) durationMs else media.durationMs
        val boundedPosition = positionMs.coerceIn(0L, boundedDuration.coerceAtLeast(0L))
        val boundedWatched = watchedMs.coerceIn(0L, 24L * 60 * 60 * 1000)
        val savedPosition = if (settings.playback.rememberProgress) boundedPosition else 0L

        mediaRepository.recordPlayback(
            mediaId = mediaId,
            positionMs = savedPosition,
            watchedMs = boundedWatched,
            playIncrement = playIncrement.coerceIn(0, 1),
        )
        // 播放历史只在一次播放开始时记一条；周期性的进度保存不应制造大量重复记录。
        if (playIncrement > 0) {
            playbackHistoryRepository.record(
                PlaybackRecord(
                    id = 0L,
                    mediaId = mediaId,
                    playedAt = System.currentTimeMillis(),
                    positionMs = savedPosition,
                    durationMs = boundedDuration,
                ),
            )
        }
        return OperationOutcome.Success
    }
}
