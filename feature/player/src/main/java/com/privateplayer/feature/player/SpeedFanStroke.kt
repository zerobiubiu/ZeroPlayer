package com.privateplayer.feature.player

import androidx.compose.ui.geometry.Offset
import kotlin.math.exp

/** 单次按住的瞬时输入记忆：法向投影判别意图，不记录轨迹，也不移动任何可见/可访问的档位。 */
internal class SpeedFanStroke(
    private val fan: SpeedFanGeometry,
    private val down: Offset,
    private var lastTime: Long,
    val slop: Float,
) {
    private var last = down
    private var intentional = false
    private var selected = SpeedFanGeometry.Outside
    var x = 0f
        private set
    var up = 0f
        private set

    fun sample(point: Offset, time: Long): Int {
        if (!intentional) {
            if ((point - down).getDistanceSquared() <= slop * slop) return SpeedFanGeometry.Outside
            intentional = true
        }
        val dt = (time - lastTime).coerceAtLeast(0L)
        val dx = point.x - last.x
        val dy = last.y - point.y
        // 对最近 48ms 左右的有向位移指数加权。匀速线段的解析积分不取决于把这段分成几个 MOVE；
        // 同刻样本只加入实际位移（往返相抵），而非用人为的 1ms 估算速度。
        if (dt == 0L) {
            x += dx
            up += dy
        } else {
            val keep = exp(-dt / MemoryMs).toFloat()
            val gain = ((1f - keep) * MemoryMs / dt).toFloat()
            x = x * keep + dx * gain
            up = up * keep + dy * gain
        }
        lastTime = maxOf(lastTime, time)
        last = point
        selected = fan.resolve(point, selected, this)
        return selected
    }

    private companion object { const val MemoryMs = 48.0 }
}
