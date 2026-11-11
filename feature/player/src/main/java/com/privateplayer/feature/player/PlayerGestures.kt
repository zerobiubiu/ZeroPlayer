package com.privateplayer.feature.player

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 播放器手势事件。所有数据由 UI 层换算，不涉及播放引擎。 */
sealed interface PlayerGesture {
    data object SingleTap : PlayerGesture

    /** 双击：左半边快退、右半边快进。 */
    data class DoubleTap(val leftSide: Boolean) : PlayerGesture

    data class DragStart(val position: Offset, val size: Size) : PlayerGesture

    /** 位移使用 dp，纵向百分比仅用于音量和亮度。 */
    data class Drag(
        val dxDp: Float,
        val dyDp: Float,
        val dyFraction: Float,
        val maxAbsDxDp: Float,
        val elapsedMs: Long,
    ) : PlayerGesture

    data object DragEnd : PlayerGesture

    data object DragCancel : PlayerGesture

    data object LongPressStart : PlayerGesture

    data object LongPressEnd : PlayerGesture
}

/**
 * 播放器统一手势识别。
 *
 * 只用一个 `awaitEachGesture` 处理全部手势，避免多个 `detect*` 探测器互相抢事件：
 * - 移动超过阈值进入拖拽，按主方向判定为进度 / 音量 / 亮度；
 * - 静止超过 450ms 触发长按（临时倍速），松手结束；
 * - 未拖拽时按时间窗口区分单击与双击。
 */
@Composable
fun Modifier.playerGestures(
    enabled: Boolean = true,
    onGesture: (PlayerGesture) -> Unit,
): Modifier {
    val currentOnGesture by rememberUpdatedState(onGesture)
    return pointerInput(enabled) {
        if (!enabled) return@pointerInput

        var lastTapAt = 0L
        var pendingTapJob: kotlinx.coroutines.Job? = null
        val scope = kotlinx.coroutines.CoroutineScope(kotlin.coroutines.coroutineContext)

        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = true)
            down.consume()

            var dragging = false
            var longPressFired = false
            var totalDx = 0f
            var totalDy = 0f
            var maxAbsDx = 0f
            var finished: PointerInputChange? = null
            var quickPreviewJob: kotlinx.coroutines.Job? = null

            fun dragEvent(elapsedMs: Long) = PlayerGesture.Drag(
                dxDp = totalDx / density,
                dyDp = totalDy / density,
                dyFraction = totalDy / size.height.coerceAtLeast(1),
                maxAbsDxDp = maxAbsDx / density,
                elapsedMs = elapsedMs,
            )

            val longPressJob = scope.launch {
                delay(LONG_PRESS_TIMEOUT_MS)
                if (!dragging) {
                    pendingTapJob?.cancel()
                    pendingTapJob = null
                    lastTapAt = 0L
                    longPressFired = true
                    currentOnGesture(PlayerGesture.LongPressStart)
                }
            }

            try {
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (change.isConsumed) break
                    val delta = change.position - down.position
                    totalDx = delta.x
                    totalDy = delta.y
                    maxAbsDx = maxOf(maxAbsDx, abs(totalDx))
                    val elapsedMs = (change.uptimeMillis - down.uptimeMillis).coerceAtLeast(0L)

                    if (!dragging && !longPressFired &&
                        (abs(totalDx) > viewConfiguration.touchSlop || abs(totalDy) > viewConfiguration.touchSlop)
                    ) {
                        dragging = true
                        longPressJob.cancel()
                        pendingTapJob?.cancel()
                        pendingTapJob = null
                        lastTapAt = 0L
                        currentOnGesture(PlayerGesture.DragStart(down.position, size.toSize()))
                        if (elapsedMs <= FAST_SWIPE_MAX_MS) {
                            // 手指停住超过快滑窗口时，也要及时显示改为微调后的目标。
                            quickPreviewJob = scope.launch {
                                delay(FAST_SWIPE_MAX_MS - elapsedMs + 1L)
                                if (dragging && finished == null) {
                                    currentOnGesture(dragEvent(FAST_SWIPE_MAX_MS + 1L))
                                }
                            }
                        }
                    }
                    if (dragging) {
                        change.consume()
                        currentOnGesture(dragEvent(elapsedMs))
                    }
                    if (!change.pressed) {
                        finished = change
                        break
                    }
                }
            } finally {
                longPressJob.cancel()
                quickPreviewJob?.cancel()
                if (longPressFired) {
                    currentOnGesture(PlayerGesture.LongPressEnd)
                } else if (dragging && finished == null) {
                    currentOnGesture(PlayerGesture.DragCancel)
                }
            }

            if (longPressFired) return@awaitEachGesture
            val up = finished ?: return@awaitEachGesture
            if (dragging) {
                currentOnGesture(PlayerGesture.DragEnd)
                return@awaitEachGesture
            }

            val upPosition = up.position
            val now = up.uptimeMillis
            if (now - lastTapAt < DOUBLE_TAP_WINDOW_MS) {
                pendingTapJob?.cancel()
                pendingTapJob = null
                lastTapAt = 0L
                currentOnGesture(PlayerGesture.DoubleTap(leftSide = upPosition.x < size.width / 2f))
            } else {
                lastTapAt = now
                pendingTapJob?.cancel()
                pendingTapJob = scope.launch {
                    delay(DOUBLE_TAP_WINDOW_MS)
                    currentOnGesture(PlayerGesture.SingleTap)
                }
            }
        }
    }
}

private const val LONG_PRESS_TIMEOUT_MS = 450L
private const val DOUBLE_TAP_WINDOW_MS = 280L
