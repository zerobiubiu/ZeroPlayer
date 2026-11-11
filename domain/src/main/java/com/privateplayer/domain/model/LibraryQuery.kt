package com.privateplayer.domain.model

/** 媒体库排序方式。 */
enum class MediaSort(val label: String) {
    ADDED_DESC("最近导入"),
    ADDED_ASC("最早导入"),
    NAME_ASC("名称 A→Z"),
    NAME_DESC("名称 Z→A"),
    DURATION_DESC("时长最长"),
    SIZE_DESC("体积最大"),
    LAST_PLAYED_DESC("最近播放"),
}

/** 媒体库分类筛选。数值型阈值直接对应真实数据，不做展示性假分类。 */
enum class MediaCategory(val label: String, val description: String, val icon: String) {
    SHORT("短片", "10 分钟以内", "movie"),
    MEDIUM("中长片", "10 分钟 - 40 分钟", "movie"),
    LONG("长片", "40 分钟以上", "movie"),
    ULTRA_HD("超高清", "分辨率 2560 宽及以上", "hd"),
    UNWATCHED("未观看", "从未播放过", "new"),
    FINISHED("已看完", "观看进度超过 98%", "done"),
}

/**
 * 媒体库查询条件。
 *
 * [search] 同时匹配展示名与文件名；[folderId] 为 null 表示不限制文件夹。
 */
data class LibraryQuery(
    val search: String = "",
    val folderId: Long? = null,
    val favoritesOnly: Boolean = false,
    val category: MediaCategory? = null,
    val sort: MediaSort = MediaSort.ADDED_DESC,
    /** 仅“未归档”媒体（folderId 为空）。 */
    val unfiledOnly: Boolean = false,
) {
    val hasFilters: Boolean
        get() = search.isNotBlank() || folderId != null || favoritesOnly || category != null || unfiledOnly
}
