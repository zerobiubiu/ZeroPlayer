package com.privateplayer.feature.player

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.privateplayer.core.design.theme.Spacing

/**
 * 播放器覆盖层 Token（F-34）：色板与关键尺寸的单一来源。
 *
 * 播放器保持独立深色媒体语境（不跟随应用明暗主题），但色板与间距集中定义、
 * 间距复用应用 [Spacing] 刻度，避免散落硬编码。强调色为媒体控制专用蓝，
 * 与应用内容主色（紫）区分，规则见设计系统文档。
 */
object PlayerTokens {
    /** 主文本：纯白。 */
    val Text: Color = Color.White

    /** 次级文本：72% 白。 */
    val TextSecondary: Color = Color.White.copy(alpha = 0.72f)

    /** 媒体控制强调色（进度条激活轨道等），非应用内容主色。 */
    val Accent: Color = Color(0xFF4A84FF)

    /** 覆盖层背景黑。 */
    val Scrim: Color = Color.Black

    /** 速度胶囊底：14% 白。 */
    val PillContainer: Color = Color.White.copy(alpha = 0.14f)

    /** 进度条未激活轨道：28% 白。 */
    val TrackInactive: Color = Color.White.copy(alpha = 0.28f)

    // 关键尺寸（触控目标 ≥48dp，F-36）。
    val TransportSize: Dp = 48.dp
    val TransportLargeSize: Dp = 56.dp
    val TransportIcon: Dp = 28.dp
    val TransportLargeIcon: Dp = 40.dp
    val TransportLoading: Dp = 22.dp
    val MinTouchTarget: Dp = 48.dp

    // 间距复用应用刻度。
    val GapSmall: Dp = Spacing.sm
    val GapMedium: Dp = Spacing.md
    val GapLarge: Dp = Spacing.lg
}
