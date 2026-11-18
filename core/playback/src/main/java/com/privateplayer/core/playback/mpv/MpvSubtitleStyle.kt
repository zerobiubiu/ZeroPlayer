package com.privateplayer.core.playback.mpv

import com.privateplayer.domain.model.SubtitleBackground
import com.privateplayer.domain.model.SubtitleFont
import com.privateplayer.domain.model.SubtitleFontStyle
import com.privateplayer.domain.model.SubtitlePreferences
import com.privateplayer.domain.model.SubtitleTextColor
import dev.jdtech.mpv.MPVLib
import kotlin.math.roundToInt

/** 一份缓存仅对应一个 libmpv 实例；调用方持有引擎锁。 */
internal class MpvSubtitleStyle {
    private var applied: SubtitlePreferences? = null
    private var appliedFamily: String? = null
    private var strippedAss: Boolean? = null

    fun apply(mpv: MPVLib, preferences: SubtitlePreferences, codec: String?) {
        val nativeAss = codec == "ass" || codec == "ssa"
        val strip = nativeAss && preferences.overrideAss
        val preserveNative = codec == null || (nativeAss && !strip)
        val style = if (preserveNative) defaults else preferences
        // 随包 Fontconfig 扫描系统字体，但不解析 Android fonts.xml 的 sans-serif 等别名。
        // 文本轨传 OpenType 家族名；原生 ASS 则恢复 mpv 默认后备字体，不替作者选字体。
        val family = if (preserveNative) "sans-serif" else when (style.font) {
            SubtitleFont.DEFAULT -> "Roboto"
            SubtitleFont.SERIF -> "Noto Serif"
            SubtitleFont.MONOSPACE -> "Droid Sans Mono"
        }
        if (appliedFamily != family) {
            mpv.setPropertyString("sub-font", family)
            appliedFamily = family
        }
        val before = applied
        if (before != style) {
            applied = null // JNI 中途失败，下次从完整样式恢复。
            if (before?.fontStyle?.bold != style.fontStyle.bold) mpv.setPropertyBoolean("sub-bold", style.fontStyle.bold)
            if (before?.fontStyle?.italic != style.fontStyle.italic) mpv.setPropertyBoolean("sub-italic", style.fontStyle.italic)
            if (before?.fontSizeScale != style.fontSizeScale) {
                // 0.41 原生文本基准为 720p 下的 38 像素。只改字号，不缩放位图、边距或整个 ASS 画布。
                mpv.setPropertyDouble("sub-font-size", 38.0 * style.fontSizeScale)
            }
            if (before?.textColor != style.textColor) {
                mpv.setPropertyString("sub-color", when (style.textColor) {
                    SubtitleTextColor.WHITE -> "#FFFFFF"
                    SubtitleTextColor.YELLOW -> "#FFFF00"
                    SubtitleTextColor.CYAN -> "#00FFFF"
                })
            }
            if (before?.background != style.background) {
                val box = style.background != SubtitleBackground.NONE
                if (before == null || box != (before.background != SubtitleBackground.NONE)) {
                    mpv.setPropertyString("sub-border-style", if (box) "background-box" else "outline-and-shadow")
                }
                // mpv 使用 #[AA]RRGGBB；libass BorderStyle=4 的整块底色取自 BackColour。
                mpv.setPropertyString("sub-back-color", when (style.background) {
                    SubtitleBackground.NONE -> "#00000000"
                    SubtitleBackground.SEMI_TRANSPARENT -> "#80000000"
                    SubtitleBackground.SOLID -> "#000000"
                    SubtitleBackground.NAVY -> "#14284B"
                })
            }
            if (before?.verticalOffsetPercent != style.verticalOffsetPercent) {
                // 按视频高度换算文本底边距。默认留 12%，下移到 -10% 时仍有 2%，不裁掉整行。
                val marginPercent = 12 + style.verticalOffsetPercent.coerceIn(-10, 30)
                mpv.setPropertyInt("sub-margin-y", (720.0 * marginPercent / 100).roundToInt())
            }
            applied = style
        }
        if (strippedAss != strip) {
            // 0.41 的 force 会保留定位及行内标签，也不强制粗斜体。strip 单独渲染纯文本轨；
            // 切回 no 时 mpv 从原始包重建排版，不向原轨写入 sub-ass-style-overrides。
            mpv.setPropertyString("sub-ass-override", if (strip) "strip" else "no")
            strippedAss = strip
        }
    }

    private companion object {
        val defaults = SubtitlePreferences()
        val SubtitleFontStyle.bold get() = this == SubtitleFontStyle.BOLD || this == SubtitleFontStyle.BOLD_ITALIC
        val SubtitleFontStyle.italic get() = this == SubtitleFontStyle.ITALIC || this == SubtitleFontStyle.BOLD_ITALIC
    }
}
