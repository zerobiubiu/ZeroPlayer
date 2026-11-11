package com.privateplayer.domain.repository

import java.io.File
import kotlinx.coroutines.flow.Flow

/** 路径相对于应用私有文件树根目录；空字符串代表根目录。 */
data class FileTreeEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val modifiedAt: Long,
    val mediaId: Long? = null,
    val isVideo: Boolean = false,
    val thumbnailPath: String? = null,
    val durationMs: Long = 0L,
    val playbackPositionMs: Long = 0L,
)

/** 所有文件树操作均由数据层实现，界面不直接访问文件系统。 */
interface FileTreeRepository {
    /** 仅在真实文件树发生变更时发出新版本。 */
    fun observeChanges(): Flow<Long>
    fun observeDirectory(path: String): Flow<List<FileTreeEntry>>
    suspend fun list(path: String): List<FileTreeEntry>
    suspend fun find(path: String): FileTreeEntry?
    suspend fun createDirectory(parent: String, name: String): FileTreeEntry
    suspend fun rename(path: String, name: String): FileTreeEntry
    suspend fun move(path: String, destination: String): FileTreeEntry
    suspend fun delete(path: String)
    suspend fun openForRead(path: String): File
    suspend fun requireDirectory(path: String): File
    /** 原位建立视频的内部记录；已有记录时直接返回其 ID。 */
    suspend fun ensureMedia(path: String): Long
    suspend fun matchingSubtitles(path: String): List<String>
    fun notifyChanged()
}
