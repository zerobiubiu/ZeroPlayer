package com.privateplayer.data.repository

import android.webkit.MimeTypeMap
import androidx.room.withTransaction
import com.privateplayer.core.common.di.IoDispatcher
import com.privateplayer.core.common.files.baseNameOf
import com.privateplayer.core.common.files.extensionOf
import com.privateplayer.core.database.PrivatePlayerDatabase
import com.privateplayer.core.database.dao.MediaDao
import com.privateplayer.core.database.dao.PlaybackHistoryDao
import com.privateplayer.core.database.dao.PlaylistDao
import com.privateplayer.core.database.entity.MediaEntity
import com.privateplayer.core.storage.MediaProbe
import com.privateplayer.core.storage.PrivateStorage
import com.privateplayer.core.storage.moveNoOverwrite
import com.privateplayer.data.importer.ImportCommitJournal
import com.privateplayer.data.importer.importTempFile
import com.privateplayer.domain.repository.FileTreeEntry
import com.privateplayer.domain.repository.FileTreeRepository
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.FileVisitResult
import java.nio.file.attribute.BasicFileAttributes
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/** 首页和网页共享的真实私有文件树。 */
@Singleton
class FileTreeRepositoryImpl @Inject constructor(
    private val database: PrivatePlayerDatabase,
    private val mediaDao: MediaDao,
    private val playbackHistoryDao: PlaybackHistoryDao,
    private val playlistDao: PlaylistDao,
    private val storage: PrivateStorage,
    private val probe: MediaProbe,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : FileTreeRepository {
    private val changes = MutableStateFlow(0L)
    private val importCommits = ImportCommitJournal(storage)

    override fun observeChanges(): Flow<Long> = changes

    override fun observeDirectory(path: String): Flow<List<FileTreeEntry>> =
        combine(mediaDao.observeTreeChanges(), changes) { _, _ -> Unit }
            .map { list(path) }

    override suspend fun list(path: String): List<FileTreeEntry> = inTree {
        val directory = existingDirectory(path)
        val mediaByPath = mediaDao.findDirectChildren(databasePath(path)).associateBy { it.relativePath }
        val children = directory.listFiles() ?: throw IOException("无法读取目录：$path")
        children.mapNotNull { child ->
            if (Files.isSymbolicLink(child.toPath())) return@mapNotNull null
            val childPath = join(path, child.name)
            val checked = storage.resolveMediaFile(childPath)
            if (!isFile(checked) && !isDirectory(checked)) return@mapNotNull null
            entry(childPath, checked, mediaByPath[databasePath(childPath)])
        }.sortedWith { left, right ->
            when {
                left.isDirectory != right.isDirectory -> if (left.isDirectory) -1 else 1
                else -> compareNames(left.name, right.name).takeIf { it != 0 } ?: left.path.compareTo(right.path)
            }
        }
    }

    override suspend fun find(path: String): FileTreeEntry? = inTree {
        val file = storage.resolveMediaFile(path)
        if (!isFile(file) && !isDirectory(file)) return@inTree null
        val media = if (isFile(file)) mediaDao.findByRelativePath(databasePath(path)) else null
        entry(path, file, media)
    }

    override suspend fun createDirectory(parent: String, name: String): FileTreeEntry = inTree {
        storage.validateMediaName(name)
        existingDirectory(parent)
        val path = join(parent, name)
        val target = storage.resolveMediaFile(path)
        Files.createDirectory(target.toPath())
        notifyChanged()
        entry(path, target, null)
    }

    override suspend fun rename(path: String, name: String): FileTreeEntry = inTree {
        storage.validateMediaName(name)
        require(path.isNotEmpty()) { "不能重命名根目录" }
        val parent = path.substringBeforeLast('/', "")
        moveEntry(path, join(parent, name), renameFile = true)
    }

    override suspend fun move(path: String, destination: String): FileTreeEntry = inTree {
        require(path.isNotEmpty()) { "不能移动根目录" }
        existingDirectory(destination)
        moveEntry(path, join(destination, path.substringAfterLast('/')), renameFile = false)
    }

    override suspend fun delete(path: String): Unit = inTree {
        require(path.isNotEmpty()) { "不能删除根目录" }
        val source = existingEntry(path)
        ensureNoPendingImport(path)
        var journalDirectory: File? = null
        try {
            database.withTransaction {
                val records = mediaDao.findInTree(databasePath(path))
                val journal = Journal(Operation.DELETE, path, "", records.map { JournalRecord(it.id, it.relativePath) })
                journalDirectory = writeJournal(journal)
                val payload = File(journalDirectory!!, PAYLOAD)
                moveNoOverwrite(source, payload)
                records.forEach { media ->
                    playbackHistoryDao.deleteForMedia(media.id)
                    playlistDao.removeMediaFromAll(media.id)
                    mediaDao.deleteById(media.id)
                }
            }
        } catch (error: Throwable) {
            compensate(journalDirectory, error)
            throw error
        }
        notifyChanged()
        finishCommittedJournal(journalDirectory)
    }

    override suspend fun openForRead(path: String): File = inTree {
        val file = storage.resolveMediaFile(path)
        require(isFile(file)) { "文件不存在或不是普通文件" }
        file
    }

    override suspend fun requireDirectory(path: String): File = inTree { existingDirectory(path) }

    override suspend fun ensureMedia(path: String): Long = inTree {
        val file = storage.resolveMediaFile(path)
        require(isFile(file)) { "文件不存在或不是普通文件" }
        ensureNoPendingImport(path)
        val relativePath = databasePath(path)
        val existing = mediaDao.findByRelativePath(relativePath)
        val declaredMime = mimeFor(file.name)
        require(isLikelyVideo(file.name, existing?.mimeType ?: declaredMime, existing != null)) { "文件不是可识别的视频" }
        if (existing != null) return@inTree existing.id

        // 平台无法解析的格式仍保留记录，让播放引擎尝试打开原文件。
        val metadata = try {
            probe.probe(file, declaredMime)
        } catch (_: Exception) {
            MediaProbe.ProbedMetadata(0L, 0, 0, null, null, null, 0, 0, declaredMime)
        }
        val fingerprint = probe.fingerprint(file)
        val now = System.currentTimeMillis()
        val media = MediaEntity(
            displayName = baseNameOf(file.name),
            fileName = file.name,
            relativePath = relativePath,
            folderId = null,
            mimeType = metadata.mimeType ?: declaredMime,
            sizeBytes = file.length(),
            durationMs = metadata.durationMs,
            width = metadata.width,
            height = metadata.height,
            videoCodec = metadata.videoCodec,
            audioCodec = metadata.audioCodec,
            bitrate = metadata.bitrate,
            audioTrackCount = metadata.audioTrackCount,
            subtitleTrackCount = metadata.subtitleTrackCount,
            fingerprint = fingerprint,
            thumbnailPath = null,
            isFavorite = false,
            addedAt = now,
            updatedAt = now,
            lastPlayedAt = null,
            playbackPositionMs = 0L,
            playedDurationMs = 0L,
            playCount = 0,
        )
        // 与导入任务并发时，以唯一路径索引裁决；不按指纹合并不同的真实文件。
        val id = try {
            mediaDao.insert(media)
        } catch (error: Exception) {
            mediaDao.findByRelativePath(relativePath)?.id ?: throw error
        }
        id
    }

    override suspend fun matchingSubtitles(path: String): List<String> = inTree {
        val video = storage.resolveMediaFile(path)
        require(isFile(video)) { "视频文件不存在或不是普通文件" }
        val displayName = visibleName(video.name, mediaDao.findByRelativePath(databasePath(path)))
        val parentPath = path.substringBeforeLast('/', "")
        val directory = existingDirectory(parentPath)
        val files = directory.listFiles() ?: throw IOException("无法读取目录：$parentPath")
        files.asSequence()
            .filter { !Files.isSymbolicLink(it.toPath()) && isFile(it) }
            .filter {
                isMatchingSubtitle(video.name, it.name) ||
                    (displayName != video.name && isMatchingSubtitle(displayName, it.name))
            }
            .map { storage.resolveMediaFile(join(parentPath, it.name)).absolutePath }
            .sortedWith { left, right -> compareNames(left, right) }
            .toList()
    }

    override fun notifyChanged() {
        changes.update { it + 1 }
    }

    private suspend fun moveEntry(path: String, destination: String, renameFile: Boolean): FileTreeEntry {
        val source = existingEntry(path)
        if (path == destination) return entry(path, source, mediaDao.findByRelativePath(databasePath(path)))
        ensureNoPendingImport(path)
        val target = storage.resolveMediaFile(destination)
        existingDirectory(destination.substringBeforeLast('/', ""))
        if (isDirectory(source)) {
            require(!destination.startsWith("$path/")) { "不能将目录移动到自身内部" }
        }
        if (Files.exists(target.toPath(), LinkOption.NOFOLLOW_LINKS)) throw IOException("目标已存在")

        var journalDirectory: File? = null
        try {
            database.withTransaction {
                val oldPrefix = databasePath(path)
                val newPrefix = databasePath(destination)
                val records = mediaDao.findInTree(oldPrefix)
                val journal = Journal(Operation.MOVE, path, destination, records.map { JournalRecord(it.id, it.relativePath) })
                journalDirectory = writeJournal(journal)
                moveNoOverwrite(source, target)
                val now = System.currentTimeMillis()
                records.forEach { media ->
                    val updatedPath = newPrefix + media.relativePath.removePrefix(oldPrefix)
                    val updatedName = updatedPath.substringAfterLast('/')
                    val displayName = if (renameFile && media.relativePath == oldPrefix && isFile(target)) {
                        baseNameOf(updatedName)
                    } else {
                        media.displayName
                    }
                    mediaDao.applyRename(media.id, displayName, updatedName, updatedPath, now)
                }
            }
        } catch (error: Throwable) {
            compensate(journalDirectory, error)
            throw error
        }
        notifyChanged()
        finishCommittedJournal(journalDirectory)
        return entry(destination, target, mediaDao.findByRelativePath(databasePath(destination)))
    }

    private suspend fun <T> inTree(block: suspend () -> T): T = withContext(ioDispatcher) {
        storage.withMediaTreeLock {
            val root = storage.resolveMediaFile("")
            if (!root.isDirectory && !root.mkdirs()) throw IOException("无法创建媒体目录")
            recoverPendingJournals()
            block()
        }
    }

    private fun existingDirectory(path: String): File {
        val file = storage.resolveMediaFile(path)
        require(isDirectory(file)) { "目录不存在或不是目录" }
        return file
    }

    private fun existingEntry(path: String): File {
        val file = storage.resolveMediaFile(path)
        require(isFile(file) || isDirectory(file)) { "路径不存在或不是普通文件、目录" }
        return file
    }

    /** rename 后暂存路径已消失，必须用持久身份保护尚未完成入库的目标。 */
    private suspend fun ensureNoPendingImport(path: String) {
        mediaDao.findUnfinishedImportsInTree(path).forEach { task ->
            val target = storage.resolveMediaFile(task.targetFileName)
            if (!isFile(target)) return@forEach
            val prepared = importCommits.read(task)
            val owned = if (prepared != null) {
                importCommits.owns(target, prepared)
            } else {
                // 升级前已产生的双路径仍可恢复，但不再创建此类文件。
                val staged = storage.importTempFile(task)
                isFile(staged) && sameFile(target, staged)
            }
            if (owned) throw IOException("文件仍在导入或等待恢复，请先处理导入任务")
        }
    }

    private fun entry(path: String, file: File, media: MediaEntity?): FileTreeEntry {
        val directory = isDirectory(file)
        return FileTreeEntry(
            name = if (path.isEmpty()) "" else visibleName(file.name, media),
            path = path,
            isDirectory = directory,
            sizeBytes = if (directory) 0L else file.length(),
            modifiedAt = file.lastModified(),
            mediaId = media?.id,
            isVideo = !directory && isLikelyVideo(file.name, media?.mimeType ?: mimeFor(file.name), media != null),
            thumbnailPath = media?.thumbnailPath,
            durationMs = media?.durationMs ?: 0L,
            playbackPositionMs = media?.playbackPositionMs ?: 0L,
        )
    }

    private fun visibleName(fileName: String, media: MediaEntity?): String {
        if (media == null || !UUID_NAME.matches(baseNameOf(fileName)) || media.displayName.isBlank()) return fileName
        val extension = fileName.substringAfterLast('.', "")
        return if (extension.isEmpty() || media.displayName.endsWith(".$extension", ignoreCase = true)) {
            media.displayName
        } else {
            "${media.displayName}.$extension"
        }
    }

    private fun mimeFor(name: String): String? = extensionOf(name)?.let {
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(it)
    }

    private fun isLikelyVideo(name: String, mime: String?, indexed: Boolean): Boolean {
        val extension = extensionOf(name)
        if (extension in PrivateStorage.SUBTITLE_EXTENSIONS) return false
        return indexed || extension in ImportRepositoryImpl.VIDEO_EXTENSIONS ||
            mime?.startsWith("video/", ignoreCase = true) == true
    }

    private fun databasePath(path: String): String =
        if (path.isEmpty()) PrivateStorage.MEDIA_DIR else storage.mediaRelativePath(path)

    private fun join(parent: String, name: String): String = if (parent.isEmpty()) name else "$parent/$name"

    private fun isFile(file: File): Boolean = Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS)
    private fun isDirectory(file: File): Boolean = Files.isDirectory(file.toPath(), LinkOption.NOFOLLOW_LINKS)


    private enum class Operation { MOVE, DELETE }
    private data class JournalRecord(val id: Long, val oldPath: String)
    private data class Journal(
        val operation: Operation,
        val source: String,
        val destination: String,
        val records: List<JournalRecord>,
    )

    /** 先持久化操作说明，再改变文件；数据库事务提交前崩溃时可还原。 */
    private fun writeJournal(journal: Journal): File {
        require(journal.records.size <= MAX_JOURNAL_RECORDS) { "操作涉及的媒体记录过多" }
        val root = journalRoot()
        val directory = File(root, UUID.randomUUID().toString())
        Files.createDirectory(directory.toPath())
        try {
            val temporary = File(directory, "$MANIFEST.tmp")
            FileOutputStream(temporary).use { stream ->
                val output = DataOutputStream(stream)
                output.writeInt(JOURNAL_VERSION)
                output.writeUTF(journal.operation.name)
                output.writeUTF(journal.source)
                output.writeUTF(journal.destination)
                output.writeInt(journal.records.size)
                journal.records.forEach { record ->
                    output.writeLong(record.id)
                    output.writeUTF(record.oldPath)
                }
                output.flush()
                stream.fd.sync()
            }
            moveNoOverwrite(temporary, File(directory, MANIFEST))
            return directory
        } catch (error: Throwable) {
            try {
                deleteTree(directory.toPath())
            } catch (cleanup: Throwable) {
                error.addSuppressed(cleanup)
            }
            throw error
        }
    }

    private fun journalRoot(): File {
        require(!Files.isSymbolicLink(storage.backupsRoot.toPath())) { "操作日志目录不能是符号链接" }
        if (!storage.backupsRoot.isDirectory && !storage.backupsRoot.mkdirs()) throw IOException("无法创建操作日志目录")
        val root = File(storage.backupsRoot, JOURNAL_DIRECTORY)
        require(!Files.isSymbolicLink(root.toPath())) { "操作日志目录不能是符号链接" }
        if (!root.isDirectory && !root.mkdir()) throw IOException("无法创建操作日志目录")
        return root
    }

    private fun readJournal(directory: File): Journal {
        val manifest = File(directory, MANIFEST)
        if (Files.isSymbolicLink(manifest.toPath())) throw IOException("操作日志异常")
        return DataInputStream(FileInputStream(manifest)).use { input ->
            if (input.readInt() != JOURNAL_VERSION) throw IOException("不支持的操作日志版本")
            val operation = try { Operation.valueOf(input.readUTF()) } catch (error: IllegalArgumentException) {
                throw IOException("未知操作日志", error)
            }
            val source = input.readUTF()
            val destination = input.readUTF()
            val count = input.readInt()
            if (count < 0 || count > MAX_JOURNAL_RECORDS) throw IOException("操作日志记录数量异常")
            val records = List(count) { JournalRecord(input.readLong(), input.readUTF()) }
            Journal(operation, source, destination, records)
        }
    }

    private suspend fun recoverPendingJournals() {
        val root = journalRoot()
        val directories = root.listFiles() ?: throw IOException("无法读取操作日志")
        directories.forEach { directory ->
            if (!isDirectory(directory)) throw IOException("操作日志目录异常")
            val manifest = File(directory, MANIFEST)
            if (!manifest.exists()) {
                if (Files.exists(File(directory, PAYLOAD).toPath(), LinkOption.NOFOLLOW_LINKS)) {
                    throw IOException("操作日志不完整，已保留暂存内容：${directory.name}")
                }
                deleteTree(directory.toPath())
            } else {
                recoverJournal(directory)
            }
        }
        if (directories.isNotEmpty()) notifyChanged()
    }

    /** 数据库已提交时保留日志供下次恢复，并明确告知调用方操作已生效。 */
    private suspend fun finishCommittedJournal(directory: File?) {
        checkNotNull(directory) { "操作日志缺失" }
        try {
            recoverJournal(directory)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            throw IOException("操作已提交，但暂存清理失败，后续访问会重试", error)
        }
    }

    private suspend fun compensate(directory: File?, error: Throwable) {
        if (directory == null) return
        withContext(NonCancellable) {
            try {
                recoverJournal(directory, rollbackUnindexed = true)
            } catch (recovery: Throwable) {
                error.addSuppressed(recovery)
            }
        }
    }

    /** 根据事务中的媒体路径判断提交结果；状态不明确时保留日志和文件，不猜测删除。 */
    private suspend fun recoverJournal(directory: File, rollbackUnindexed: Boolean = false) {
        val journal = readJournal(directory)
        require(journal.source.isNotEmpty()) { "操作日志路径异常" }
        val source = storage.resolveMediaFile(journal.source)
        val destination = if (journal.operation == Operation.MOVE) {
            require(journal.destination.isNotEmpty()) { "操作日志路径异常" }
            storage.resolveMediaFile(journal.destination)
        } else {
            File(directory, PAYLOAD)
        }
        val sourceExists = Files.exists(source.toPath(), LinkOption.NOFOLLOW_LINKS)
        val destinationExists = Files.exists(destination.toPath(), LinkOption.NOFOLLOW_LINKS)
        val current = journal.records.map { mediaDao.findById(it.id)?.relativePath }
        val old = journal.records.indices.all { current[it] == journal.records[it].oldPath }
        val committed = if (journal.records.isEmpty()) {
            !rollbackUnindexed && destinationExists && (!sourceExists || sameFile(source, destination))
        } else if (journal.operation == Operation.DELETE) {
            val deleted = current.all { it == null }
            if (!old && !deleted) throw IOException("删除操作状态不一致，已保留日志")
            deleted
        } else {
            val oldPrefix = databasePath(journal.source)
            val newPrefix = databasePath(journal.destination)
            val moved = journal.records.indices.all {
                val oldPath = journal.records[it].oldPath
                oldPath.startsWith(oldPrefix) && current[it] == newPrefix + oldPath.removePrefix(oldPrefix)
            }
            if (!old && !moved) throw IOException("移动操作状态不一致，已保留日志")
            moved
        }
        if (journal.operation == Operation.DELETE) {
            if (destinationExists && committed) {
                deleteTree(destination.toPath())
            } else if (destinationExists) {
                if (sourceExists) {
                    if (!sameFile(source, destination)) throw IOException("原位置已被占用，已保留暂存内容")
                    Files.delete(destination.toPath())
                } else {
                    moveNoOverwrite(destination, source)
                }
            } else if (!sourceExists && !committed) {
                throw IOException("原文件和暂存内容均不存在")
            }
        } else if (committed) {
            if (!destinationExists) throw IOException("移动目标不存在，已保留日志")
            if (sourceExists && sameFile(source, destination)) Files.delete(source.toPath())
        } else if (sourceExists) {
            if (destinationExists && sameFile(source, destination)) Files.delete(destination.toPath())
        } else if (destinationExists) {
            moveNoOverwrite(destination, source)
        } else {
            throw IOException("移动源和目标均不存在")
        }
        deleteTree(directory.toPath())
    }

    private fun sameFile(first: File, second: File): Boolean =
        try { Files.isSameFile(first.toPath(), second.toPath()) } catch (_: IOException) { false }


    companion object {
        private const val JOURNAL_DIRECTORY = ".file-tree-operations"
        private const val MANIFEST = "manifest"
        private const val PAYLOAD = "payload"
        private const val JOURNAL_VERSION = 1
        private const val MAX_JOURNAL_RECORDS = 1_000_000
        private val UUID_NAME = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
    }
}

private val SUBTITLE_LANGUAGE_SUFFIX = Regex("[._-](?:[a-zA-Z]{2,3}(?:[-_][a-zA-Z0-9]{2,8})*|简体|繁体|中文|英文)")

private fun compareNames(left: String, right: String): Int =
    left.compareTo(right, ignoreCase = true).takeIf { it != 0 } ?: left.compareTo(right)

/** 匹配同基名字幕及常见的语言后缀，不把其他同前缀影片的字幕混入。 */
internal fun isMatchingSubtitle(videoName: String, subtitleName: String): Boolean {
    if (extensionOf(subtitleName) !in PrivateStorage.SUBTITLE_EXTENSIONS) return false
    val videoBase = baseNameOf(videoName)
    val subtitleBase = baseNameOf(subtitleName)
    if (subtitleBase.equals(videoBase, ignoreCase = true)) return true
    if (!subtitleBase.startsWith(videoBase, ignoreCase = true)) return false
    val suffix = subtitleBase.substring(videoBase.length)
    return SUBTITLE_LANGUAGE_SUFFIX.matches(suffix)
}


/** 不跟随符号链接清理暂存目录，防止递归删除树外内容。 */
internal fun deleteTree(root: Path) {
    if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return
    Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
        override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
            Files.delete(file)
            return FileVisitResult.CONTINUE
        }
        override fun postVisitDirectory(dir: Path, error: IOException?): FileVisitResult {
            if (error != null) throw error
            Files.delete(dir)
            return FileVisitResult.CONTINUE
        }
    })
}
