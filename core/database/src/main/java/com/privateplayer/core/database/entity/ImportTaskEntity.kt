package com.privateplayer.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 持久化的导入任务。
 *
 * 导入一旦开始就写入该表，因此进程被杀、系统清理后台后仍可恢复：
 * - [tempRelativePath] 指向 `cache/temporary` 下的临时文件，恢复时按 [bytesCopied] 续传；
 * - [state] 为 [ImportTaskState] 的持久化字符串；
 * - [sourceUri] 使用 SAF 持久化授权，恢复后仍能重新打开原始文件（只读）。
 */
@Entity(
    tableName = "import_tasks",
    indices = [Index(value = ["state"]), Index(value = ["createdAt"])],
)
data class ImportTaskEntity(
    @PrimaryKey val id: String,
    val sourceUri: String,
    val displayName: String,
    val mimeType: String?,
    val sizeBytes: Long,
    val targetFileName: String,
    /** 导入完成后媒体归入的文件夹；null 表示未归档。 */
    val folderId: Long?,
    val tempRelativePath: String,
    val state: String,
    val bytesCopied: Long,
    val errorMessage: String?,
    val mediaId: Long?,
    val createdAt: Long,
    val updatedAt: Long,
)

