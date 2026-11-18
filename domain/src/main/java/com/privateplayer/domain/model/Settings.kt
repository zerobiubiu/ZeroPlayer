package com.privateplayer.domain.model

import androidx.annotation.StringRes
import com.privateplayer.domain.R

/** 主题模式。文案由界面按 [labelRes] 解析，领域层不持有中文。 */
enum class ThemeMode(@StringRes val labelRes: Int) {
    SYSTEM(R.string.option_theme_system),
    LIGHT(R.string.option_theme_light),
    DARK(R.string.option_theme_dark),
}

/** 网格密度（媒体库 / 首页共用）。 */
enum class GridDensity(val label: String) {
    COMPACT("紧凑（更多列）"),
    DEFAULT("标准"),
    COMFORTABLE("宽松（更少列）"),
}

/** 画面比例（播放器）。 */
enum class VideoAspect(@StringRes val labelRes: Int) {
    FIT(R.string.option_aspect_fit),
    FILL(R.string.option_aspect_fill),
    CROP(R.string.option_aspect_crop),
    RATIO_16_9(R.string.option_aspect_16_9),
    RATIO_4_3(R.string.option_aspect_4_3),
}

/** 字幕背景样式。 */
enum class SubtitleBackground(@StringRes val labelRes: Int) {
    NONE(R.string.option_subtitle_bg_none),
    SEMI_TRANSPARENT(R.string.option_subtitle_bg_semi),
    SOLID(R.string.option_subtitle_bg_solid),
    NAVY(R.string.option_subtitle_bg_navy),
}

/** 字幕文字颜色。 */
enum class SubtitleTextColor(@StringRes val labelRes: Int) {
    WHITE(R.string.option_subtitle_color_white),
    YELLOW(R.string.option_subtitle_color_yellow),
    CYAN(R.string.option_subtitle_color_cyan),
}

/** 文本字幕字体。 */
enum class SubtitleFont(@StringRes val labelRes: Int) {
    DEFAULT(R.string.option_subtitle_font_default),
    SERIF(R.string.option_subtitle_font_serif),
    MONOSPACE(R.string.option_subtitle_font_monospace),
}

/** 文本字幕字形。 */
enum class SubtitleFontStyle(@StringRes val labelRes: Int) {
    NORMAL(R.string.option_subtitle_style_normal),
    BOLD(R.string.option_subtitle_style_bold),
    ITALIC(R.string.option_subtitle_style_italic),
    BOLD_ITALIC(R.string.option_subtitle_style_bold_italic),
}

/** 记录断点与重新打开视频的策略；不影响播放历史或正在运行的会话。 */
enum class PlaybackProgressMode(@StringRes val labelRes: Int) {
    DISABLED(R.string.option_progress_disabled),
    RECORD_ONLY(R.string.option_progress_record_only),
    RESUME(R.string.option_progress_resume);

    fun positionToSave(positionMs: Long, durationMs: Long): Long = when {
        this == DISABLED -> 0L
        durationMs > 0 -> positionMs.coerceIn(0L, durationMs)
        else -> positionMs.coerceAtLeast(0L)
    }

    fun positionOnOpen(positionMs: Long, durationMs: Long): Long = when {
        this != RESUME || positionMs <= 0 -> 0L
        // 已知时长的 98%（向上取整），不将未知时长或短断点误判为已看完。
        durationMs > 0 && positionMs >= durationMs - durationMs / 50 -> 0L
        else -> positionMs
    }

    companion object {
        fun fromStoredFlags(record: Boolean, resume: Boolean): PlaybackProgressMode = when {
            !record -> DISABLED
            resume -> RESUME
            else -> RECORD_ONLY
        }
    }
}

/** 常规播放与长按临时倍速共用的边界。 */
object PlaybackSpeedLimits {
    const val MIN = 0.25f
    const val MAX = 8f
    const val MIN_TEMPORARY = 1.5f
}

/** 播放相关偏好。 */
data class PlaybackPreferences(
    val autoPlayNext: Boolean = true,
    val progressMode: PlaybackProgressMode = PlaybackProgressMode.RESUME,
    val defaultSpeed: Float = 1.0f,
    val defaultAspect: VideoAspect = VideoAspect.FIT,
    val seekStepSeconds: Int = 10,
    val longPressSpeed: Float = 2.0f,
)

/** 文本字幕显示偏好；默认保留 ASS 自带的排版，不改变位图字幕的字形。 */
data class SubtitlePreferences(
    val fontSizeScale: Float = 1.0f,
    val background: SubtitleBackground = SubtitleBackground.SEMI_TRANSPARENT,
    val textColor: SubtitleTextColor = SubtitleTextColor.WHITE,
    val delayStepMs: Int = 100,
    val font: SubtitleFont = SubtitleFont.DEFAULT,
    val fontStyle: SubtitleFontStyle = SubtitleFontStyle.NORMAL,
    /** 相对默认位置的画面高度百分比（-10～30），正值上移。 */
    val verticalOffsetPercent: Int = 0,
    val overrideAss: Boolean = false,
)

/** 手势偏好。 */
data class GesturePreferences(
    val brightnessGestureEnabled: Boolean = true,
    val volumeGestureEnabled: Boolean = true,
    val seekGestureEnabled: Boolean = true,
    val longPressSpeedEnabled: Boolean = true,
    /** 滑动进度时是否震动；部分设备没有线性马达或手感较差，允许关闭。 */
    val hapticsEnabled: Boolean = true,
)

/** 显示偏好。 */
data class DisplayPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
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
