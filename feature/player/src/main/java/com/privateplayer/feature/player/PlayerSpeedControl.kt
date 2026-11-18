package com.privateplayer.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.privateplayer.core.design.theme.Spacing
import kotlin.math.roundToInt

internal enum class SpeedPickerPhase { Closed, Pressed, Open, Sliding }

internal enum class SpeedHaptic { LongPress, Tick }

/** 选速板只拥有一次按住手势；会话发放的试播句柄把更改绑定到按下时的媒体。 */
@Stable
internal class PlayerSpeedControlState(
    private val beginPreview: () -> SpeedPreview?,
    private val hapticsEnabled: () -> Boolean,
    private val haptic: (SpeedHaptic) -> Unit,
) {
    var phase by mutableStateOf(SpeedPickerPhase.Closed)
        private set
    val active get() = phase != SpeedPickerPhase.Closed
    val expanded get() = phase == SpeedPickerPhase.Open || phase == SpeedPickerPhase.Sliding
    var hover by mutableIntStateOf(SpeedFanGeometry.Outside)
        private set
    val buttonFocus = FocusRequester()
    val bubble = SpeedBubbleUnderlay()
    var anchor by mutableStateOf(Rect.Zero)
    var geometry: SpeedFanGeometry? = null
        set(value) {
            if (field !== value && phase == SpeedPickerPhase.Sliding) dismiss()
            field = value
        }
    var preview: SpeedPreview? = null
        private set
    private var stroke: SpeedFanStroke? = null
    private var fingerX by mutableFloatStateOf(0f)
    private var fingerY by mutableFloatStateOf(0f)
    val fingerInRoot get() = Offset(fingerX, fingerY)

    fun beginPress() { if (!active) phase = SpeedPickerPhase.Pressed }
    fun open() { if (!expanded) phase = SpeedPickerPhase.Open }

    fun beginSlide(down: Offset, downTime: Long, touchSlop: Float) {
        if (phase != SpeedPickerPhase.Pressed) return
        val fan = geometry ?: return dismiss()
        preview = beginPreview() ?: return dismiss()
        stroke = SpeedFanStroke(fan, anchor.topLeft + down - fan.topLeft, downTime, touchSlop)
        phase = SpeedPickerPhase.Sliding
        if (hapticsEnabled()) haptic(SpeedHaptic.LongPress)
    }

    fun track(position: Offset, time: Long, released: Boolean = false) {
        val held = preview ?: return
        if (held.revoked.value) return dismiss()
        val fan = geometry ?: return dismiss()
        val root = anchor.topLeft + position
        val next = stroke?.sample(root - fan.topLeft, time) ?: return dismiss()
        if (next >= SpeedFanGeometry.Origin && !released) {
            fingerX = root.x
            fingerY = root.y
        }
        if (next != hover) {
            hover = next
            if (next >= SpeedFanGeometry.Origin && hapticsEnabled()) haptic(SpeedHaptic.Tick)
        } else if (!released) return
        // 尚未越过起点 slop、或已离开真实扇面，才保留精确基线。主动返回方块就是普通的 1×。
        held.update(if (next >= SpeedFanGeometry.Origin) SpeedPresets[next] else held.originalSpeed, finish = released)
        if (released) close()
    }

    fun tap(index: Int) {
        if (phase != SpeedPickerPhase.Open) return
        if (index in SpeedPresets.indices) beginPreview()?.update(SpeedPresets[index], finish = true)
        close()
    }

    fun dismiss() {
        preview?.cancel()
        close()
    }

    private fun close() {
        preview = null
        stroke = null
        bubble.hudInRoot = Rect.Zero
        fingerX = 0f
        fingerY = 0f
        hover = SpeedFanGeometry.Outside
        phase = SpeedPickerPhase.Closed
    }
}

@Composable
internal fun speedLabel(speed: Float): String =
    stringResource(R.string.player_speed_format, remember(speed) { speed.toString().removeSuffix(".0") })

@Composable
internal fun PlayerSpeedButton(speed: Float, state: PlayerSpeedControlState, modifier: Modifier) {
    val name = stringResource(R.string.player_speed_sheet_title)
    val fullLabel = speedLabel(speed)
    val rounded = (speed * 100).roundToInt() / 100f
    val label = if (rounded == speed) fullLabel else stringResource(R.string.player_speed_approximate, speedLabel(rounded))
    val type = MaterialTheme.typography.labelLarge
    val fit = remember(type.fontSize) { TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = type.fontSize, stepSize = 1.sp) }
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier.heightIn(min = PlayerTokens.MinTouchTarget)
            .onGloballyPositioned { state.anchor = it.boundsInRoot() }
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = name
                stateDescription = fullLabel
                onClick { state.open(); true }
            }
            .focusRequester(state.buttonFocus)
            .onFocusChanged { focused = it.isFocused }
            .onSpeedActivate(state::open)
            .focusable()
            .speedPointerInput(state),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.fillMaxWidth().heightIn(min = PlayerTokens.SpeedPillHeight)
                .background(if (state.active) PlayerTokens.TrackInactive else PlayerTokens.PillContainer, CircleShape)
                .then(if (focused) Modifier.border(Spacing.xxs, PlayerTokens.Text, CircleShape) else Modifier)
                .padding(horizontal = Spacing.xs, vertical = Spacing.xxs),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                text = label,
                style = type.copy(color = PlayerTokens.Text, fontFeatureSettings = "tnum", textAlign = TextAlign.Center),
                maxLines = 1, softWrap = false, autoSize = fit,
            )
        }
    }
}

internal fun Modifier.onSpeedActivate(action: () -> Unit): Modifier = onKeyEvent {
    when (it.key) {
        Key.Enter, Key.NumPadEnter, Key.DirectionCenter, Key.Spacebar -> {
            if (it.type == KeyEventType.KeyUp) action()
            true
        }
        else -> false
    }
}

/** 命中链在按钮 DOWN 时确定；展开同窗口的面板后仍只读取这根指针，不把首个 UP 派给菜单。 */
private fun Modifier.speedPointerInput(state: PlayerSpeedControlState): Modifier = pointerInput(state) {
    awaitEachGesture {
        val down = awaitFirstDown()
        down.consume()
        if (state.active) return@awaitEachGesture
        state.beginPress()
        try {
            val tap = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) { awaitShortRelease(down) }
            if (state.phase != SpeedPickerPhase.Pressed) return@awaitEachGesture
            when (tap) {
                true -> state.open()
                false -> return@awaitEachGesture
                null -> {
                    state.beginSlide(down.position, down.uptimeMillis, viewConfiguration.touchSlop)
                    while (true) {
                        val event = awaitPointerEvent()
                        if (state.phase != SpeedPickerPhase.Sliding) break
                        val change = event.changes.singleOrNull() ?: break
                        if (change.id != down.id || change.isConsumed) break
                        val released = change.changedToUp()
                        change.consume()
                        state.track(change.position, change.uptimeMillis, released)
                        if (released) break
                    }
                }
            }
        } finally {
            if (state.phase == SpeedPickerPhase.Pressed || state.phase == SpeedPickerPhase.Sliding) state.dismiss()
        }
    }
}

private suspend fun AwaitPointerEventScope.awaitShortRelease(down: PointerInputChange): Boolean {
    val slop = viewConfiguration.touchSlop
    while (true) {
        val change = awaitPointerEvent().changes.singleOrNull() ?: return false
        // DOWN 在胶囊内即可；边缘的小幅手抖也按同一平台 slop 判断，不因越过按钮矩形而中断。
        if (change.id != down.id || change.isConsumed ||
            (change.position - down.position).getDistanceSquared() > slop * slop
        ) return false
        val released = change.changedToUp()
        change.consume()
        if (released) return true
    }
}
