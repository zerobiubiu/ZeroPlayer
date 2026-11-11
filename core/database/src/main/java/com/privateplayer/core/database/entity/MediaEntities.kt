package com.privateplayer.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 媒体条目。
 *
 * 约定：
 * - [relativePath] 是相对于 `filesDir` 的路径（如 `media/2024/电影.mp4`），数据库不存绝对路径，
 *   便于应用数据目录发生变化（备份恢复、迁移）时仍可解析。
 * - [fileName] 是磁盘上的真实文件名，重命名时必须与磁盘保持一致；
 *   [displayName] 是展示名，可自由修改。
 * - [fingerprint] 用于导入时的重复检测：大小 + 首段内容哈希。
 */
@Entity(
    tableName = "media",
    foreignKeys = [
        ForeignKey(
            entity = FolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["relativePath"], unique = true),
        Index(value = ["folderId"]),
        Index(value = ["isFavorite"]),
        Index(value = ["addedAt"]),
        Index(value = ["lastPlayedAt"]),
        Index(value = ["fingerprint"]),
    ],
)
data class MediaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val displayName: String,
    val fileName: String,
    val relativePath: String,
    val folderId: Long?,
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
    val fingerprint: String,
    val thumbnailPath: String?,
    val isFavorite: Boolean,
    val addedAt: Long,
    val updatedAt: Long,
    val lastPlayedAt: Long?,
    val playbackPositionMs: Long,
    val playedDurationMs: Long,
    val playCount: Int,
) {
    /** 观看进度 0f..1f，用于首页“继续观看”和详情页进度条。 */
    @get:androidx.room.Ignore
    val progress: Float
        get() = if (durationMs <= 0L) 0f else (playbackPositionMs.toFloat() / durationMs).coerceIn(0f, 1f)
}

/**
 * 旧版逻辑文件夹，仅为兼容已有数据而保留。
 * 当前文件和目录以 `files/media` 下的真实文件树为准。
 */
@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val createdAt: Long,
    @ColumnInfo(defaultValue = "0") val sortOrder: Int = 0,
)

/** 一次播放行为的快照，用于“最近播放”与统计。 */
@Entity(
    tableName = "playback_history",
    indices = [Index(value = ["mediaId"]), Index(value = ["playedAt"])],
)
data class PlaybackHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val mediaId: Long,
    val playedAt: Long,
    val positionMs: Long,
    val durationMs: Long,
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val description: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "playlist_items",
    primaryKeys = ["playlistId", "mediaId"],
    indices = [Index(value = ["playlistId"]), Index(value = ["mediaId"])],
)
data class PlaylistItemEntity(
    val playlistId: Long,
    val mediaId: Long,
    val position: Int,
    val addedAt: Long,
)
