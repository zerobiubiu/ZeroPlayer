package com.privateplayer.core.design.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * 品牌色板：以中性色为主的“私人影院”气质。
 *
 * 主色选用偏冷紫（私人、收藏、夜场感），第三色用琥珀作为强调，
 * 深色模式降低容器亮度、避免大面积高饱和背景，符合 Material 3 的色调映射规则。
 */
private val Primary = Color(0xFF5B4BCE)
private val OnPrimary = Color(0xFFFFFFFF)
private val PrimaryContainer = Color(0xFFE6DEFF)
private val OnPrimaryContainer = Color(0xFF170065)

private val Secondary = Color(0xFF605C72)
private val OnSecondary = Color(0xFFFFFFFF)
private val SecondaryContainer = Color(0xFFE6E0F9)
private val OnSecondaryContainer = Color(0xFF1C1A2B)

private val Tertiary = Color(0xFF7C5800)
private val OnTertiary = Color(0xFFFFFFFF)
private val TertiaryContainer = Color(0xFFFFDEA6)
private val OnTertiaryContainer = Color(0xFF271900)

private val Background = Color(0xFFFDF8FF)
private val OnBackground = Color(0xFF1C1B20)
private val SurfaceVariant = Color(0xFFE6E0F0)
private val OnSurfaceVariant = Color(0xFF47455A)
private val Outline = Color(0xFF787586)
private val OutlineVariant = Color(0xFFC9C4D6)
private val Error = Color(0xFFBA1A1A)
private val OnError = Color(0xFFFFFFFF)
private val SurfaceContainerLow = Color(0xFFF7F2FA)
private val SurfaceContainer = Color(0xFFF1ECF4)
private val SurfaceContainerHigh = Color(0xFFEBE6EE)

private val DarkPrimary = Color(0xFFC8BFFF)
private val DarkOnPrimary = Color(0xFF2B1A75)
private val DarkPrimaryContainer = Color(0xFF43319B)
private val DarkOnPrimaryContainer = Color(0xFFE6DEFF)
private val DarkSecondary = Color(0xFFC9C3DD)
private val DarkSecondaryContainer = Color(0xFF484459)
private val DarkTertiary = Color(0xFFF2BF48)
private val DarkOnTertiary = Color(0xFF412D00)
private val DarkTertiaryContainer = Color(0xFF5E4200)
private val DarkBackground = Color(0xFF131218)
private val DarkOnBackground = Color(0xFFE6E1E9)
private val DarkSurfaceVariant = Color(0xFF47455A)
private val DarkOnSurfaceVariant = Color(0xFFC9C5D6)
private val DarkOutline = Color(0xFF928FA0)
private val DarkOutlineVariant = Color(0xFF47455A)
private val DarkError = Color(0xFFFFB4AB)
private val DarkOnError = Color(0xFF690005)
private val DarkSurfaceContainerLow = Color(0xFF1B1A20)
private val DarkSurfaceContainer = Color(0xFF1F1E24)
private val DarkSurfaceContainerHigh = Color(0xFF2A2830)

val LightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    secondary = Secondary,
    onSecondary = OnSecondary,
    secondaryContainer = SecondaryContainer,
    onSecondaryContainer = OnSecondaryContainer,
    tertiary = Tertiary,
    onTertiary = OnTertiary,
    tertiaryContainer = TertiaryContainer,
    onTertiaryContainer = OnTertiaryContainer,
    background = Background,
    onBackground = OnBackground,
    surface = Background,
    onSurface = OnBackground,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = SurfaceContainerLow,
    surfaceContainer = SurfaceContainer,
    surfaceContainerHigh = SurfaceContainerHigh,
    surfaceContainerHighest = Color(0xFFE6E0E9),
    outline = Outline,
    outlineVariant = OutlineVariant,
    error = Error,
    onError = OnError,
    inverseSurface = Color(0xFF313036),
    inverseOnSurface = Color(0xFFF4EFF7),
    inversePrimary = DarkPrimary,
    scrim = Color(0xFF000000),
)

val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = Color(0xFF322F41),
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = Color(0xFFE6E0F9),
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = Color(0xFFFFDEA6),
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkBackground,
    onSurface = DarkOnBackground,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceContainerLowest = Color(0xFF0E0D12),
    surfaceContainerLow = DarkSurfaceContainerLow,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerHighest = Color(0xFF35323C),
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    error = DarkError,
    onError = DarkOnError,
    inverseSurface = Color(0xFFE6E1E9),
    inverseOnSurface = Color(0xFF313036),
    inversePrimary = Primary,
    scrim = Color(0xFF000000),
)

/** 缩略图缺省占位色，按媒体 id 稳定分配，避免列表出现“彩虹随机色”。 */
internal val PlaceholderPalettes = listOf(
    listOf(Color(0xFF3A2E5C), Color(0xFF241C38)),
    listOf(Color(0xFF2C3A4E), Color(0xFF1B2530)),
    listOf(Color(0xFF4A2F3B), Color(0xFF2C1B23)),
    listOf(Color(0xFF2F453C), Color(0xFF1B2A25)),
    listOf(Color(0xFF3F3A29), Color(0xFF262318)),
)
