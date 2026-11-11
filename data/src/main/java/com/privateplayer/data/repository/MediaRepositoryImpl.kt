package com.privateplayer.data.repository

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.privateplayer.core.common.files.withSameExtension
import com.privateplayer.core.common.di.IoDispatcher
import com.privateplayer.core.database.dao.FolderDao
import com.privateplayer.core.database.dao.MediaDao
import com.privateplayer.core.database.entity.MediaEntity
import com.privateplayer.core.storage.PrivateStorage
import com.privateplayer.data.mapper.toDomain
import com.privateplayer.domain.model.LibraryQuery
import com.privateplayer.domain.model.LibraryStats
import com.privateplayer.domain.model.Media
import com.privateplayer.domain.model.MediaCategory
import com.privateplayer.domain.model.MediaSort
import com.privateplayer.domain.model.OperationOutcome
import com.privateplayer.domain.repository.FileTreeRepository
import com.privateplayer.domain.repository.MediaRepository
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Singleton
class MediaRepositoryImpl @Inject constructor(
    private val mediaDao: MediaDao,
    private val folderDao: FolderDao,
    private val storage: PrivateStorage,
    private val fileTree: FileTreeRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : MediaRepository {

    override fun observeRecentlyAdded(limit: Int): Flow<List<Media>> =
        decorate(mediaDao.observeRecentlyAdded(limit))

    override fun observeContinueWatching(limit: Int): Flow<List<Media>> =
        decorate(mediaDao.observeContinueWatching(limit))

    override fun observeFavorites(limit: Int): Flow<List<Media>> =
        decorate(mediaDao.observeFavorites(limit))

    override fun observeRecentlyPlayed(limit: Int): Flow<List<Media>> =
        decorate(mediaDao.observeRecentlyPlayed(limit))

    override fun observeLibrary(query: LibraryQuery): Flow<List<Media>> =
        decorate(mediaDao.observeQuery(buildQuery(query)))

    override fun observeById(id: Long): Flow<Media?> =
        combine(mediaDao.observeById(id), folderDao.observeNames()) { entity, folders ->
            entity?.toDomain(folders.firstOrNull { it.id == entity.folderId }?.name)
        }

    override fun observeStats(): Flow<LibraryStats> =
        combine(mediaDao.observeTotalCount(), mediaDao.observeTotalBytes()) { count, bytes ->
            LibraryStats(mediaCount = count, totalBytes = bytes)
        }

    override suspend fun findById(id: Long): Media? = withContext(ioDispatcher) {
        val entity = mediaDao.findById(id) ?: return@withContext null
        val folderName = entity.folderId?.let { folderDao.findById(it)?.name }
        entity.toDomain(folderName)
    }

    override suspend fun setFavorite(id: Long, favorite: Boolean): OperationOutcome =
        withContext(ioDispatcher) {
            mediaDao.setFavorite(id, favorite, System.currentTimeMillis())
            OperationOutcome.Success
        }

    override suspend fun setSelectedSubtitleTrack(mediaId: Long, trackId: String?) {
        withContext(ioDispatcher) {
            mediaDao.setSelectedSubtitleTrack(mediaId, trackId, System.currentTimeMillis())
        }
    }

    override suspend fun setFavorite(ids: List<Long>, favorite: Boolean): OperationOutcome =
        withContext(ioDispatcher) {
            val now = System.currentTimeMillis()
            ids.forEach { mediaDao.setFavorite(it, favorite, now) }
            OperationOutcome.Success
        }

    override suspend fun rename(id: Long, newDisplayName: String): OperationOutcome =
        withContext(ioDispatcher) {
            val entity = mediaDao.findById(id)
                ?: return@withContext OperationOutcome.Failure("媒体不存在或已删除")
            val newFileName = withSameExtension(newDisplayName, entity.fileName)
            if (newFileName == entity.fileName && newDisplayName == entity.displayName) {
                return@withContext OperationOutcome.Success
            }

            try {
                require(entity.relativePath.startsWith("${PrivateStorage.MEDIA_DIR}/")) { "媒体路径异常" }
                fileTree.rename(entity.relativePath.removePrefix("${PrivateStorage.MEDIA_DIR}/"), newFileName)
                OperationOutcome.Success
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                OperationOutcome.Failure("重命名失败：${error.message ?: "文件或数据库操作异常"}")
            }
        }

    override suspend fun moveToFolder(ids: List<Long>, folderId: Long?): OperationOutcome =
        withContext(ioDispatcher) {
            if (folderId != null && folderDao.findById(folderId) == null) {
                return@withContext OperationOutcome.Failure("目标文件夹不存在")
            }
            val now = System.currentTimeMillis()
            ids.forEach { id ->
                val entity = mediaDao.findById(id) ?: return@forEach
                // 文件夹是逻辑归档，磁盘路径不变。
                mediaDao.applyMove(id, folderId, entity.relativePath, now)
            }
            OperationOutcome.Success
        }

    override suspend fun delete(ids: List<Long>): OperationOutcome = withContext(ioDispatcher) {
        val failed = mutableListOf<String>()
        ids.forEach { id ->
            val entity = mediaDao.findById(id) ?: return@forEach
            try {
                require(entity.relativePath.startsWith("${PrivateStorage.MEDIA_DIR}/")) { "媒体路径异常" }
                fileTree.delete(entity.relativePath.removePrefix("${PrivateStorage.MEDIA_DIR}/"))
                storage.deleteCacheFile(entity.thumbnailPath)
                storage.deleteSubtitleDir(id)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                failed += entity.displayName
            }
        }
        if (failed.isEmpty()) {
            OperationOutcome.Success
        } else {
            OperationOutcome.Failure("以下文件删除失败：${failed.joinToString("、")}")
        }
    }

    override suspend fun recordPlayback(
        mediaId: Long,
        positionMs: Long,
        watchedMs: Long,
        playIncrement: Int,
    ) = withContext(ioDispatcher) {
        mediaDao.recordProgress(
            id = mediaId,
            positionMs = positionMs,
            watchedMs = watchedMs,
            playIncrement = playIncrement,
            now = System.currentTimeMillis(),
        )
        Unit
    }

    /** 为列表补上文件夹名称（展示用），避免在 SQL 里做无谓的联表。 */
    private fun decorate(source: Flow<List<MediaEntity>>): Flow<List<Media>> =
        combine(source, folderDao.observeNames()) { entities, folders ->
            val names = folders.associate { it.id to it.name }
            entities.map { it.toDomain(names[it.folderId]) }
        }

    private fun buildQuery(query: LibraryQuery): SupportSQLiteQuery {
        val where = mutableListOf<String>()
        val args = mutableListOf<Any>()

        if (query.search.isNotBlank()) {
            where += "(displayName LIKE ? OR fileName LIKE ?)"
            val pattern = "%${query.search.trim()}%"
            args += pattern
            args += pattern
        }
        val folderId = query.folderId
        if (folderId != null) {
            where += "folderId = ?"
            args += folderId
        } else if (query.unfiledOnly) {
            where += "folderId IS NULL"
        }
        if (query.favoritesOnly) {
            where += "isFavorite = 1"
        }
        when (query.category) {
            MediaCategory.SHORT -> where += "durationMs > 0 AND durationMs <= 600000"
            MediaCategory.MEDIUM -> where += "durationMs > 600000 AND durationMs <= 2400000"
            MediaCategory.LONG -> where += "durationMs > 2400000"
            MediaCategory.ULTRA_HD -> where += "width >= 2560"
            MediaCategory.UNWATCHED -> where += "playCount = 0"
            MediaCategory.FINISHED -> where += "durationMs > 0 AND playbackPositionMs >= durationMs * 0.98"
            null -> Unit
        }

        val orderBy = when (query.sort) {
            MediaSort.ADDED_DESC -> "addedAt DESC"
            MediaSort.ADDED_ASC -> "addedAt ASC"
            MediaSort.NAME_ASC -> "displayName COLLATE NOCASE ASC"
            MediaSort.NAME_DESC -> "displayName COLLATE NOCASE DESC"
            MediaSort.DURATION_DESC -> "durationMs DESC"
            MediaSort.SIZE_DESC -> "sizeBytes DESC"
            MediaSort.LAST_PLAYED_DESC -> "lastPlayedAt DESC"
        }

        val sql = buildString {
            append("SELECT * FROM media")
            if (where.isNotEmpty()) {
                append(" WHERE ").append(where.joinToString(" AND "))
            }
            append(" ORDER BY ").append(orderBy)
        }
        return SimpleSQLiteQuery(sql, args.toTypedArray())
    }
}
