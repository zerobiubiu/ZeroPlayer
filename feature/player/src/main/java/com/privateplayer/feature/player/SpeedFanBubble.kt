package com.privateplayer.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.privateplayer.core.design.theme.Radius
import com.privateplayer.core.design.theme.Spacing
import com.privateplayer.domain.model.PlaybackSpeedLimits
import kotlin.math.roundToInt

/** 只在放置阶段读取手指位置，不因同档移动重排文字、建立 Popup、抢焦点或拦截指针。 */
@Composable
internal fun SpeedFanBubble(state: PlayerSpeedControlState, fan: SpeedFanGeometry, safeOrigin: Offset, index: Int) {
    val type = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum", color = PlayerTokens.Text)
    val measure = rememberTextMeasurer()
    val longest = speedLabel(PlaybackSpeedLimits.MIN)
    val textWidth = remember(measure, longest, type) { measure.measure(longest, type, maxLines = 1).size.width }
    val width = with(LocalDensity.current) { textWidth.toDp() } + Spacing.md * 2
    val shape = RoundedCornerShape(Radius.thumbnail)
    Layout(modifier = Modifier.fillMaxSize(), content = {
        Box(Modifier.width(width).heightIn(min = 36.dp)
            .semantics(mergeDescendants = true) { testTag = "speedFanBubble"; hideFromAccessibility() }
            .background(PlayerTokens.SpeedKeyContainer, shape).border(1.dp, PlayerTokens.Accent, shape)
            .padding(vertical = Spacing.xs), contentAlignment = Alignment.Center) {
            BasicText(speedLabel(SpeedPresets[index]), style = type, maxLines = 1, softWrap = false)
        }
    }) { nodes, bounds ->
        val key = nodes.single().measure(Constraints(maxWidth = bounds.maxWidth, maxHeight = bounds.maxHeight))
        val viewport = IntSize(bounds.maxWidth, bounds.maxHeight)
        val keySize = IntSize(key.width, key.height)
        val origin = fan.topLeft - safeOrigin
        val labels = Array(SpeedPresets.size) { fan.labelBounds(it).translate(origin) }
        val hud = state.bubble.hudInRoot
        val dock = SpeedBubbleDock(viewport, keySize, labels[index].center, if (hud.isEmpty) Rect.Zero else hud.translate(-safeOrigin),
            Spacing.xl.toPx(), Spacing.xs.toPx(), PlayerTokens.SpeedBubbleLift.toPx(), PlayerTokens.MinTouchTarget.toPx(), labels)
        val fanOffset = IntOffset(origin.x.roundToInt(), origin.y.roundToInt())
        layout(viewport.width, viewport.height) {
            val pos = dock.position(state.fingerInRoot - safeOrigin)
            state.bubble.placeInFan(pos - fanOffset, keySize)
            key.place(pos.x, pos.y)
        }
    }
}

/** 优先指尖上方；上方无空间时试较空一侧、下方。即便是小窗，键帽整体也不出安全边。 */
internal fun speedBubblePosition(finger: Offset, area: IntSize, key: IntSize, lift: Float, gap: Float): IntOffset {
    val maxX = (area.width - key.width).coerceAtLeast(0).toFloat()
    val maxY = (area.height - key.height).coerceAtLeast(0).toFloat()
    var x = finger.x - key.width / 2f
    var y = finger.y - maxOf(lift, gap + key.height / 2f) - key.height / 2f
    if (y < 0f) {
        val right = area.width - finger.x >= finger.x
        val side = if (right) finger.x + gap else finger.x - gap - key.width
        if (side in 0f..maxX || finger.y + gap > maxY) {
            x = side
            y = finger.y - key.height / 2f
        } else y = finger.y + gap
    }
    return IntOffset(x.coerceIn(0f, maxX).roundToInt(), y.coerceIn(0f, maxY).roundToInt())
}
