package com.privateplayer.core.playback.session

/** 对外会话可展示的媒体信息；不包含私有文件路径。 */
data class SessionMedia(
    val mediaId: Long,
    val title: String,
    val artist: String? = null,
    val durationMs: Long = 0L,
    /** 压缩后的图片数据；发布后不应原地修改。 */
    val artworkData: ByteArray? = null,
)
