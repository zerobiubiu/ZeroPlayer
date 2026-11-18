package com.privateplayer.media

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.unit.toSize
import androidx.test.platform.app.InstrumentationRegistry
import java.io.Closeable

/** 屏幕坐标，不把 Compose 根坐标假定成系统输入坐标。 */
internal fun ComposeTestRule.playerBounds(node: SemanticsNodeInteraction): Rect {
    val n = node.fetchSemanticsNode()
    return runOnIdle { Rect(n.positionOnScreen, n.size.toSize()) }
}

/** 由系统分发的一根手指。沿用同一 downTime；异常退出时发 CANCEL 而非触发提交的 UP。 */
internal class PlayerSystemTouch(private var at: Offset) : Closeable {
    private val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
    private val downTime = SystemClock.uptimeMillis()
    private var active = true

    init { send(MotionEvent.ACTION_DOWN) }

    fun moveTo(point: Offset) {
        at = point
        send(MotionEvent.ACTION_MOVE)
    }

    /** 按实际单调时钟排一串 MOVE 和紧随的 UP。异步系统投递，途中不等 Compose/引擎/下一绘制帧。 */
    fun burstAndUp(points: List<Offset>, intervalMs: Long): LongArray {
        require(points.isNotEmpty() && intervalMs > 0)
        val times = LongArray(points.size + 1)
        val start = SystemClock.uptimeMillis()
        for ((i, point) in points.withIndex()) {
            val delay = start + i * intervalMs - SystemClock.uptimeMillis()
            if (delay > 0) SystemClock.sleep(delay)
            at = point
            times[i] = send(MotionEvent.ACTION_MOVE, synchronous = false) - downTime
        }
        times[points.size] = send(MotionEvent.ACTION_UP, synchronous = false) - downTime
        active = false
        return times
    }

    /** 同一触摸流加入并释放 id=1；id=0 全程保持按下，最后由调用者抬起。 */
    fun tapSecond(point: Offset) {
        check(active)
        val properties = Array(2) { index ->
            MotionEvent.PointerProperties().apply { id = index; toolType = MotionEvent.TOOL_TYPE_FINGER }
        }
        val coordinates = arrayOf(at, point).map {
            MotionEvent.PointerCoords().apply { x = it.x; y = it.y; pressure = 1f; size = 1f }
        }.toTypedArray()
        for (action in intArrayOf(MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_POINTER_UP)) {
            val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(),
                action or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 2, properties, coordinates,
                0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
            try { check(automation.injectInputEvent(event, true)) } finally { event.recycle() }
        }
    }

    fun up() { send(MotionEvent.ACTION_UP); active = false }

    override fun close() {
        if (active) { send(MotionEvent.ACTION_CANCEL); active = false }
    }

    private fun send(action: Int, synchronous: Boolean = true): Long {
        check(active)
        val time = SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(downTime, time, action, at.x, at.y, 0)
        try {
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            check(automation.injectInputEvent(event, synchronous)) { "系统未投递 ${MotionEvent.actionToString(action)} 到 $at" }
            return time
        } finally { event.recycle() }
    }
}

internal fun tapPlayerScreen(at: Offset, double: Boolean = false) {
    repeat(if (double) 2 else 1) { i ->
        if (i > 0) SystemClock.sleep(65)
        PlayerSystemTouch(at).use { finger -> SystemClock.sleep(12); finger.up() }
    }
}
