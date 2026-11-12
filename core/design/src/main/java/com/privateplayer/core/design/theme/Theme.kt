package com.privateplayer.core.design.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 统一间距尺度。页面一律从这里取，避免各处硬编码 13dp 之类的魔法值。 */
object Spacing {
    val xxs: Dp = 2.dp
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 24.dp
    val xxl: Dp = 32.dp
    val xxxl: Dp = 48.dp

    /** 页面左右内容边距。 */
    val screenHorizontal = 16.dp

    /** 底部导航覆盖后的内容底部留白。 */
    val contentBottom = 96.dp
}

/**
 * 圆角单一语义刻度（F-35）：所有圆角从这里取值，Shapes 由其派生，避免两套数值冲突。
 * thumbnail=小元素（缩略图/进度条），card=卡片/容器，sheet=底部面板/对话框，pill=胶囊。
 */
object Radius {
    val thumbnail = 12.dp
    val card = 16.dp
    val sheet = 28.dp
    val pill = 999.dp
}

object PrivatePlayerShapes {
    val shapes = Shapes(
        extraSmall = RoundedCornerShape(Radius.thumbnail),
        small = RoundedCornerShape(Radius.thumbnail),
        medium = RoundedCornerShape(Radius.card),
        large = RoundedCornerShape(Radius.sheet),
        extraLarge = RoundedCornerShape(Radius.sheet),
    )
}

object PrivatePlayerTypography {

    private val family: FontFamily = FontFamily.Default

    val typography = Typography(
        displaySmall = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.SemiBold,
            fontSize = 34.sp,
            lineHeight = 40.sp,
            letterSpacing = (-0.4).sp,
        ),
        headlineMedium = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.SemiBold,
            fontSize = 28.sp,
            lineHeight = 34.sp,
        ),
        headlineSmall = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.SemiBold,
            fontSize = 24.sp,
            lineHeight = 30.sp,
        ),
        titleLarge = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
            lineHeight = 26.sp,
        ),
        titleMedium = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            lineHeight = 22.sp,
            letterSpacing = 0.1.sp,
        ),
        titleSmall = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        ),
        bodyLarge = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 24.sp,
        ),
        bodyMedium = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        ),
        bodySmall = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            lineHeight = 16.sp,
        ),
        labelLarge = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        ),
        labelMedium = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.2.sp,
        ),
        labelSmall = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            lineHeight = 14.sp,
        ),
    )
}

/** 供非 MaterialTheme 作用域（如播放器覆盖层）读取的间距。 */
val LocalSpacing = staticCompositionLocalOf { Spacing }

/** 统一的页面根包装：Material3 主题 + 间距提供者。 */
@Composable
fun PrivatePlayerTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = PrivatePlayerTypography.typography,
        shapes = PrivatePlayerShapes.shapes,
    ) {
        CompositionLocalProvider(LocalSpacing provides Spacing) {
            content()
        }
    }
}
