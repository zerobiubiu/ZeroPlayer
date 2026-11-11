package com.privateplayer.data.webupload

import com.privateplayer.core.database.dao.ImportTaskDao
import com.privateplayer.core.database.entity.ImportTaskEntity
import com.privateplayer.core.storage.PrivateStorage
import com.privateplayer.data.importer.ImportWorkScheduler
import com.privateplayer.data.importer.WEB_UPLOAD_DIRECTORY
import com.privateplayer.data.importer.isWebUpload
import com.privateplayer.data.importer.webUploadFile
import com.privateplayer.data.importer.webUploadSource
import com.privateplayer.domain.model.ImportTaskState
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

/** 记录接收目标，使进程在接收完成和任务入库之间终止时仍可恢复上传。 */
internal class WebUploadJournal(
    private val storage: PrivateStorage,
    private val tasks: ImportTaskDao,
    private val scheduler: ImportWorkScheduler,
) {
    internal data class Entry(
        val id: String,
        val name: String,
        val target: String,
        val size: Long,
        val mimeType: String?,
    )
    fun begin(id: String) {
        active.add(id)
    }

    fun end(id: String) {
        active.remove(id)
    }

    private val directory: File get() = storage.absoluteFile(WEB_UPLOAD_DIRECTORY)
    private fun metadata(id: String) = File(directory, "$id.json")

    fun prepare(entry: Entry) {
        if (!directory.isDirectory && !directory.mkdirs() && !directory.isDirectory) {
            throw IOException("无法创建上传暂存目录")
        }
        val temporary = File(directory, "${entry.id}.json.tmp")
        val target = metadata(entry.id)
        if (!temporary.createNewFile()) throw IOException("无法创建上传记录")
        try {
            val data = JSONObject().put("id", entry.id).put("name", entry.name)
                .put("target", entry.target).put("size", entry.size)
                .put("mimeType", entry.mimeType ?: JSONObject.NULL).toString()
                .toByteArray(StandardCharsets.UTF_8)
            FileOutputStream(temporary).use { output ->
                output.write(data)
                output.flush()
                output.fd.sync()
            }
            if (target.exists() || !temporary.renameTo(target)) throw IOException("无法保存上传记录")
        } finally {
            temporary.delete()
        }
    }

    fun remove(id: String) {
        metadata(id).delete()
    }

    fun insert(entry: Entry) {
        val now = System.currentTimeMillis()
        runBlocking {
            tasks.insert(
                ImportTaskEntity(
                    id = entry.id,
                    sourceUri = webUploadSource(entry.id),
                    displayName = entry.name,
                    mimeType = entry.mimeType,
                    sizeBytes = entry.size,
                    targetFileName = entry.target,
                    folderId = null,
                    tempRelativePath = "$WEB_UPLOAD_DIRECTORY/${entry.id}.part",
                    state = ImportTaskState.QUEUED.name,
                    bytesCopied = entry.size,
                    errorMessage = null,
                    mediaId = null,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }
    }

    /** 仅处理本服务带记录的暂存；旧版未知来源文件不会被删除。 */
    fun recover() {
        directory.listFiles()?.filter { it.isFile && it.name.endsWith(".json") }?.forEach { file ->
            val entry = runCatching { read(file) }.getOrNull() ?: return@forEach
            if (entry.id in active) return@forEach
            val existing = runBlocking { tasks.findById(entry.id) }
            if (existing != null) {
                if (existing.isWebUpload()) {
                    if (existing.state in setOf("QUEUED", "RUNNING", "COMMITTING")) {
                        runCatching { scheduler.schedule(entry.id) }
                    }
                    remove(entry.id)
                }
                return@forEach
            }
            val temp = storage.webUploadFile(entry.id)
            if (temp.isFile && temp.length() == entry.size) {
                // 接收后同步失败或进程终止时先重新同步；失败则保留记录和暂存。
                if (runCatching {
                        FileOutputStream(temp, true).use { it.fd.sync() }
                        insert(entry)
                    }.isSuccess
                ) {
                    remove(entry.id)
                    runCatching { scheduler.schedule(entry.id) }
                }
            } else {
                // 未完整接收的内容不是已确认上传；无记录的旧暂存不在此清理。
                temp.delete()
                remove(entry.id)
            }
        }
    }

    private fun read(file: File): Entry {
        val id = file.name.removeSuffix(".json")
        require(runCatching { UUID.fromString(id).toString() }.getOrNull() == id)
        val data = JSONObject(file.readText(StandardCharsets.UTF_8))
        require(data.getString("id") == id)
        val name = data.getString("name")
        val target = data.getString("target")
        val size = data.getLong("size")
        val mimeType = if (data.isNull("mimeType")) null else data.optString("mimeType").takeIf { it.isNotBlank() }
        require(size >= 0L && name.isNotBlank() && target.isNotBlank())
        require(!target.startsWith('/') && '\\' !in target && '\u0000' !in target)
        require(target.split('/').all { it.isNotEmpty() && it != "." && it != ".." })
        return Entry(id, name, target, size, mimeType)
    }

    companion object {
        private val active = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    }
}
