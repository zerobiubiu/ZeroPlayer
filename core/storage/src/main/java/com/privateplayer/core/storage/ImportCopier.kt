package com.privateplayer.core.storage

import android.net.Uri
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 大文件流式复制。
 *
 * 关键约束：
 * - 复制使用 1 MiB 缓冲区，几 GB / 几十 GB 的文件也不会读入内存；
 * - 断点续传前逐块核对已复制部分，防止来源变化后拼接出损坏文件；
 * - 每次保存断点前同步文件，避免数据库进度领先于磁盘数据；
 * - 每写一批检查协程取消，用户取消能立刻停止 IO；
 * - 结束后 `fsync`，避免进程随后被杀时留下“长度已满但内容未落盘”的临时文件。
 */
@Singleton
class ImportCopier @Inject constructor(
    private val safDocuments: SafDocuments,
    private val storage: PrivateStorage,
) {
    /**
     * @param onProgress 已复制总字节数、来源总大小（未知为 null）
     * @return 最终复制的总字节数
     */
    suspend fun copy(
        uri: Uri,
        target: File,
        alreadyCopied: Long,
        onProgress: suspend (copied: Long, total: Long?) -> Unit,
    ): Long {
        val safFile = safDocuments.query(uri)
        val totalSize = safFile?.sizeBytes
        target.parentFile?.mkdirs()

        val startOffset = if (alreadyCopied > 0L) {
            if (target.isFile && target.length() > alreadyCopied) {
                RandomAccessFile(target, "rw").use { it.setLength(alreadyCopied) }
            }
            alreadyCopied.coerceAtMost(target.length().takeIf { target.isFile } ?: 0L)
        } else {
            if (target.exists()) target.delete()
            0L
        }

        val input = safDocuments.openInput(uri)
        input.use { source ->
            verifyCopiedPrefix(source, target, startOffset)
            FileOutputStream(target, startOffset > 0L).use { output ->
                val buffer = ByteArray(PrivateStorage.COPY_BUFFER_SIZE)
                var copied = startOffset
                var lastReported = 0L
                var lastReportedAt = 0L
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = source.read(buffer)
                    if (read < 0) break
                    if (read == 0) continue
                    output.write(buffer, 0, read)
                    copied += read
                    val now = System.currentTimeMillis()
                    if (copied - lastReported >= PROGRESS_BYTES_STEP || now - lastReportedAt >= PROGRESS_TIME_STEP_MS) {
                        output.fd.sync()
                        lastReported = copied
                        lastReportedAt = now
                        onProgress(copied, totalSize)
                    }
                }
                output.flush()
                output.fd.sync()
                onProgress(copied, totalSize)
                return copied
            }
        }
    }

    /** 续传前核对来源和已有暂存的全部前缀，来源变化时保留暂存并明确失败。 */
    private suspend fun verifyCopiedPrefix(source: InputStream, target: File, offset: Long) {
        if (offset <= 0L) return
        val sourceBuffer = ByteArray(64 * 1024)
        val existingBuffer = ByteArray(sourceBuffer.size)
        RandomAccessFile(target, "r").use { existing ->
            var remaining = offset
            while (remaining > 0L) {
                currentCoroutineContext().ensureActive()
                val requested = minOf(sourceBuffer.size.toLong(), remaining).toInt()
                var read = 0
                while (read < requested) {
                    val count = source.read(sourceBuffer, read, requested - read)
                    if (count < 0) throw IOException("来源文件长度小于断点位置，请重新分享或重新选择")
                    if (count > 0) read += count
                }
                existing.readFully(existingBuffer, 0, requested)
                if (requested < sourceBuffer.size) {
                    sourceBuffer.fill(0, requested)
                    existingBuffer.fill(0, requested)
                }
                if (!sourceBuffer.contentEquals(existingBuffer)) {
                    throw IOException("来源文件已变化，无法安全续传，请重新分享或重新选择")
                }
                remaining -= requested
            }
        }
    }

    /**
     * 复制前检查：剩余空间需大于源文件大小 + 预留余量。
     * 大小未知时不拦截，由写入过程中的 IO 异常兜底。
     */
    fun hasEnoughSpace(requiredBytes: Long): Boolean {
        if (requiredBytes <= 0L) return true
        return storage.freeBytes() > requiredBytes + SPACE_HEADROOM_BYTES
    }

    companion object {
        private const val PROGRESS_BYTES_STEP = 8L * 1024 * 1024
        private const val PROGRESS_TIME_STEP_MS = 250L

        /** 预留 256 MiB，避免把设备写满。 */
        const val SPACE_HEADROOM_BYTES = 256L * 1024 * 1024
    }
}
