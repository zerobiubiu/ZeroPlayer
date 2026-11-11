package com.privateplayer.domain.model

/** 通用操作结果。业务层不抛异常给 UI，而是返回带上限说明的结果对象。 */
sealed interface OperationOutcome {
    data object Success : OperationOutcome

    /** 失败原因直接可展示给用户。 */
    data class Failure(val message: String) : OperationOutcome

    val isSuccess: Boolean get() = this is Success
}

/** 媒体条目（领域模型）。路径一律为相对于 `filesDir` 的相对路径。 */
data class Media(
    val id: Long,
    val displayName: String,
    val fileName: String,
    val relativePath: String,
    val folderId: Long?,
    val folderName: String?,
    val mimeType: String?,
    val sizeBytes: Long,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val videoCodec: String?,
    val audioCodec: String?,
    val bitrate: Long?,
    val audioTrackCount: Int,
    val subtitleTrackCount: Int,
    val thumbnailPath: String?,
    val isFavorite: Boolean,
    val addedAt: Long,
    val lastPlayedAt: Long?,
    val playbackPositionMs: Long,
    val playedDurationMs: Long,
    val playCount: Int,
) {
    /** 观看进度 0f..1f。 */
    val progress: Float
        get() = if (durationMs <= 0L) 0f else (playbackPositionMs.toFloat() / durationMs).coerceIn(0f, 1f)

    val resolutionLabel: String
        get() = when {
            width <= 0 || height <= 0 -> "未知分辨率"
            width >= 3840 || height >= 2160 -> "4K"
            width >= 2560 || height >= 1440 -> "2K"
            width >= 1920 || height >= 1080 -> "1080P"
            width >= 1280 || height >= 720 -> "720P"
            else -> "${width}×${height}"
        }

    val hasProgress: Boolean get() = playbackPositionMs > 0L && durationMs > 0L
}

/** 文件夹（逻辑归档）。 */
data class Folder(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val mediaCount: Int,
    val coverThumbnailPath: String?,
)

/** “未归档”聚合信息。 */
data class UnfiledBucket(
    val mediaCount: Int,
    val coverThumbnailPath: String?,
)

/** 播放列表。 */
data class Playlist(
    val id: Long,
    val name: String,
    val description: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val itemCount: Int,
    val coverThumbnailPath: String?,
)

/** 媒体库统计信息。 */
data class LibraryStats(
    val mediaCount: Int,
    val totalBytes: Long,
)

/** 播放历史条目。 */
data class PlaybackRecord(
    val id: Long,
    val mediaId: Long,
    val playedAt: Long,
    val positionMs: Long,
    val durationMs: Long,
)
