package com.privateplayer.domain.model

import androidx.annotation.StringRes
import com.privateplayer.domain.R

// 已写入旧版 import_tasks.errorMessage；新任务沿用原文，不需要迁移历史记录。
const val WEB_STAGING_MISSING_REASON = "上传暂存文件已丢失，请从网页重新上传"
const val TARGET_DIRECTORY_GONE_REASON = "目标目录已不存在，暂存已保留，请恢复目录后重试"
const val DIRECTORY_UNAVAILABLE_REASON = "目录不存在或不是目录"

enum class ImportErrorKind(@StringRes val messageRes: Int, val canRetry: Boolean = true) {
    Space(R.string.import_error_space),
    Source(R.string.import_error_source),
    Io(R.string.import_error_io),
    Canceled(R.string.import_error_canceled),
    Generic(R.string.import_error_generic),
    WebStagingMissing(R.string.import_error_web_staging_missing, canRetry = false),
    TargetDirectoryMissing(R.string.import_error_target_directory_missing);

    /** 已说明恢复方法的固定原因不重复展示，也不向界面或网页转发含路径、凭据的系统诊断。 */
    fun diagnostic(raw: String?): String? = when (this) {
        WebStagingMissing, TargetDirectoryMissing -> null
        else -> raw?.takeUnless { it.isBlank() || privateDiagnostic.containsMatchIn(it) }
    }
}

private val privateDiagnostic = Regex(
    """(?:^|[\s:=（("'：])[/\\]|://|[A-Za-z]:\\|(?:content|file|web-upload):|password|passwd|token|secret|authorization|bearer|口令|密码|令牌""",
    RegexOption.IGNORE_CASE,
)

fun classifyImportError(raw: String?, isWebUpload: Boolean): ImportErrorKind {
    when (raw) {
        WEB_STAGING_MISSING_REASON -> if (isWebUpload) return ImportErrorKind.WebStagingMissing
        TARGET_DIRECTORY_GONE_REASON, DIRECTORY_UNAVAILABLE_REASON -> return ImportErrorKind.TargetDirectoryMissing
    }
    if (raw.isNullOrBlank()) return ImportErrorKind.Generic
    val text = raw.lowercase()
    return when {
        raw.contains("空间") || text.contains("space") || text.contains("enospc") -> ImportErrorKind.Space
        raw.contains("来源") || raw.contains("重新选择") || raw.contains("重新分享") ||
            text.contains("permission") || text.contains("security") -> ImportErrorKind.Source
        raw.contains("取消") || text.contains("cancel") -> ImportErrorKind.Canceled
        raw.contains("读写") || raw.contains("磁盘") || text.contains("io") ||
            text.contains("read") || text.contains("write") -> ImportErrorKind.Io
        else -> ImportErrorKind.Generic
    }
}
