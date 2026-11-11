package com.privateplayer.domain.model

/** 导入任务状态（持久化于 `import_tasks.state`）。 */
enum class ImportTaskState {
    /** 已入队，等待 WorkManager 调度。 */
    QUEUED,

    /** 正在复制或续传。 */
    RUNNING,

    /** 复制完成，正在校验并提交到正式媒体目录。 */
    COMMITTING,

    /** 已提交成功，媒体记录已入库。 */
    COMPLETED,

    /** 用户取消，临时文件已清理。 */
    CANCELED,

    /** 失败，可重试；临时文件保留以支持续传。 */
    FAILED,
    ;

    val isTerminal: Boolean get() = this == COMPLETED || this == CANCELED

    val isActive: Boolean get() = this == QUEUED || this == RUNNING || this == COMMITTING
}

/** 导入任务视图模型。 */
data class ImportTask(
    val id: String,
    val displayName: String,
    val mimeType: String?,
    val sizeBytes: Long,
    val state: ImportTaskState,
    val bytesCopied: Long,
    val errorMessage: String?,
    val mediaId: Long?,
    val createdAt: Long,
    val updatedAt: Long,
) {
    /** 0f..1f；总大小未知时返回 null，由 UI 显示不确定进度。 */
    val progress: Float?
        get() = when {
            state == ImportTaskState.COMPLETED -> 1f
            sizeBytes > 0L -> (bytesCopied.toFloat() / sizeBytes).coerceIn(0f, 1f)
            else -> null
        }
}

/** 入队结果。重复、空间不足等拒绝原因必须反馈给用户，不能静默丢弃。 */
data class ImportEnqueueResult(
    val acceptedTaskIds: List<String>,
    val duplicates: List<String>,
    val rejected: List<RejectedImport>,
)

data class RejectedImport(val displayName: String, val reason: String)
