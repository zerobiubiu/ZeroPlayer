package com.privateplayer.domain.model

/** 私有存储中的一个字幕文件。 */
data class SubtitleFile(
    val name: String,
    /** 绝对路径；播放器交给播放引擎加载，UI 不直接读取内容。 */
    val absolutePath: String,
    val format: String,
)

/** 媒体文件在私有存储中的定位结果；供设置页/详情页展示真实磁盘路径。 */
data class MediaFileInfo(
    val absolutePath: String,
    val exists: Boolean,
    val sizeBytes: Long,
)
