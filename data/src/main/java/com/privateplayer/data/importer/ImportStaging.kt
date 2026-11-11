package com.privateplayer.data.importer

import com.privateplayer.core.database.dao.ImportTaskDao
import com.privateplayer.core.database.entity.ImportTaskEntity
import com.privateplayer.core.storage.PrivateFileIdentity
import com.privateplayer.core.storage.PrivateStorage
import com.privateplayer.core.storage.moveNoOverwrite
import com.privateplayer.core.storage.resolveFileInMediaRoot
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.util.UUID

/** 网页上传文件保存在 files 而不是 cache，避免已接收任务被系统清理缓存时丢失。 */
internal const val WEB_UPLOAD_DIRECTORY = "web-uploads"
private const val LOCAL_IMPORT_DIRECTORY = "import-staging"
private const val WEB_UPLOAD_SOURCE_PREFIX = "web-upload:"

internal fun webUploadSource(taskId: String): String = "$WEB_UPLOAD_SOURCE_PREFIX$taskId"

internal fun ImportTaskEntity.isWebUpload(): Boolean = sourceUri == webUploadSource(id)

internal fun PrivateStorage.webUploadFile(taskId: String): File =
    resolveImportStagedFile(absoluteFile("$WEB_UPLOAD_DIRECTORY/${UUID.fromString(taskId)}.part"))

/** 新任务使用持久目录；旧暂存即使已被移动，恢复时也必须使用日志中的原始位置。 */
internal fun PrivateStorage.importTempFile(task: ImportTaskEntity): File {
    ImportCommitJournal(this).read(task)?.let { return resolveImportStagedFile(File(it.sourcePath)) }
    if (task.isWebUpload()) return webUploadFile(task.id)
    if (task.tempRelativePath.startsWith("${PrivateStorage.TEMPORARY_DIR}/")) {
        val legacy = resolveImportStagedFile(absoluteCacheFile(task.tempRelativePath))
        if (Files.exists(legacy.toPath(), LinkOption.NOFOLLOW_LINKS)) return legacy
    }
    val id = UUID.fromString(task.id).toString()
    return resolveImportStagedFile(absoluteFile("$LOCAL_IMPORT_DIRECTORY/$id.part"))
}

/** 只有复制、校验及数据同步完成后才写入完成标记，供失去外部授权后的任务恢复。 */
internal fun importReadyFile(temp: File): File = File(temp.parentFile, "${temp.name}.ready")

internal fun markImportReady(temp: File, length: Long) {
    require(length >= 0L && temp.isFile && temp.length() == length) { "暂存文件长度不一致" }
    val marker = importReadyFile(temp)
    val pending = File.createTempFile("${temp.name}.", ".ready-pending", temp.parentFile)
    try {
        FileOutputStream(pending).use { output ->
            output.write(length.toString().toByteArray(StandardCharsets.UTF_8))
            output.flush()
            output.fd.sync()
        }
        // 完成标记可重写；先同步新标记再替换，不能留下截断的一半文本。
        Files.move(
            pending.toPath(), marker.toPath(),
            java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
        )
    } finally {
        Files.deleteIfExists(pending.toPath())
    }
}

internal fun readyImportSize(temp: File): Long? {
    if (!Files.isRegularFile(temp.toPath(), LinkOption.NOFOLLOW_LINKS)) return null
    val marker = importReadyFile(temp)
    val length = try {
        marker.takeIf { Files.isRegularFile(it.toPath(), LinkOption.NOFOLLOW_LINKS) && it.length() <= 32L }
            ?.readText()?.toLongOrNull()
    } catch (_: IOException) {
        null
    }
    return length?.takeIf { it >= 0L && it == temp.length() }
}

internal fun removeImportReady(temp: File) {
    Files.deleteIfExists(importReadyFile(temp).toPath())
}

/**
 * 提交日志只记录真实源身份和路径，不复制内容。所有方法由调用方置于共享文件树锁内。
 * 先同步日志，再 rename，再提交 Room；任一中断点都只能接管 dev/ino 和长度均相符的文件。
 */
class ImportCommitJournal(private val storage: PrivateStorage) {
    data class Prepared(
        val sourcePath: String,
        val targetPath: String,
        val identity: PrivateFileIdentity,
        val size: Long,
    )

    fun read(task: ImportTaskEntity): Prepared? {
        val marker = marker(task.id)
        if (!Files.exists(marker.toPath(), LinkOption.NOFOLLOW_LINKS)) return null
        if (!Files.isRegularFile(marker.toPath(), LinkOption.NOFOLLOW_LINKS) || marker.length() > 16_384L) {
            throw IOException("导入提交日志异常，已保留文件")
        }
        val prepared = DataInputStream(FileInputStream(marker)).use { input ->
            if (input.readInt() != VERSION || input.readUTF() != task.id) throw IOException("导入提交日志不匹配")
            Prepared(input.readUTF(), input.readUTF(), PrivateFileIdentity(input.readLong(), input.readLong()), input.readLong())
                .also { if (input.read() != -1) throw IOException("导入提交日志内容异常") }
        }
        if (prepared.targetPath != task.targetFileName || prepared.size < 0L) throw IOException("导入提交目标不匹配")
        validateSource(task, File(prepared.sourcePath))
        require(prepared.targetPath.isNotEmpty()) { "导入目标不能为空" }
        storage.resolveMediaFile(prepared.targetPath)
        return prepared
    }

    fun prepare(task: ImportTaskEntity, source: File): Prepared {
        read(task)?.let { return it }
        val checked = validateSource(task, source)
        if (!Files.isRegularFile(checked.toPath(), LinkOption.NOFOLLOW_LINKS)) throw IOException("导入暂存不存在")
        require(task.targetFileName.isNotEmpty()) { "导入目标不能为空" }
        val target = storage.resolveMediaFile(task.targetFileName)
        val prepared = Prepared(checked.path, task.targetFileName, storage.fileIdentity(checked), checked.length())
        if (Files.exists(target.toPath(), LinkOption.NOFOLLOW_LINKS) && !owns(target, prepared)) {
            throw IOException("目标位置已有同名文件，暂存已保留，请处理重名后重试")
        }
        val marker = marker(task.id)
        val pending = File.createTempFile("${task.id}.", ".pending", marker.parentFile)
        try {
            FileOutputStream(pending).use { stream ->
                DataOutputStream(stream).apply {
                    writeInt(VERSION)
                    writeUTF(task.id)
                    writeUTF(prepared.sourcePath)
                    writeUTF(prepared.targetPath)
                    writeLong(prepared.identity.device)
                    writeLong(prepared.identity.inode)
                    writeLong(prepared.size)
                    flush()
                }
                stream.fd.sync()
            }
            moveNoOverwrite(pending, marker)
        } finally {
            Files.deleteIfExists(pending.toPath())
        }
        return prepared
    }

    /** rename 后源路径可以消失；此时只凭持久身份确认目标，不能凭同名或长度接管。 */
    fun content(prepared: Prepared): File {
        val target = storage.resolveMediaFile(prepared.targetPath)
        if (Files.exists(target.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            if (owns(target, prepared)) return target
            throw IOException("目标位置已有其他文件，导入暂存和日志已保留")
        }
        val source = storage.resolveImportStagedFile(File(prepared.sourcePath))
        if (owns(source, prepared)) return source
        throw IOException("导入源和目标均无法确认身份，已保留提交日志")
    }

    fun commit(prepared: Prepared): File {
        val current = content(prepared)
        val target = storage.resolveMediaFile(prepared.targetPath)
        if (storage.hasFileIdentity(target, prepared.identity) && target.length() == prepared.size) return target
        return storage.commitTempFileLocked(current, storage.mediaRelativePath(prepared.targetPath))
    }

    /** 数据库未提交时退回原暂存；恢复目标被占用时两份内容都保留，绝不覆盖。 */
    fun rollback(prepared: Prepared) {
        val source = storage.resolveImportStagedFile(File(prepared.sourcePath))
        val target = storage.resolveMediaFile(prepared.targetPath)
        val sourceExists = Files.exists(source.toPath(), LinkOption.NOFOLLOW_LINKS)
        val targetExists = Files.exists(target.toPath(), LinkOption.NOFOLLOW_LINKS)
        if (targetExists && owns(target, prepared)) {
            if (sourceExists) {
                if (!owns(source, prepared)) throw IOException("原暂存位置已被占用，正式位置及日志已保留")
                // 兼容升级前已存在的双路径；仅删除已确认属于本任务的未入库目标。
                Files.delete(target.toPath())
            } else {
                moveNoOverwrite(target, source)
            }
        } else if (!owns(source, prepared)) {
            throw IOException("无法确认可恢复的导入文件，已保留所有现有内容")
        }
    }

    /** 仅在任务及媒体事务已完成后调用，未知身份的同名暂存不能被清理。 */
    fun finish(task: ImportTaskEntity, prepared: Prepared) {
        val source = storage.resolveImportStagedFile(File(prepared.sourcePath))
        if (owns(source, prepared)) {
            if (!owns(storage.resolveMediaFile(prepared.targetPath), prepared)) {
                throw IOException("正式目标已不存在或身份变化，唯一暂存已保留")
            }
            Files.delete(source.toPath())
            removeImportReady(source)
        } else if (!Files.exists(source.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            removeImportReady(source)
        }
        Files.deleteIfExists(marker(task.id).toPath())
    }

    fun owns(file: File, prepared: Prepared): Boolean =
        storage.hasFileIdentity(file, prepared.identity) && file.length() == prepared.size

    private fun validateSource(task: ImportTaskEntity, source: File): File {
        val checked = storage.resolveImportStagedFile(source)
        val expected = if (task.isWebUpload()) {
            storage.webUploadFile(task.id)
        } else {
            storage.resolveImportStagedFile(storage.absoluteFile("$LOCAL_IMPORT_DIRECTORY/${UUID.fromString(task.id)}.part"))
        }
        val legacy = if (!task.isWebUpload() && task.tempRelativePath.startsWith("${PrivateStorage.TEMPORARY_DIR}/")) {
            storage.resolveImportStagedFile(storage.absoluteCacheFile(task.tempRelativePath))
        } else null
        require(checked == expected || checked == legacy) { "提交来源与导入任务不匹配" }
        return checked
    }

    private fun marker(taskId: String): File {
        val id = UUID.fromString(taskId).toString()
        val directory = resolveFileInMediaRoot(storage.absoluteFile(""), DIRECTORY)
        if (!directory.isDirectory && !directory.mkdir()) throw IOException("无法创建导入提交日志目录")
        return resolveFileInMediaRoot(directory, "$id.commit")
    }

    private companion object {
        const val DIRECTORY = "import-commits"
        const val VERSION = 1
    }
}

/** 调用方持有任务执行锁和树锁；取消不丢弃已接收内容，仍可显式重试。 */
internal suspend fun preserveCanceledImport(
    task: ImportTaskEntity,
    tasks: ImportTaskDao,
    storage: PrivateStorage,
) {
    if (tasks.findState(task.id) != "CANCELED") return
    val commits = ImportCommitJournal(storage)
    var prepared: ImportCommitJournal.Prepared? = null
    try {
        prepared = commits.read(task)
        if (prepared != null) commits.rollback(prepared)
    } catch (error: Exception) {
        tasks.failIfCanceled(
            task.id, prepared?.size ?: task.bytesCopied,
            "已取消，但暂存恢复失败，文件及提交日志已保留：${error.message}", System.currentTimeMillis(),
        )
        throw error
    }
    val source = storage.importTempFile(task)
    if (source.isFile || prepared != null) {
        tasks.failIfCanceled(
            task.id, source.takeIf { it.isFile }?.length() ?: prepared!!.size,
            "已取消处理，暂存文件仍保留，可重试", System.currentTimeMillis(),
        )
    }
}
