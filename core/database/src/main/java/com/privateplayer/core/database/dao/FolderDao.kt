package com.privateplayer.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.privateplayer.core.database.entity.FolderEntity
import kotlinx.coroutines.flow.Flow

/** 文件夹列表项：文件夹本身 + 内含媒体数量 + 一张封面缩略图。 */
data class FolderSummary(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val mediaCount: Int,
    val coverThumbnailPath: String?,
)

/**
 * 未归档媒体的统计行。`folderId IS NULL` 的媒体不属于任何文件夹，
 * 在“文件夹”页里以虚拟条目展示，因此单独查询。
 */
data class UnfiledSummary(
    val mediaCount: Int,
    val coverThumbnailPath: String?,
)

data class FolderName(
    val id: Long,
    val name: String,
)

@Dao
interface FolderDao {

    @Query(
        """
        SELECT f.id AS id,
               f.name AS name,
               f.createdAt AS createdAt,
               (SELECT COUNT(*) FROM media m WHERE m.folderId = f.id) AS mediaCount,
               (SELECT m.thumbnailPath FROM media m WHERE m.folderId = f.id AND m.thumbnailPath IS NOT NULL
                ORDER BY m.addedAt DESC LIMIT 1) AS coverThumbnailPath
        FROM folders f
        ORDER BY f.sortOrder ASC, f.name COLLATE NOCASE ASC
        """,
    )
    fun observeFolders(): Flow<List<FolderSummary>>

    @Query(
        """
        SELECT (SELECT COUNT(*) FROM media m WHERE m.folderId IS NULL) AS mediaCount,
               (SELECT m.thumbnailPath FROM media m WHERE m.folderId IS NULL AND m.thumbnailPath IS NOT NULL
                ORDER BY m.addedAt DESC LIMIT 1) AS coverThumbnailPath
        """,
    )
    fun observeUnfiled(): Flow<UnfiledSummary>

    @Query("SELECT * FROM folders WHERE id = :id")
    fun observeById(id: Long): Flow<FolderEntity?>

    @Query("SELECT id, name FROM folders")
    fun observeNames(): Flow<List<FolderName>>

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun findById(id: Long): FolderEntity?

    @Query("SELECT * FROM folders ORDER BY sortOrder ASC, name COLLATE NOCASE ASC")
    suspend fun findAll(): List<FolderEntity>

    /** 名称去重（忽略大小写）。[excludeId] 用于重命名时排除自身。 */
    @Query("SELECT COUNT(*) FROM folders WHERE name = :name COLLATE NOCASE AND id != :excludeId")
    suspend fun countByName(name: String, excludeId: Long = -1L): Int

    @Insert
    suspend fun insert(folder: FolderEntity): Long

    @Update
    suspend fun update(folder: FolderEntity)

    @Query("UPDATE folders SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** 删除文件夹时把其中的媒体归入“未归档”，不删除任何文件。 */
    @Query("UPDATE media SET folderId = NULL WHERE folderId = :folderId")
    suspend fun detachMedia(folderId: Long)
}
