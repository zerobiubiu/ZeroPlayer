package com.privateplayer.domain.repository

import com.privateplayer.domain.model.Folder
import com.privateplayer.domain.model.ImportEnqueueResult
import com.privateplayer.domain.model.ImportTask
import com.privateplayer.domain.model.LibraryQuery
import com.privateplayer.domain.model.LibraryStats
import com.privateplayer.domain.model.Media
import com.privateplayer.domain.model.OperationOutcome
import com.privateplayer.domain.model.PlaybackRecord
import com.privateplayer.domain.model.Playlist
import kotlinx.coroutines.flow.Flow

/** 媒体读取与变更。所有变更同时保证磁盘文件与数据库记录一致。 */
interface MediaRepository {

    fun observeRecentlyAdded(limit: Int): Flow<List<Media>>

    fun observeContinueWatching(limit: Int): Flow<List<Media>>

    fun observeFavorites(limit: Int): Flow<List<Media>>

    fun observeRecentlyPlayed(limit: Int): Flow<List<Media>>

    fun observeLibrary(query: LibraryQuery): Flow<List<Media>>

    fun observeById(id: Long): Flow<Media?>

    fun observeStats(): Flow<LibraryStats>

    suspend fun findById(id: Long): Media?

    suspend fun setFavorite(id: Long, favorite: Boolean): OperationOutcome

    suspend fun setFavorite(ids: List<Long>, favorite: Boolean): OperationOutcome

    suspend fun rename(id: Long, newDisplayName: String): OperationOutcome

    suspend fun moveToFolder(ids: List<Long>, folderId: Long?): OperationOutcome

    /** 删除媒体：先删数据库记录再删文件；文件删除失败时返回失败原因。 */
    suspend fun delete(ids: List<Long>): OperationOutcome

    suspend fun recordPlayback(
        mediaId: Long,
        positionMs: Long,
        watchedMs: Long,
        playIncrement: Int,
    )

    /** 记住该视频的字幕选择；`trackId` 为 null 表示关闭字幕，"off" 由播放层写入。 */
    suspend fun setSelectedSubtitleTrack(mediaId: Long, trackId: String?)
}

interface FolderRepository {
    fun observeFolders(): Flow<List<Folder>>

    fun observeUnfiled(): Flow<com.privateplayer.domain.model.UnfiledBucket>

    fun observeFolder(id: Long): Flow<Folder?>

    suspend fun createFolder(name: String): Result<Long>

    suspend fun renameFolder(id: Long, newName: String): OperationOutcome

    suspend fun deleteFolder(id: Long): OperationOutcome
}

interface PlaylistRepository {
    fun observePlaylists(): Flow<List<Playlist>>

    fun observePlaylistMedia(playlistId: Long): Flow<List<Media>>

    suspend fun createPlaylist(name: String): Result<Long>

    suspend fun renamePlaylist(id: Long, newName: String): OperationOutcome

    suspend fun deletePlaylist(id: Long): OperationOutcome

    suspend fun addToPlaylist(playlistId: Long, mediaIds: List<Long>): OperationOutcome

    suspend fun removeFromPlaylist(playlistId: Long, mediaId: Long): OperationOutcome
}

interface PlaybackHistoryRepository {
    fun observeRecent(limit: Int): Flow<List<PlaybackRecord>>

    suspend fun record(record: PlaybackRecord)

    suspend fun clear()
}

interface SettingsRepository {
    fun observeSettings(): Flow<com.privateplayer.domain.model.AppSettings>

    suspend fun setThemeMode(mode: com.privateplayer.domain.model.ThemeMode)

    suspend fun setGridDensity(density: com.privateplayer.domain.model.GridDensity)

    suspend fun setAutoPlayNext(enabled: Boolean)

    suspend fun setRememberProgress(enabled: Boolean)

    suspend fun setAutoResume(enabled: Boolean)

    suspend fun setDefaultSpeed(speed: Float)

    suspend fun setDefaultAspect(aspect: com.privateplayer.domain.model.VideoAspect)

    suspend fun setSeekStepSeconds(seconds: Int)

    suspend fun setLongPressSpeed(speed: Float)

    suspend fun setSubtitleFontScale(scale: Float)

    suspend fun setSubtitleBackground(background: com.privateplayer.domain.model.SubtitleBackground)

    suspend fun setSubtitleTextColor(color: com.privateplayer.domain.model.SubtitleTextColor)

    suspend fun setSubtitleDelayStep(stepMs: Int)

    suspend fun setBrightnessGesture(enabled: Boolean)

    suspend fun setVolumeGesture(enabled: Boolean)

    suspend fun setSeekGesture(enabled: Boolean)

    suspend fun setLongPressSpeedGesture(enabled: Boolean)

    suspend fun setHapticsEnabled(enabled: Boolean)

    suspend fun setGenerateThumbnails(enabled: Boolean)

    suspend fun setDuplicateDetection(enabled: Boolean)
}

/** 导入队列。任务持久化后交给 WorkManager 执行，进程被杀后可恢复。 */
interface ImportRepository {
    fun observeTasks(): Flow<List<ImportTask>>

    fun observeActiveCount(): Flow<Int>

    suspend fun enqueue(
        uris: List<String>,
        folderId: Long? = null,
        targetDirectory: String = "",
    ): ImportEnqueueResult

    /** 系统打开或分享的来源；MIME 仅用于来源未提供类型时的识别。 */
    suspend fun enqueueExternal(
        uris: List<String>,
        mimeType: String?,
        openForPlayback: Boolean,
        requestTaskId: String? = null,
    ): ImportEnqueueResult

    suspend fun cancel(taskId: String)

    suspend fun retry(taskId: String)

    suspend fun removeTask(taskId: String)

    /** 应用启动时对账：补挂丢失的 WorkManager 任务。 */
    suspend fun reconcilePendingTasks()
}

/** 存储占用统计与清理。 */
interface StorageRepository {
    fun observeUsage(): Flow<com.privateplayer.domain.model.StorageUsage>

    suspend fun clearThumbnailCache(): OperationOutcome

    suspend fun clearTemporaryFiles(): OperationOutcome
}

/**
 * 私有存储定位器。UI 通过该接口拿到可展示的真实路径 / 可交给 coil 的缩略图文件，
 * 从而不直接依赖文件系统实现。
 */
interface MediaFileLocator {
    fun mediaFile(relativePath: String): com.privateplayer.domain.model.MediaFileInfo

    fun thumbnailFile(thumbnailPath: String?): java.io.File?

    fun subtitlesFor(mediaId: Long): List<com.privateplayer.domain.model.SubtitleFile>
}

/**
 * 外挂字幕仓库：字幕文件保存在 `files/subtitles/<mediaId>/`，
 * 原字幕文件（外部）不被移动或修改。
 */
interface SubtitleStore {
    fun list(mediaId: Long): List<com.privateplayer.domain.model.SubtitleFile>

    suspend fun import(mediaId: Long, uri: String): OperationOutcome

    suspend fun delete(absolutePath: String): OperationOutcome
}
