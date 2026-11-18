package com.privateplayer.feature.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import com.privateplayer.core.design.theme.Spacing
import kotlin.math.ceil

/** 两种长按共用。数字总是立即显示已确认的精确速度，只有旁边的速率符号做一次短行进。 */
@Composable
internal fun PlayerTemporarySpeedHud(rate: Float, describedByFan: Boolean, modifier: Modifier) {
    val arrival = remember { Animatable(1f) }
    LaunchedEffect(rate) {
        if (coroutineContext[MotionDurationScale]?.scaleFactor == 0f) arrival.snapTo(1f) else {
            arrival.snapTo(0f)
            arrival.animateTo(1f, tween(150, easing = LinearOutSlowInEasing))
        }
    }
    Row(modifier.semantics(mergeDescendants = true) {
        testTag = "temporarySpeedHud"
        if (describedByFan) hideFromAccessibility()
    }.background(PlayerTokens.SpeedKeyContainer, CircleShape).padding(horizontal = Spacing.md, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(Spacing.lg)) {
            val travel = (arrival.value - 1f) * size.width * 0.16f
            val count = if (rate > 1f) 2 else 1
            val stroke = 2.dp.toPx()
            for (i in 0 until count) {
                val start = size.width * ((if (count == 1) 0.34f else 0.18f) + i * 0.37f) + travel
                val tip = Offset(start + size.width * 0.26f, center.y)
                drawLine(PlayerTokens.Accent, Offset(start, size.height * 0.25f), tip, stroke, StrokeCap.Round)
                drawLine(PlayerTokens.Accent, tip, Offset(start, size.height * 0.75f), stroke, StrokeCap.Round)
            }
        }
        BasicText(stringResource(R.string.player_gesture_temp_speed, speedLabel(rate)), maxLines = 1, softWrap = false,
            style = MaterialTheme.typography.titleSmall.copy(color = PlayerTokens.Text, fontFeatureSettings = "tnum"))
    }
}

/** 小窗仍保持视频 1/4 高度；只有默认位置真压住当前扇面的键位、且右侧确有空间才水平让位。 */
internal fun SpeedFanGeometry.speedHudLeft(
    centered: Int, y: Int, width: Int, height: Int, safeRight: Int, gap: Int, hostOrigin: Offset,
): Int {
    val x = hostOrigin.x + centered - topLeft.x
    val top = hostOrigin.y + y - topLeft.y
    if (x >= radius || x + width <= 0f || top >= radius || top + height <= 0f) return centered
    val beside = ceil(topLeft.x + radius - hostOrigin.x).toInt() + gap
    if (beside + width > safeRight) return centered
    for (i in SpeedPresets.indices) {
        val key = labelBounds(i)
        if (x < key.right && x + width > key.left && top < key.bottom && top + height > key.top) return beside
    }
    return centered
}
