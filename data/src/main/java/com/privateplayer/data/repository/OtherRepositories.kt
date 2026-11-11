package com.privateplayer.data.repository

import com.privateplayer.core.common.di.IoDispatcher
import com.privateplayer.core.common.files.sanitizeFileName
import com.privateplayer.core.database.dao.ImportTaskDao
import com.privateplayer.core.database.dao.FolderDao
import com.privateplayer.core.database.dao.PlaybackHistoryDao
import com.privateplayer.core.database.dao.PlaylistDao
import com.privateplayer.core.database.entity.FolderEntity
import com.privateplayer.core.database.entity.PlaybackHistoryEntity
import com.privateplayer.core.database.entity.PlaylistEntity
import com.privateplayer.core.database.entity.PlaylistItemEntity
import com.privateplayer.core.storage.PrivateStorage
import com.privateplayer.data.mapper.toDomain
import com.privateplayer.domain.model.Folder
import com.privateplayer.domain.model.ImportTaskState
import com.privateplayer.domain.model.Media
import com.privateplayer.domain.model.OperationOutcome
import com.privateplayer.domain.model.PlaybackRecord
import com.privateplayer.domain.model.Playlist
import com.privateplayer.domain.model.StorageUsage
import com.privateplayer.domain.repository.FileTreeRepository
import com.privateplayer.domain.repository.FolderRepository
import com.privateplayer.domain.repository.PlaybackHistoryRepository
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import com.privateplayer.domain.repository.PlaylistRepository
import com.privateplayer.domain.repository.StorageRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

@Singleton
class FolderRepositoryImpl @Inject constructor(
    private val folderDao: FolderDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : FolderRepository {

    override fun observeFolders(): Flow<List<Folder>> =
        folderDao.observeFolders().map { list -> list.map { it.toDomain() } }

    override fun observeUnfiled(): Flow<com.privateplayer.domain.model.UnfiledBucket> =
        folderDao.observeUnfiled().map { summary ->
            com.privateplayer.domain.model.UnfiledBucket(
                mediaCount = summary.mediaCount,
                coverThumbnailPath = summary.coverThumbnailPath,
            )
        }

    override fun observeFolder(id: Long): Flow<Folder?> = folderDao.observeById(id).map { entity ->
        entity?.let {
            Folder(
                id = it.id,
                name = it.name,
                createdAt = it.createdAt,
                mediaCount = 0,
                coverThumbnailPath = null,
            )
        }
    }

    override suspend fun createFolder(name: String): Result<Long> = withContext(ioDispatcher) {
        val trimmed = sanitizeFileName(name.trim())
        if (trimmed.isEmpty()) return@withContext Result.failure(IllegalArgumentException("文件夹名称不能为空"))
        if (folderDao.countByName(trimmed) > 0) {
            return@withContext Result.failure(IllegalArgumentException("已存在同名文件夹"))
        }
        val id = folderDao.insert(
            FolderEntity(
                name = trimmed,
                createdAt = System.currentTimeMillis(),
            ),
        )
        Result.success(id)
    }

    override suspend fun renameFolder(id: Long, newName: String): OperationOutcome =
        withContext(ioDispatcher) {
            val trimmed = sanitizeFileName(newName.trim())
            if (trimmed.isEmpty()) return@withContext OperationOutcome.Failure("文件夹名称不能为空")
            if (folderDao.countByName(trimmed, excludeId = id) > 0) {
                return@withContext OperationOutcome.Failure("已存在同名文件夹")
            }
            folderDao.rename(id, trimmed)
            OperationOutcome.Success
        }

    override suspend fun deleteFolder(id: Long): OperationOutcome = withContext(ioDispatcher) {
        // 只解除归档关系，绝不删除视频文件。
        folderDao.detachMedia(id)
        folderDao.deleteById(id)
        OperationOutcome.Success
    }
}

@Singleton
class PlaylistRepositoryImpl @Inject constructor(
    private val playlistDao: PlaylistDao,
    private val folderDao: FolderDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : PlaylistRepository {

    override fun observePlaylists(): Flow<List<Playlist>> =
        playlistDao.observePlaylists().map { list -> list.map { it.toDomain() } }

    override fun observePlaylistMedia(playlistId: Long): Flow<List<Media>> =
        combine(playlistDao.observeMedia(playlistId), folderDao.observeNames()) { entities, folders ->
            val names = folders.associate { it.id to it.name }
            entities.map { entity -> toMedia(entity, names[entity.folderId]) }
        }

    override suspend fun createPlaylist(name: String): Result<Long> = withContext(ioDispatcher) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return@withContext Result.failure(IllegalArgumentException("播放列表名称不能为空"))
        val now = System.currentTimeMillis()
        val id = playlistDao.insertPlaylist(
            PlaylistEntity(
                name = trimmed,
                description = null,
                createdAt = now,
                updatedAt = now,
            ),
        )
        Result.success(id)
    }

    override suspend fun renamePlaylist(id: Long, newName: String): OperationOutcome =
        withContext(ioDispatcher) {
            val trimmed = newName.trim()
            if (trimmed.isEmpty()) return@withContext OperationOutcome.Failure("播放列表名称不能为空")
            playlistDao.rename(id, trimmed, System.currentTimeMillis())
            OperationOutcome.Success
        }

    override suspend fun deletePlaylist(id: Long): OperationOutcome = withContext(ioDispatcher) {
        playlistDao.deleteItems(id)
        playlistDao.deleteById(id)
        OperationOutcome.Success
    }

    override suspend fun addToPlaylist(playlistId: Long, mediaIds: List<Long>): OperationOutcome =
        withContext(ioDispatcher) {
            var position = playlistDao.maxPosition(playlistId) + 1
            val now = System.currentTimeMillis()
            mediaIds.forEach { mediaId ->
                playlistDao.insertItem(
                    PlaylistItemEntity(
                        playlistId = playlistId,
                        mediaId = mediaId,
                        position = position,
                        addedAt = now,
                    ),
                )
                position++
            }
            OperationOutcome.Success
        }

    override suspend fun removeFromPlaylist(playlistId: Long, mediaId: Long): OperationOutcome =
        withContext(ioDispatcher) {
            playlistDao.removeItem(playlistId, mediaId)
            OperationOutcome.Success
        }

    private fun toMedia(
        entity: com.privateplayer.core.database.entity.MediaEntity,
        folderName: String?,
    ): Media = entity.toDomain(folderName)
}

@Singleton
class PlaybackHistoryRepositoryImpl @Inject constructor(
    private val playbackHistoryDao: PlaybackHistoryDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : PlaybackHistoryRepository {

    override fun observeRecent(limit: Int): Flow<List<PlaybackRecord>> =
        playbackHistoryDao.observeRecent(limit).map { list -> list.map { it.toDomain() } }

    override suspend fun record(record: PlaybackRecord) = withContext(ioDispatcher) {
        playbackHistoryDao.insert(
            PlaybackHistoryEntity(
                mediaId = record.mediaId,
                playedAt = record.playedAt,
                positionMs = record.positionMs,
                durationMs = record.durationMs,
            ),
        )
        Unit
    }

    override suspend fun clear() = withContext(ioDispatcher) {
        playbackHistoryDao.clear()
    }
}

@Singleton
class StorageRepositoryImpl @Inject constructor(
    private val mediaDao: com.privateplayer.core.database.dao.MediaDao,
    private val importTaskDao: ImportTaskDao,
    private val fileTreeRepository: FileTreeRepository,
    private val storage: PrivateStorage,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : StorageRepository {
    private val refresh = MutableStateFlow(0L)


    override fun observeUsage(): Flow<StorageUsage> =
        combine(mediaDao.observeTotalCount().distinctUntilChanged(), fileTreeRepository.observeChanges(), refresh) { count, _, _ ->
            // 首次进入设置页时目录可能尚未创建，先补齐布局再统计。
            storage.ensureLayout()
            fileTreeRepository.requireDirectory("")
            val mediaBytes = storage.withMediaTreeLock { storage.directoryBytes(storage.mediaRoot) }
            StorageUsage(
                mediaBytes = mediaBytes,
                thumbnailBytes = storage.directoryBytes(storage.thumbnailsRoot),
                temporaryBytes = storage.directoryBytes(storage.temporaryRoot),
                freeBytes = storage.freeBytes(),
                mediaCount = count,
            )
        }.flowOn(ioDispatcher)

    override suspend fun clearThumbnailCache(): OperationOutcome = withContext(ioDispatcher) {
        val result = try {
            storage.clearDirectory(storage.thumbnailsRoot)
            OperationOutcome.Success
        } catch (_: IOException) {
            OperationOutcome.Failure("部分缩略图缓存无法清理")
        }
        refresh.update { it + 1 }
        result
    }

    override suspend fun clearTemporaryFiles(): OperationOutcome = withContext(ioDispatcher) {
        if (!storage.ensureLayout()) return@withContext OperationOutcome.Failure("无法打开临时目录")
        val root = storage.temporaryRoot.toPath().toAbsolutePath().normalize()
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            return@withContext OperationOutcome.Failure("临时目录不可用")
        }
        val protected = importTaskDao.observeAll().first().asSequence()
            .filter { it.state != ImportTaskState.COMPLETED.name && it.state != ImportTaskState.CANCELED.name }
            .filter { it.tempRelativePath.startsWith("${PrivateStorage.TEMPORARY_DIR}/") }
            .map { storage.absoluteCacheFile(it.tempRelativePath).toPath().toAbsolutePath().normalize() }
            .filter { it.startsWith(root) }
            .flatMap { path -> sequenceOf(path, path.resolveSibling("${path.fileName}.ready")) }
            .toSet()
        var failed = false
        try {
            Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    if (protected.none { it.startsWith(file) }) {
                        try {
                            Files.deleteIfExists(file)
                        } catch (_: IOException) {
                            failed = true
                        }
                    }
                    return FileVisitResult.CONTINUE
                }

                override fun visitFileFailed(file: Path, error: IOException): FileVisitResult {
                    failed = true
                    return FileVisitResult.CONTINUE
                }

                override fun postVisitDirectory(dir: Path, error: IOException?): FileVisitResult {
                    if (error != null) failed = true
                    if (dir != root && protected.none { it.startsWith(dir) }) {
                        try {
                            Files.deleteIfExists(dir)
                        } catch (_: IOException) {
                            failed = true
                        }
                    }
                    return FileVisitResult.CONTINUE
                }
            })
        } catch (_: IOException) {
            failed = true
        }
        refresh.update { it + 1 }
        if (failed) return@withContext OperationOutcome.Failure("部分临时文件无法清理")
        OperationOutcome.Success
    }
}
