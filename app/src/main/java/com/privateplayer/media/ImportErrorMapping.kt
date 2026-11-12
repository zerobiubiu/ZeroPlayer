package com.privateplayer.media

import androidx.annotation.StringRes

/**
 * 导入失败原因分类（F-12）：把原始错误映射为用户可行动文案。
 *
 * 纯函数，按错误文本中的关键词归类；未知错误保留通用提示，
 * 原始错误文本在界面“详情”中折叠展示，不直接主展示。
 */
enum class ImportErrorKind { Space, Source, Io, Canceled, Generic }

fun classifyImportError(raw: String?): ImportErrorKind {
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

@StringRes
fun ImportErrorKind.messageRes(): Int = when (this) {
    ImportErrorKind.Space -> R.string.app_task_error_space
    ImportErrorKind.Source -> R.string.app_task_error_source
    ImportErrorKind.Io -> R.string.app_task_error_io
    ImportErrorKind.Canceled -> R.string.app_task_error_canceled
    ImportErrorKind.Generic -> R.string.app_task_error_generic
}
