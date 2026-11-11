package com.privateplayer.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Update
import androidx.sqlite.db.SupportSQLiteQuery
import com.privateplayer.core.database.entity.ImportTaskEntity
import com.privateplayer.core.database.entity.MediaEntity
import kotlinx.coroutines.flow.Flow

/**
 * 媒体表访问。
 *
 * 列表页通过 [observeQuery] 接收动态 SQL（搜索 / 排序 / 筛选组合），
 * 其余场景使用语义明确的固定查询，避免把业务规则散落在 SQL 字符串里。
 */
@Dao
interface MediaDao {

    @Query("SELECT * FROM media ORDER BY addedAt DESC LIMIT :limit")
    fun observeRecentlyAdded(limit: Int): Flow<List<MediaEntity>>

    /**
     * 继续观看：有播放进度但尚未看完（留 2% 余量，避免片尾字幕导致永久残留）。
     */
    @Query(
        """
        SELECT * FROM media
        WHERE playbackPositionMs > 0
          AND durationMs > 0
          AND playbackPositionMs < durationMs * 0.98
        ORDER BY lastPlayedAt DESC
        LIMIT :limit
        """,
    )
    fun observeContinueWatching(limit: Int): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media WHERE lastPlayedAt IS NOT NULL ORDER BY lastPlayedAt DESC LIMIT :limit")
    fun observeRecentlyPlayed(limit: Int): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media WHERE isFavorite = 1 ORDER BY addedAt DESC LIMIT :limit")
    fun observeFavorites(limit: Int): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media WHERE id = :id")
    fun observeById(id: Long): Flow<MediaEntity?>

    @Query("SELECT * FROM media WHERE id = :id")
    suspend fun findById(id: Long): MediaEntity?

    @Query("SELECT * FROM media WHERE relativePath = :relativePath LIMIT 1")
    suspend fun findByRelativePath(relativePath: String): MediaEntity?

    /** 用于触发文件树刷新；Room 在媒体表变更后重新执行查询。 */
    @Query("SELECT COUNT(*) FROM media")
    fun observeTreeChanges(): Flow<Int>

    @Query(
        """
        SELECT * FROM media
        WHERE substr(relativePath, 1, length(:directory) + 1) = :directory || '/'
          AND instr(substr(relativePath, length(:directory) + 2), '/') = 0
        """,
    )
    suspend fun findDirectChildren(directory: String): List<MediaEntity>

    @Query(
        """
        SELECT * FROM media
        WHERE relativePath = :path
           OR substr(relativePath, 1, length(:path) + 1) = :path || '/'
        """,
    )
    suspend fun findInTree(path: String): List<MediaEntity>

    /** 防止移动尚未完成入库但已重命名到文件树的导入目标。 */
    @Query(
        """
        SELECT * FROM import_tasks
        WHERE state != 'COMPLETED'
          AND (targetFileName = :path OR substr(targetFileName, 1, length(:path) + 1) = :path || '/')
        """,
    )
    suspend fun findUnfinishedImportsInTree(path: String): List<ImportTaskEntity>

    @RawQuery(observedEntities = [MediaEntity::class])
    fun observeQuery(query: SupportSQLiteQuery): Flow<List<MediaEntity>>

    @RawQuery(observedEntities = [MediaEntity::class])
    fun observeCount(query: SupportSQLiteQuery): Flow<Int>

    @Query("SELECT * FROM media WHERE fingerprint = :fingerprint LIMIT 1")
    suspend fun findByFingerprint(fingerprint: String): MediaEntity?

    @Query("SELECT relativePath FROM media")
    suspend fun allRelativePaths(): List<String>

    @Query("SELECT COUNT(*) FROM media")
    fun observeTotalCount(): Flow<Int>

    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM media")
    fun observeTotalBytes(): Flow<Long>

    @Insert
    suspend fun insert(media: MediaEntity): Long

    @Update
    suspend fun update(media: MediaEntity)

    @Query("UPDATE media SET isFavorite = :favorite, updatedAt = :now WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean, now: Long)

    /** 记住单个视频的字幕选择；`off` 表示关闭字幕，null 表示没有偏好。 */
    @Query("UPDATE media SET selectedSubtitleTrackId = :trackId, updatedAt = :now WHERE id = :id")
    suspend fun setSelectedSubtitleTrack(id: Long, trackId: String?, now: Long)

    @Query(
        """
        UPDATE media
        SET displayName = :displayName, fileName = :fileName, relativePath = :relativePath, updatedAt = :now
        WHERE id = :id
        """,
    )
    suspend fun applyRename(
        id: Long,
        displayName: String,
        fileName: String,
        relativePath: String,
        now: Long,
    )

    @Query(
        """
        UPDATE media
        SET folderId = :folderId, relativePath = :relativePath, updatedAt = :now
        WHERE id = :id
        """,
    )
    suspend fun applyMove(id: Long, folderId: Long?, relativePath: String, now: Long)

    @Query(
        """
        UPDATE media
        SET playbackPositionMs = :positionMs,
            playedDurationMs = playedDurationMs + :watchedMs,
            lastPlayedAt = :now,
            playCount = playCount + :playIncrement,
            updatedAt = :now
        WHERE id = :id
        """,
    )
    suspend fun recordProgress(
        id: Long,
        positionMs: Long,
        watchedMs: Long,
        playIncrement: Int,
        now: Long,
    )

    @Query("UPDATE media SET thumbnailPath = :thumbnailPath, updatedAt = :now WHERE id = :id")
    suspend fun setThumbnail(id: Long, thumbnailPath: String?, now: Long)

    @Query("DELETE FROM media WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM media WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)
}
