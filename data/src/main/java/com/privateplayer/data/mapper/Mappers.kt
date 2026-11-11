package com.privateplayer.data.mapper

import com.privateplayer.core.database.dao.FolderSummary
import com.privateplayer.core.database.dao.PlaylistSummary
import com.privateplayer.core.database.entity.ImportTaskEntity
import com.privateplayer.core.database.entity.MediaEntity
import com.privateplayer.core.database.entity.PlaybackHistoryEntity
import com.privateplayer.domain.model.Folder
import com.privateplayer.domain.model.ImportTask
import com.privateplayer.domain.model.ImportTaskState
import com.privateplayer.domain.model.Media
import com.privateplayer.domain.model.PlaybackRecord
import com.privateplayer.domain.model.Playlist

/** 数据库实体 → 领域模型。展示层不接触 Room 类型。 */

fun MediaEntity.toDomain(folderName: String?): Media = Media(
    id = id,
    displayName = displayName,
    fileName = fileName,
    relativePath = relativePath,
    folderId = folderId,
    folderName = folderName,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    durationMs = durationMs,
    width = width,
    height = height,
    videoCodec = videoCodec,
    audioCodec = audioCodec,
    bitrate = bitrate,
    audioTrackCount = audioTrackCount,
    subtitleTrackCount = subtitleTrackCount,
    thumbnailPath = thumbnailPath,
    isFavorite = isFavorite,
    addedAt = addedAt,
    lastPlayedAt = lastPlayedAt,
    playbackPositionMs = playbackPositionMs,
    playedDurationMs = playedDurationMs,
    playCount = playCount,
    selectedSubtitleTrackId = selectedSubtitleTrackId,
)

fun FolderSummary.toDomain(): Folder = Folder(
    id = id,
    name = name,
    createdAt = createdAt,
    mediaCount = mediaCount,
    coverThumbnailPath = coverThumbnailPath,
)

fun PlaylistSummary.toDomain(): Playlist = Playlist(
    id = id,
    name = name,
    description = description,
    createdAt = createdAt,
    updatedAt = updatedAt,
    itemCount = itemCount,
    coverThumbnailPath = coverThumbnailPath,
)

fun PlaybackHistoryEntity.toDomain(): PlaybackRecord = PlaybackRecord(
    id = id,
    mediaId = mediaId,
    playedAt = playedAt,
    positionMs = positionMs,
    durationMs = durationMs,
)

fun ImportTaskEntity.toDomain(): ImportTask = ImportTask(
    id = id,
    displayName = displayName,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    state = ImportTaskState.entries.firstOrNull { it.name == state } ?: ImportTaskState.FAILED,
    bytesCopied = bytesCopied,
    errorMessage = errorMessage,
    mediaId = mediaId,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
