package com.privateplayer.data.repository

import android.net.Uri
import com.privateplayer.core.common.di.IoDispatcher
import com.privateplayer.core.common.files.extensionOf
import com.privateplayer.core.common.files.sanitizeFileName
import com.privateplayer.core.common.files.uniqueFileName
import com.privateplayer.core.database.dao.ImportTaskDao
import com.privateplayer.core.database.entity.ImportTaskEntity
import com.privateplayer.core.storage.ImportCopier
import com.privateplayer.core.storage.PrivateStorage
import com.privateplayer.core.storage.SafDocuments
import com.privateplayer.data.importer.ImportCommitJournal
import com.privateplayer.data.importer.ImportWorkScheduler
import com.privateplayer.data.importer.importTempFile
import com.privateplayer.data.importer.preserveCanceledImport
import com.privateplayer.data.mapper.toDomain
import com.privateplayer.domain.model.ImportEnqueueResult
import com.privateplayer.domain.model.ImportTask
import com.privateplayer.domain.model.ImportTaskState
import com.privateplayer.domain.model.RejectedImport
import com.privateplayer.domain.repository.FileTreeRepository
import com.privateplayer.domain.repository.ImportRepository
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
class ImportRepositoryImpl @Inject constructor(
    private val taskDao: ImportTaskDao,
    private val storage: PrivateStorage,
    private val fileTree: FileTreeRepository,
    private val safDocuments: SafDocuments,
    private val scheduler: ImportWorkScheduler,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ImportRepository {
    private val enqueueLock = Mutex()

    override fun observeTasks(): Flow<List<ImportTask>> =
        taskDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeActiveCount(): Flow<Int> = taskDao.observeActive().map { it.size }

    override suspend fun enqueue(
        uris: List<String>,
        folderId: Long?,
        targetDirectory: String,
    ): ImportEnqueueResult = enqueueSources(uris, folderId, targetDirectory, null, false, false, null)

    override suspend fun enqueueExternal(
        uris: List<String>,
        mimeType: String?,
        openForPlayback: Boolean,
        requestTaskId: String?,
    ): ImportEnqueueResult = enqueueSources(uris, null, "", mimeType, openForPlayback, true, requestTaskId)

    private suspend fun enqueueSources(
        uris: List<String>,
        folderId: Long?,
        targetDirectory: String,
        mimeHint: String?,
        forceVideo: Boolean,
        reusePendingSource: Boolean,
        requestTaskId: String?,
    ): ImportEnqueueResult = withContext(ioDispatcher) {
        enqueueLock.withLock {
            val accepted = mutableListOf<String>()
            val rejected = mutableListOf<RejectedImport>()
            val requestedId = requestTaskId?.let {
                runCatching { UUID.fromString(it).toString() }.getOrElse {
                    return@withLock ImportEnqueueResult(emptyList(), emptyList(), listOf(RejectedImport("", "导入任务标识无效")))
                }
            }
            val root: File
            val directory: File
            try {
                root = fileTree.requireDirectory("").canonicalFile
                directory = fileTree.requireDirectory(targetDirectory).canonicalFile
            } catch (error: Exception) {
                return@withLock ImportEnqueueResult(
                    emptyList(), emptyList(),
                    uris.distinct().map { RejectedImport(it, error.message ?: "目标目录不可用") },
                )
            }
            val relativeDirectory = directory.relativeTo(root).invariantSeparatorsPath.let {
                if (it == ".") "" else it
            }
            val tasks = taskDao.observeAll().first()
            val reservedNames = directory.listFiles()?.mapTo(HashSet()) { it.name } ?: HashSet()
            tasks.filter { it.state != ImportTaskState.COMPLETED.name && it.state != ImportTaskState.CANCELED.name }
                .filter { it.targetFileName.substringBeforeLast('/', "") == relativeDirectory }
                .mapTo(reservedNames) { it.targetFileName.substringAfterLast('/') }
            var pendingBytes = 0L
            val availableBytes = (storage.freeBytes() - ImportCopier.SPACE_HEADROOM_BYTES).coerceAtLeast(0L)

            for ((index, rawUri) in uris.distinct().withIndex()) {
                val taskIdForRequest = requestedId.takeIf { index == 0 }
                val existingRequest = tasks.firstOrNull { it.id == taskIdForRequest }
                if (existingRequest != null) {
                    if (existingRequest.sourceUri == rawUri) accepted += existingRequest.id
                    else rejected += RejectedImport(rawUri, "导入任务标识已被其他来源使用")
                    continue
                }
                val pending = if (reusePendingSource) tasks.firstOrNull {
                    it.sourceUri == rawUri &&
                        (!forceVideo || isVideo(it.targetFileName, it.mimeType)) &&
                        it.state in setOf(
                            ImportTaskState.QUEUED.name, ImportTaskState.RUNNING.name, ImportTaskState.COMMITTING.name,
                        )
                } else null
                if (pending != null) {
                    accepted += pending.id
                    continue
                }
                val uri = Uri.parse(rawUri)
                val info = try {
                    safDocuments.query(uri) ?: throw IllegalArgumentException("无法读取来源信息")
                } catch (error: Exception) {
                    rejected += RejectedImport(rawUri, error.message ?: "无法读取来源，请重新分享或重新选择")
                    continue
                }
                val size = info.sizeBytes
                if (size != null && (pendingBytes > availableBytes || size > availableBytes - pendingBytes)) {
                    rejected += RejectedImport(info.displayName, "存储空间不足")
                    continue
                }
                if (size != null) pendingBytes += size

                val taskId = taskIdForRequest ?: UUID.randomUUID().toString()
                val safeName = boundedImportName(info.displayName)
                val name = uniqueFileName(safeName, reservedNames)
                reservedNames += name
                val targetPath = if (relativeDirectory.isEmpty()) name else "$relativeDirectory/$name"
                val sourceMime = info.mimeType?.takeUnless { it == "*/*" || it == "application/octet-stream" }
                val mime = if (forceVideo && sourceMime?.startsWith("video/") != true) {
                    "video/*"
                } else {
                    sourceMime ?: mimeHint
                }
                safDocuments.takePersistableReadPermission(uri)
                taskDao.insert(
                    ImportTaskEntity(
                        id = taskId,
                        sourceUri = uri.toString(),
                        displayName = info.displayName,
                        mimeType = mime,
                        sizeBytes = size ?: -1L,
                        targetFileName = targetPath,
                        folderId = folderId,
                        tempRelativePath = "import-staging/$taskId.part",
                        state = ImportTaskState.QUEUED.name,
                        bytesCopied = 0L,
                        errorMessage = null,
                        mediaId = null,
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
                accepted += taskId
                scheduler.schedule(taskId)
            }
            ImportEnqueueResult(accepted, emptyList(), rejected)
        }
    }

    override suspend fun cancel(taskId: String): Unit = withContext(ioDispatcher) {
        val canceled = storage.withMediaTreeLock {
            taskDao.cancelUnlessFinished(taskId, System.currentTimeMillis()) == 1
        }
        if (!canceled) return@withContext
        scheduler.cancel(taskId)
        // 先通知 WorkManager 停止复制，再等旧执行器退出，避免边读写边回退暂存。
        storage.withImportTaskLock(taskId) {
            storage.withMediaTreeLock {
                taskDao.findById(taskId)?.let { preserveCanceledImport(it, taskDao, storage) }
            }
        }
        fileTree.notifyChanged()
    }

    override suspend fun retry(taskId: String): Unit = withContext(ioDispatcher) {
        storage.withImportTaskLock(taskId) {
            val retried = storage.withMediaTreeLock { taskDao.retryFailed(taskId, System.currentTimeMillis()) == 1 }
            if (retried) {
                scheduler.cancel(taskId)
                scheduler.schedule(taskId)
            }
        }
    }

    override suspend fun removeTask(taskId: String): Unit = withContext(ioDispatcher) {
        val task = taskDao.findById(taskId) ?: return@withContext
        if (ImportTaskState.entries.firstOrNull { it.name == task.state }?.isActive == true) cancel(taskId)
        storage.withImportTaskLock(taskId) {
            storage.withMediaTreeLock {
                val current = taskDao.findById(taskId) ?: return@withMediaTreeLock
                val commits = ImportCommitJournal(storage)
                val prepared = commits.read(current)
                if (current.state == ImportTaskState.COMPLETED.name) {
                    if (prepared != null) commits.finish(current, prepared)
                } else if (prepared != null || storage.importTempFile(current).isFile) {
                    // 失败暂存和提交身份不能因移除记录变成不可恢复的孤儿。
                    return@withMediaTreeLock
                }
                taskDao.deleteById(taskId)
            }
        }
    }

    override suspend fun reconcilePendingTasks(): Unit = withContext(ioDispatcher) {
        // 进程可能在写下取消状态后退出；先补做回退，保留可重试任务。
        taskDao.observeAll().first().filter { it.state == ImportTaskState.CANCELED.name }.forEach { task ->
            storage.withImportTaskLock(task.id) {
                storage.withMediaTreeLock { preserveCanceledImport(task, taskDao, storage) }
            }
        }
        taskDao.findPending().forEach { task ->
            if (!scheduler.isActive(task.id)) {
                scheduler.schedule(task.id)
            }
        }
    }

    companion object {
        val VIDEO_EXTENSIONS = setOf(
            "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v", "ts", "m2ts",
            "mpg", "mpeg", "3gp", "3g2", "rmvb", "rm", "ogv", "asf", "mts", "f4v",
        )

        fun isVideo(name: String, mimeType: String?): Boolean {
            val extension = extensionOf(name)
            if (extension != null && extension in PrivateStorage.SUBTITLE_EXTENSIONS) return false
            return mimeType?.startsWith("video/", ignoreCase = true) == true ||
                (extension != null && extension in VIDEO_EXTENSIONS)
        }
    }
}

/** 给重名编号预留空间，按 UTF-8 字节截断而不是把多字节字符从中间截开。 */
internal fun boundedImportName(raw: String): String {
    val dot = raw.lastIndexOf('.')
    val candidateExtension = if (dot > 0 && dot < raw.lastIndex) {
        ".${sanitizeFileName(raw.substring(dot + 1))}"
    } else ""
    val extension = candidateExtension.takeIf { it.toByteArray(Charsets.UTF_8).size <= 32 } ?: ""
    val base = sanitizeFileName(if (extension.isNotEmpty()) raw.substring(0, dot) else raw)
    val budget = 220 - extension.toByteArray(Charsets.UTF_8).size
    val result = StringBuilder()
    var bytes = 0
    var index = 0
    while (index < base.length) {
        val codePoint = base.codePointAt(index)
        val length = when {
            codePoint <= 0x7f -> 1
            codePoint <= 0x7ff -> 2
            codePoint <= 0xffff -> 3
            else -> 4
        }
        if (bytes + length > budget) break
        result.appendCodePoint(codePoint)
        bytes += length
        index += Character.charCount(codePoint)
    }
    return result.toString().ifBlank { "未命名" } + extension
}
