package com.privateplayer.domain.model

/** 主题模式。 */
enum class ThemeMode(val label: String) {
    SYSTEM("跟随系统"),
    LIGHT("浅色"),
    DARK("深色"),
}

/** 网格密度（媒体库 / 首页共用）。 */
enum class GridDensity(val label: String) {
    COMPACT("紧凑（更多列）"),
    DEFAULT("标准"),
    COMFORTABLE("宽松（更少列）"),
}

/** 画面比例（播放器）。 */
enum class VideoAspect(val label: String) {
    FIT("适应屏幕"),
    FILL("拉伸填充"),
    CROP("裁剪填满"),
    RATIO_16_9("16:9"),
    RATIO_4_3("4:3"),
}

/** 字幕背景样式。 */
enum class SubtitleBackground(val label: String) {
    NONE("无背景"),
    SEMI_TRANSPARENT("半透明"),
    SOLID("纯色"),
}

/** 字幕文字颜色。 */
enum class SubtitleTextColor(val label: String) {
    WHITE("白色"),
    YELLOW("黄色"),
    CYAN("青色"),
}

/** 播放相关偏好。 */
data class PlaybackPreferences(
    val autoPlayNext: Boolean = true,
    val rememberProgress: Boolean = true,
    val autoResume: Boolean = true,
    val defaultSpeed: Float = 1.0f,
    val defaultAspect: VideoAspect = VideoAspect.FIT,
    val seekStepSeconds: Int = 10,
    val longPressSpeed: Float = 2.0f,
)

/** 字幕显示偏好；ASS 等自带样式的字幕保留原有排版。 */
data class SubtitlePreferences(
    val fontSizeScale: Float = 1.0f,
    val background: SubtitleBackground = SubtitleBackground.SEMI_TRANSPARENT,
    val textColor: SubtitleTextColor = SubtitleTextColor.WHITE,
    val delayStepMs: Int = 100,
)

/** 手势偏好。 */
data class GesturePreferences(
    val brightnessGestureEnabled: Boolean = true,
    val volumeGestureEnabled: Boolean = true,
    val seekGestureEnabled: Boolean = true,
    val longPressSpeedEnabled: Boolean = true,
)

/** 显示偏好。 */
data class DisplayPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val gridDensity: GridDensity = GridDensity.DEFAULT,
)

/** 导入偏好。 */
data class ImportPreferences(
    val generateThumbnails: Boolean = true,
    val duplicateDetection: Boolean = true,
)

/** 全局设置聚合。 */
data class AppSettings(
    val playback: PlaybackPreferences = PlaybackPreferences(),
    val subtitle: SubtitlePreferences = SubtitlePreferences(),
    val gesture: GesturePreferences = GesturePreferences(),
    val display: DisplayPreferences = DisplayPreferences(),
    val import: ImportPreferences = ImportPreferences(),
)

/** 存储占用统计（设置页“存储管理”）。 */
data class StorageUsage(
    val mediaBytes: Long,
    val thumbnailBytes: Long,
    val temporaryBytes: Long,
    val freeBytes: Long,
    val mediaCount: Int,
) {
    val cacheBytes: Long get() = thumbnailBytes + temporaryBytes
}
