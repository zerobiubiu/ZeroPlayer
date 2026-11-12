package com.privateplayer.feature.home

import androidx.annotation.StringRes

/**
 * 首页用户可见消息。
 *
 * ViewModel 只产出消息类型与参数（纯逻辑、可 JVM 单测），
 * 文案统一由界面从 `feature/home` 资源解析，避免在逻辑层硬编码中文。
 */
sealed interface HomeMessage {
    /** 不支持播放的文件：给出下一步（文件名由 F-03 在阶段 3 补充）。 */
    data object UnsupportedFile : HomeMessage

    /** 无法打开视频。 */
    data object OpenVideoFailed : HomeMessage

    /** 新建文件夹失败。 */
    data object CreateFolderFailed : HomeMessage

    /** 文件夹已创建。 */
    data object FolderCreated : HomeMessage

    /** 重命名失败。 */
    data object RenameFailed : HomeMessage

    /** 已重命名。 */
    data object Renamed : HomeMessage

    /** 移动失败。 */
    data object MoveFailed : HomeMessage

    /** 已移动。 */
    data object Moved : HomeMessage

    /** 删除失败。 */
    data object DeleteFailed : HomeMessage

    /** 已删除。 */
    data object Deleted : HomeMessage
}

/** 消息对应的文案资源；界面用 `Context.getString` 解析，参数按资源占位符传入。 */
@StringRes
fun HomeMessage.labelRes(): Int = when (this) {
    HomeMessage.UnsupportedFile -> R.string.home_unsupported_file
    HomeMessage.OpenVideoFailed -> R.string.home_open_video_failed
    HomeMessage.CreateFolderFailed -> R.string.home_create_folder_failed
    HomeMessage.FolderCreated -> R.string.home_folder_created
    HomeMessage.RenameFailed -> R.string.home_rename_failed
    HomeMessage.Renamed -> R.string.home_renamed
    HomeMessage.MoveFailed -> R.string.home_move_failed
    HomeMessage.Moved -> R.string.home_moved
    HomeMessage.DeleteFailed -> R.string.home_delete_failed
    HomeMessage.Deleted -> R.string.home_deleted
}
