package com.privateplayer.data.importer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.room.withTransaction
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.privateplayer.core.database.PrivatePlayerDatabase
import com.privateplayer.core.database.dao.ImportTaskDao
import com.privateplayer.core.database.dao.MediaDao
import com.privateplayer.core.database.entity.ImportTaskEntity
import com.privateplayer.core.database.entity.MediaEntity
import com.privateplayer.core.storage.ImportCopier
import com.privateplayer.core.storage.MediaProbe
import com.privateplayer.core.storage.PrivateStorage
import com.privateplayer.data.repository.ImportRepositoryImpl
import com.privateplayer.domain.model.ImportTaskState
import com.privateplayer.domain.repository.FileTreeRepository
import com.privateplayer.domain.repository.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.FileNotFoundException
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

/**
 * 导入执行器。
 *
 * 恢复时先校验已复制前缀，完整复制后将文件和完成标记同步到私有持久目录。
 * 先持久化 dev/ino、长度和路径，再在共享树锁内 rename；数据库失败时原位退回暂存。
 * 来源无法持久授权且已失效时，任务明确提示重新分享或重新选择。
 */
@HiltWorker
class ImportWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val taskDao: ImportTaskDao,
    private val database: PrivatePlayerDatabase,
    private val mediaDao: MediaDao,
    private val storage: PrivateStorage,
    private val fileTree: FileTreeRepository,
    private val copier: ImportCopier,
    private val probe: MediaProbe,
    private val settingsRepository: SettingsRepository,
) : CoroutineWorker(appContext, params) {

    private var lastForegroundUpdateAt = 0L

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val taskId = inputData.getString(KEY_TASK_ID) ?: return@withContext Result.failure()
        storage.withImportTaskLock(taskId) { importTask(taskId) }
    }

    private suspend fun importTask(taskId: String): Result {
        val task = taskDao.findById(taskId) ?: return Result.success()
        val commits = ImportCommitJournal(storage)
        if (task.state == ImportTaskState.COMPLETED.name) {
            return try {
                storage.withMediaTreeLock { commits.read(task)?.let { commits.finish(task, it) } }
                Result.success()
            } catch (_: IOException) {
                if (runAttemptCount < MAX_AUTO_RETRY) Result.retry() else Result.failure()
            }
        }
        if (task.state == ImportTaskState.CANCELED.name) {
            storage.withMediaTreeLock { preserveCanceledImport(task, taskDao, storage) }
            return Result.success()
        }
        if (taskDao.claimForImport(taskId, System.currentTimeMillis()) == 0) return Result.success()

        var tempFile: java.io.File? = null
        try {
            setForeground(progressForeground(task.displayName, 0f, indeterminate = true))
            val targetPath = task.targetFileName
            require(targetPath.isNotEmpty()) { "目标文件名无效" }
            storage.resolveMediaFile(targetPath)
            val parentPath = targetPath.substringBeforeLast('/', "")
            val name = targetPath.substringAfterLast('/')
            fileTree.requireDirectory(parentPath)
            val mediaPath = storage.mediaRelativePath(targetPath)
            val prepared = storage.withMediaTreeLock { commits.read(task) }
            val staged = storage.withMediaTreeLock { storage.importTempFile(task) }
            tempFile = staged
            val content = if (prepared != null) {
                storage.withMediaTreeLock { commits.content(prepared) }
            } else staged
            val expectedSize = when {
                task.isWebUpload() || task.tempRelativePath.startsWith("import-staging/") ->
                    task.sizeBytes.takeIf { it >= 0L }
                else -> task.sizeBytes.takeIf { it > 0L }
            }
            val readySize = readyImportSize(staged)
            val copied = when {
                prepared != null -> prepared.size
                task.isWebUpload() -> {
                    if (!staged.isFile) throw IOException("上传暂存文件已丢失，请从网页重新上传")
                    staged.length()
                }
                readySize != null -> readySize
                expectedSize == 0L && staged.isFile -> 0L
                task.state == ImportTaskState.COMMITTING.name && staged.isFile -> staged.length()
                else -> copier.copy(
                    uri = Uri.parse(task.sourceUri),
                    target = staged,
                    // 使用实际前缀并由 copier 逐块核对；数据库进度滞后时不能先截断失败暂存。
                    alreadyCopied = staged.takeIf { it.isFile }?.length() ?: 0L,
                ) { bytes, total ->
                    ensureNotCanceled(taskId)
                    taskDao.updateBytesCopied(taskId, bytes, System.currentTimeMillis())
                    val ratio = if (total != null && total > 0L) bytes.toFloat() / total else null
                    val now = System.currentTimeMillis()
                    if (now - lastForegroundUpdateAt >= FOREGROUND_UPDATE_INTERVAL_MS) {
                        lastForegroundUpdateAt = now
                        setForeground(progressForeground(task.displayName, ratio?.coerceIn(0f, 1f) ?: 0f, ratio == null))
                    }
                }
            }
            ensureNotCanceled(taskId)
            if (expectedSize != null && copied != expectedSize) {
                throw IOException("复制大小不一致：期望 $expectedSize，实际 $copied")
            }
            if (prepared == null && readySize == null) markImportReady(staged, copied)

            val isVideo = ImportRepositoryImpl.isVideo(name, task.mimeType)
            val settings = settingsRepository.observeSettings().first()
            val fingerprint = if (isVideo) probe.fingerprint(content) else null
            val metadata = if (isVideo) {
                try {
                    probe.probe(content, task.mimeType)
                } catch (_: Exception) {
                    // 平台无法识别并不代表 libmpv 不能播放，保留文件及未知元数据。
                    MediaProbe.ProbedMetadata(0L, 0, 0, null, null, null, 0, 0, task.mimeType)
                }
            } else null

            var thumbnailPath: String? = null
            if (isVideo && settings.import.generateThumbnails && fingerprint != null) {
                val candidate = storage.thumbnailRelativePath(fingerprint.substringAfter(':').take(32))
                val generated = runCatching {
                    probe.generateThumbnail(content, storage.absoluteCacheFile(candidate))
                }.getOrDefault(false)
                if (generated) thumbnailPath = candidate
            }

            storage.withMediaTreeLock {
                // requireDirectory 会恢复文件树日志，此处只重验目录，不能在锁内再次调用仓库。
                val lockedParent = storage.resolveMediaFile(parentPath)
                if (!java.nio.file.Files.isDirectory(lockedParent.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                    throw IOException("目标目录已不存在，暂存已保留，请恢复目录后重试")
                }
                if (taskDao.beginCommit(taskId, System.currentTimeMillis()) == 0) throw ImportCanceledException()
                val commit = prepared ?: commits.prepare(task, staged)
                try {
                    commits.commit(commit)
                    database.withTransaction {
                        if (taskDao.findState(taskId) != ImportTaskState.COMMITTING.name) throw ImportCanceledException()
                        val duplicate = if (fingerprint != null && settings.import.duplicateDetection) {
                            mediaDao.findByFingerprint(fingerprint)?.takeIf { it.relativePath != mediaPath }
                        } else null
                        val notice = duplicate?.let { "可能与 ${it.displayName} 重复，已保留副本" }
                        val mediaId = if (isVideo && metadata != null && fingerprint != null) {
                            mediaDao.findByRelativePath(mediaPath)?.id ?: run {
                                val addedAt = System.currentTimeMillis()
                                mediaDao.insert(
                                    MediaEntity(
                                        displayName = storage.baseNameOfMedia(name),
                                        fileName = name,
                                        relativePath = mediaPath,
                                        folderId = task.folderId,
                                        mimeType = metadata.mimeType ?: task.mimeType,
                                        sizeBytes = copied,
                                        durationMs = metadata.durationMs,
                                        width = metadata.width,
                                        height = metadata.height,
                                        videoCodec = metadata.videoCodec,
                                        audioCodec = metadata.audioCodec,
                                        bitrate = metadata.bitrate,
                                        audioTrackCount = metadata.audioTrackCount,
                                        subtitleTrackCount = metadata.subtitleTrackCount,
                                        fingerprint = fingerprint,
                                        thumbnailPath = thumbnailPath,
                                        isFavorite = false,
                                        addedAt = addedAt,
                                        updatedAt = addedAt,
                                        lastPlayedAt = null,
                                        playbackPositionMs = 0L,
                                        playedDurationMs = 0L,
                                        playCount = 0,
                                    ),
                                )
                            }
                        } else null
                        taskDao.completeWithNotice(taskId, mediaId, notice, System.currentTimeMillis())
                    }
                } catch (error: Throwable) {
                    withContext(NonCancellable) {
                        if (taskDao.findState(taskId) != ImportTaskState.COMPLETED.name) {
                            try {
                                commits.rollback(commit)
                            } catch (recovery: Throwable) {
                                error.addSuppressed(recovery)
                            }
                        }
                    }
                    throw error
                }
                commits.finish(task, commit)
            }
            fileTree.notifyChanged()
            return Result.success()
        } catch (_: ImportCanceledException) {
            cleanupCanceled(task)
            return Result.failure()
        } catch (cancellation: CancellationException) {
            withContext(NonCancellable) {
                try {
                    cleanupCanceled(task)
                } catch (recovery: Exception) {
                    cancellation.addSuppressed(recovery)
                }
            }
            throw cancellation
        } catch (error: Exception) {
            val state = taskDao.findState(taskId)
            if (state == ImportTaskState.COMPLETED.name) {
                fileTree.notifyChanged()
                return if (runAttemptCount < MAX_AUTO_RETRY) Result.retry() else Result.failure()
            }
            if (state == null || state == ImportTaskState.CANCELED.name) {
                cleanupCanceled(task)
                return Result.failure()
            }
            val message = if (error is SecurityException || error is FileNotFoundException) {
                "来源文件不可用或读取授权已失效，请重新分享或重新选择"
            } else {
                error.message ?: "导入失败，请重试"
            }
            val retainedBytes = tempFile?.takeIf { it.isFile }?.length() ?: task.bytesCopied
            if (taskDao.failIfActive(taskId, retainedBytes, message, System.currentTimeMillis()) == 0) {
                return Result.failure()
            }
            val retryable = error is IOException && error !is FileNotFoundException && runAttemptCount < MAX_AUTO_RETRY
            return if (retryable) Result.retry() else Result.failure()
        }
    }

    private suspend fun cleanupCanceled(task: ImportTaskEntity) = withContext(NonCancellable) {
        storage.withMediaTreeLock { preserveCanceledImport(task, taskDao, storage) }
        fileTree.notifyChanged()
    }

    private suspend fun ensureNotCanceled(taskId: String) {
        val state = taskDao.findState(taskId)
        if (state != ImportTaskState.RUNNING.name) throw ImportCanceledException()
    }

    private fun progressForeground(title: String, progress: Float, indeterminate: Boolean): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager?.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "导入进度",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply { description = "显示文件导入的实时进度" },
            )
        }

        val contentIntent = applicationContext.packageManager
            .getLaunchIntentForPackage(applicationContext.packageName)
            ?.let { Intent(it).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP) }
        val pendingIntent = contentIntent?.let {
            PendingIntent.getActivity(
                applicationContext,
                0,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("正在导入")
            .setContentText(title)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, (progress * 100).toInt().coerceIn(0, 100), indeterminate)
            .apply { pendingIntent?.let { setContentIntent(it) } }
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    /** 用户取消导入的内部信号。 */
    private class ImportCanceledException : Exception("导入已取消")

    companion object {
        const val KEY_TASK_ID = "task_id"
        const val TAG = "import"
        private const val CHANNEL_ID = "import_progress"
        private const val NOTIFICATION_ID = 4211
        private const val MAX_AUTO_RETRY = 2
        private const val FOREGROUND_UPDATE_INTERVAL_MS = 800L
    }
}
