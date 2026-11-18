package com.privateplayer.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.privateplayer.core.design.theme.Radius
import com.privateplayer.core.design.theme.Spacing
import kotlin.math.roundToInt
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** 先看到倍率，其次是已选键帽与指下试选；不在视频上铺设整屏遮罩。 */
@Composable
internal fun PlayerSpeedMenuHost(
    state: PlayerSpeedControlState,
    speed: Float,
    feedback: @Composable (candidate: Int) -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    val input = LocalInputModeManager.current
    var wasOpen by remember(state) { mutableStateOf(false) }
    LaunchedEffect(state, state.expanded) {
        if (!state.expanded && wasOpen && input.inputMode == InputMode.Keyboard) state.buttonFocus.requestFocus()
        wasOpen = state.expanded
    }
    val held = if (state.phase == SpeedPickerPhase.Sliding) state.preview else null
    val revoked = held != null && held.revoked.collectAsStateWithLifecycle().value
    val candidate = if (held != null && !revoked) state.hover else SpeedFanGeometry.Outside
    if (revoked) SideEffect { if (state.preview === held) state.dismiss() }
    Box(Modifier.fillMaxSize().pointerInput(state) {
        // 第二根手指可能命中另一个子节点；根在子节点之前终止并吞掉多指剩余事件。
        awaitPointerEventScope {
            var cancelled = false
            while (true) {
                val changes = awaitPointerEvent(PointerEventPass.Initial).changes
                if (!cancelled && changes.size > 1 && state.active) {
                    state.dismiss()
                    cancelled = true
                }
                if (cancelled) {
                    changes.forEach { it.consume() }
                    if (changes.none { it.pressed }) cancelled = false
                }
            }
        }
    }) {
        val background = if (state.expanded) Modifier.clearAndSetSemantics {} else Modifier
        Box(Modifier.matchParentSize().then(background), content = content)
        if (state.expanded) {
            Box(Modifier.matchParentSize().pointerInput(state) { detectTapGestures { state.dismiss() } })
        }
        val density = LocalDensity.current
        var safe by remember { mutableStateOf(Rect.Zero) }
        Box(
            Modifier.matchParentSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(Spacing.xs)
                .onGloballyPositioned { safe = it.boundsInRoot() },
            contentAlignment = AbsoluteAlignment.TopLeft,
        ) {
            val fan = remember(state.anchor, safe, density.density) {
                SpeedFanGeometry.fit(state.anchor, safe, density.density)
            }
            // 关闭时也完成测量：长按成立后的第一个 MOVE 无须等待标签布局。
            SideEffect { state.geometry = fan }
            if (fan != null && state.expanded && !revoked) {
                SpeedFan(state, held?.originalSpeed ?: speed, fan,
                    Modifier.absoluteOffset { (fan.topLeft - safe.topLeft).toIntOffset() }.size(with(density) { fan.radius.toDp() })
                        .then(if (candidate >= SpeedFanGeometry.Origin) state.bubble.cutout else Modifier))
                if (candidate >= SpeedFanGeometry.Origin) SpeedFanBubble(state, fan, safe.topLeft, candidate)
            }
        }
        feedback(candidate)
    }
}

@Composable
private fun SpeedFan(state: PlayerSpeedControlState, speed: Float, fan: SpeedFanGeometry, modifier: Modifier) {
    val title = stringResource(R.string.player_speed_sheet_title)
    val close = stringResource(R.string.player_speed_close)
    val preview = stringResource(R.string.player_speed_preview)
    val selectedIndex = SpeedPresets.indexOfFirst { it == speed }
    val customBaseline = if (selectedIndex < 0) stringResource(R.string.player_speed_current, speedLabel(speed)) else null
    val hasCandidate = state.hover >= SpeedFanGeometry.Origin
    val entryIndex = remember { maxOf(SpeedFanGeometry.Origin, selectedIndex) }
    val entryFocus = remember { FocusRequester() }
    val inputMode = LocalInputModeManager.current.inputMode
    val density = LocalDensity.current
    val style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum", textAlign = TextAlign.Center,
        lineHeight = TextUnit.Unspecified, letterSpacing = 0.sp, color = PlayerTokens.Text)
    val floor = with(density) { minOf(9.sp.toPx(), 10.dp.toPx()).toSp() }
    val fit = remember(style.fontSize, floor) { TextAutoSize.StepBased(minFontSize = floor, maxFontSize = style.fontSize, stepSize = 0.5.sp) }
    val keyShape = remember { RoundedCornerShape(Radius.thumbnail) }
    Box(
        modifier.semantics {
            paneTitle = title
            if (customBaseline != null && hasCandidate) stateDescription = customBaseline
            dismiss(close) { state.dismiss(); true }
        }
            .onPreviewKeyEvent {
                if (it.key != Key.Escape) false else {
                    if (it.type == KeyEventType.KeyUp) state.dismiss()
                    true
                }
            }
            .focusProperties { onExit = { cancelFocusChange() } }.focusGroup().selectableGroup()
            .pointerInput(state, fan) { detectTapGestures { state.tap(fan.hit(it)) } },
        contentAlignment = AbsoluteAlignment.TopLeft,
    ) {
        FanSurface(fan, state.hover, Modifier.matchParentSize())
        SpeedPresets.forEachIndexed { index, rate ->
            val bounds = fan.labelBounds(index)
            val chosen = index == selectedIndex
            val auditioning = index == state.hover
            var focused by remember { mutableStateOf(false) }
            val outline = when {
                focused -> Modifier.border(Spacing.xxs, PlayerTokens.Text, keyShape)
                chosen -> Modifier.border(1.dp, PlayerTokens.TextSecondary, keyShape)
                else -> Modifier
            }
            Box(
                Modifier.absoluteOffset { bounds.topLeft.toIntOffset() }
                    .size(with(density) { bounds.width.toDp() }, with(density) { bounds.height.toDp() })
                    .then(if (index == entryIndex) Modifier.focusRequester(entryFocus) else Modifier)
                    .onFocusChanged { focused = it.isFocused }
                    .then(outline)
                    .semantics(mergeDescendants = true) {
                        role = Role.RadioButton
                        selected = chosen
                        if (auditioning) stateDescription = preview
                        onClick { state.tap(index); true }
                    }
                    .onSpeedActivate { state.tap(index) }.focusable(),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(speedLabel(rate), style = style.copy(textDecoration = if (auditioning) TextDecoration.Underline else null),
                    maxLines = 1, softWrap = false, autoSize = fit)
            }
        }
        if (customBaseline != null && !hasCandidate) {
            BasicText(customBaseline,
                modifier = Modifier.align(AbsoluteAlignment.TopRight).widthIn(max = with(density) { (fan.radius * 0.48f).toDp() })
                    .background(PlayerTokens.SpeedKeyContainer, RoundedCornerShape(Radius.thumbnail))
                    .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                style = style.copy(color = PlayerTokens.Text), maxLines = 1, softWrap = false, autoSize = fit)
        }
    }
    LaunchedEffect(inputMode) { if (inputMode == InputMode.Keyboard) entryFocus.requestFocus() }
}

private fun Offset.toIntOffset() = IntOffset(x.roundToInt(), y.roundToInt())
