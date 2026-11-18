package com.privateplayer.feature.player

import android.view.ViewConfiguration
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 播放器手势事件。所有数据由 UI 层换算，不涉及播放引擎。 */
sealed interface PlayerGesture {
    data object SingleTap : PlayerGesture

    /** 按触摸层的物理横坐标分区，不随语言方向或视频画面比例翻转。 */
    enum class DoubleTap : PlayerGesture { Rewind, PlayPause, Forward }

    data class DragStart(val position: Offset, val size: Size) : PlayerGesture

    /** 位移使用 dp，纵向百分比仅用于音量和亮度。 */
    data class Drag(
        val dxDp: Float,
        val dyDp: Float,
        val dyFraction: Float,
        val elapsedMs: Long,
    ) : PlayerGesture

    data object DragEnd : PlayerGesture

    data object DragCancel : PlayerGesture

    data object LongPressStart : PlayerGesture

    data object LongPressEnd : PlayerGesture
}

/** 竖屏为三等分，直到 2:1 连续拓宽中央区为 40%，更宽时不再变化。 */
internal fun doubleTapZone(x: Float, width: Int, height: Int): PlayerGesture.DoubleTap {
    val blend = (width.toFloat() / height.coerceAtLeast(1) - 1f).coerceIn(0f, 1f)
    val portraitSide = width / 3f
    val wideSide = width * 0.30f
    val side = portraitSide + (wideSide - portraitSide) * blend
    return when {
        x < side -> PlayerGesture.DoubleTap.Rewind
        x < width - side -> PlayerGesture.DoubleTap.PlayPause
        else -> PlayerGesture.DoubleTap.Forward
    }
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
    val context = LocalContext.current
    val maxDistanceSquared = remember(context, LocalDensity.current) {
        val slop = ViewConfiguration.get(context).scaledDoubleTapSlop.toFloat()
        slop * slop
    }
    return pointerInput(enabled, maxDistanceSquared) {
        if (!enabled) return@pointerInput

        var firstUpAt = 0L
        var firstPosition = Offset.Zero
        var firstSize = IntSize.Zero
        var pendingTapJob: kotlinx.coroutines.Job? = null
        val scope = kotlinx.coroutines.CoroutineScope(kotlin.coroutines.coroutineContext)

        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = true)
            down.consume()
            val zone = doubleTapZone(down.position.x, size.width, size.height)
            // 二次按下时领取仍待定的首击，而非在两次抬手间用旧时间戳猜测。
            val pending = pendingTapJob
            val isDouble = pending?.isActive == true && firstSize == size &&
                down.uptimeMillis - firstUpAt in viewConfiguration.doubleTapMinTimeMillis..viewConfiguration.doubleTapTimeoutMillis &&
                (down.position - firstPosition).getDistanceSquared() <= maxDistanceSquared &&
                zone == doubleTapZone(firstPosition.x, firstSize.width, firstSize.height)
            if (pending != null) {
                pending.cancel()
                pendingTapJob = null
                if (!isDouble) currentOnGesture(PlayerGesture.SingleTap)
            }

            var dragging = false
            var longPressFired = false
            var totalDx = 0f
            var totalDy = 0f
            var finished: PointerInputChange? = null

            fun dragEvent(elapsedMs: Long) = PlayerGesture.Drag(
                dxDp = totalDx / density,
                dyDp = totalDy / density,
                dyFraction = totalDy / size.height.coerceAtLeast(1),
                elapsedMs = elapsedMs,
            )

            val longPressJob = scope.launch {
                delay(LONG_PRESS_TIMEOUT_MS)
                if (!dragging) {
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
                    val elapsedMs = (change.uptimeMillis - down.uptimeMillis).coerceAtLeast(0L)

                    if (!dragging && !longPressFired &&
                        (abs(totalDx) > viewConfiguration.touchSlop || abs(totalDy) > viewConfiguration.touchSlop)
                    ) {
                        dragging = true
                        longPressJob.cancel()
                        currentOnGesture(PlayerGesture.DragStart(down.position, size.toSize()))
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

            if (isDouble) {
                currentOnGesture(zone)
            } else {
                firstPosition = up.position
                firstSize = size
                firstUpAt = up.uptimeMillis
                pendingTapJob = scope.launch {
                    delay(viewConfiguration.doubleTapTimeoutMillis)
                    pendingTapJob = null
                    currentOnGesture(PlayerGesture.SingleTap)
                }
            }
        }
    }
}

private const val LONG_PRESS_TIMEOUT_MS = 450L
