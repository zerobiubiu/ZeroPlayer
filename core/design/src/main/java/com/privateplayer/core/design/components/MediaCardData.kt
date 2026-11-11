package com.privateplayer.core.design.components

import com.privateplayer.core.common.format.formatBytes
import com.privateplayer.core.common.format.formatDateTime
import com.privateplayer.core.common.format.formatDuration
import com.privateplayer.domain.model.Media
import com.privateplayer.domain.repository.MediaFileLocator
import java.io.File

/**
 * 列表/卡片展示所需的媒体数据。
 *
 * 由 ViewModel 在 IO 线程解析缩略图文件后一次性给出，
 * Compose 层不再访问文件系统。
 */
data class MediaCardData(
    val id: Long,
    val title: String,
    val subtitle: String,
    val durationText: String,
    val progress: Float?,
    val thumbnailFile: File?,
    val isFavorite: Boolean,
    val sizeText: String,
    val resolutionText: String,
    val addedAtText: String,
    val playedAtText: String,
    /** 相对时间，用于首页“最近播放”这类强调新鲜度的场景。 */
    val playedAtRelativeText: String,
)

/** 领域模型 → 卡片数据。缩略图缺失时返回 null，由组件绘制占位渐变。 */
fun Media.toCardData(locator: MediaFileLocator): MediaCardData = MediaCardData(
    id = id,
    title = displayName,
    subtitle = folderName ?: resolutionLabel,
    durationText = formatDuration(durationMs),
    progress = progress.takeIf { it > 0f },
    thumbnailFile = locator.thumbnailFile(thumbnailPath),
    isFavorite = isFavorite,
    sizeText = formatBytes(sizeBytes),
    resolutionText = resolutionLabel,
    addedAtText = formatDateTime(addedAt),
    playedAtText = formatDateTime(lastPlayedAt),
    playedAtRelativeText = com.privateplayer.core.common.format.formatRelativeTime(lastPlayedAt),
)
