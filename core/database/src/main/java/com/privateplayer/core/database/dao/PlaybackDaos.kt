package com.privateplayer.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.privateplayer.core.database.entity.PlaybackHistoryEntity
import com.privateplayer.core.database.entity.PlaylistEntity
import com.privateplayer.core.database.entity.PlaylistItemEntity
import kotlinx.coroutines.flow.Flow

/** 播放列表列表项：列表信息 + 条目数量 + 封面。 */
data class PlaylistSummary(
    val id: Long,
    val name: String,
    val description: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val itemCount: Int,
    val coverThumbnailPath: String?,
)

@Dao
interface PlaybackHistoryDao {

    @Query("SELECT * FROM playback_history ORDER BY playedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<PlaybackHistoryEntity>>

    @Query("SELECT * FROM playback_history WHERE mediaId = :mediaId ORDER BY playedAt DESC LIMIT :limit")
    suspend fun findForMedia(mediaId: Long, limit: Int = 1): List<PlaybackHistoryEntity>

    @Insert
    suspend fun insert(history: PlaybackHistoryEntity)

    @Query("DELETE FROM playback_history")
    suspend fun clear()

    @Query("DELETE FROM playback_history WHERE mediaId = :mediaId")
    suspend fun deleteForMedia(mediaId: Long)
}

@Dao
interface PlaylistDao {

    @Query(
        """
        SELECT p.id AS id,
               p.name AS name,
               p.description AS description,
               p.createdAt AS createdAt,
               p.updatedAt AS updatedAt,
               (SELECT COUNT(*) FROM playlist_items i WHERE i.playlistId = p.id) AS itemCount,
               (SELECT m.thumbnailPath FROM playlist_items i
                  JOIN media m ON m.id = i.mediaId
                 WHERE i.playlistId = p.id AND m.thumbnailPath IS NOT NULL
                 ORDER BY i.position ASC LIMIT 1) AS coverThumbnailPath
        FROM playlists p
        ORDER BY p.updatedAt DESC
        """,
    )
    fun observePlaylists(): Flow<List<PlaylistSummary>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    fun observeById(id: Long): Flow<PlaylistEntity?>

    @Query("SELECT COUNT(*) FROM playlists")
    fun observeCount(): Flow<Int>

    @Insert
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :name, updatedAt = :now WHERE id = :id")
    suspend fun rename(id: Long, name: String, now: Long)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId")
    suspend fun deleteItems(playlistId: Long)

    @Query("SELECT * FROM playlist_items WHERE playlistId = :playlistId ORDER BY position ASC")
    fun observeItems(playlistId: Long): Flow<List<PlaylistItemEntity>>

    @Query(
        """
        SELECT m.* FROM playlist_items i
        JOIN media m ON m.id = i.mediaId
        WHERE i.playlistId = :playlistId
        ORDER BY i.position ASC
        """,
    )
    fun observeMedia(playlistId: Long): Flow<List<com.privateplayer.core.database.entity.MediaEntity>>

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_items WHERE playlistId = :playlistId")
    suspend fun maxPosition(playlistId: Long): Int

    @Insert(onConflict = androidx.room.OnConflictStrategy.IGNORE)
    suspend fun insertItem(item: PlaylistItemEntity)

    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId AND mediaId = :mediaId")
    suspend fun removeItem(playlistId: Long, mediaId: Long)

    @Query("DELETE FROM playlist_items WHERE mediaId = :mediaId")
    suspend fun removeMediaFromAll(mediaId: Long)

    @Query("DELETE FROM playlist_items WHERE mediaId IN (:mediaIds)")
    suspend fun removeMediaFromAll(mediaIds: List<Long>)
}
