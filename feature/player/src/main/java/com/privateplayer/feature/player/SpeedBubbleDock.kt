package com.privateplayer.feature.player

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

/** 键帽与指腹、HUD 的间隔是硬约束。以静态键中心选一次避字方向，同档 MOVE 不重新择边。 */
internal class SpeedBubbleDock(
    private val area: IntSize,
    private val key: IntSize,
    private val center: Offset,
    private val hud: Rect,
    private val fingerGap: Float,
    private val seam: Float,
    lift: Float,
    maxDetour: Float,
    labels: Array<Rect>,
) {
    private val maxX = (area.width - key.width).coerceAtLeast(0).toFloat()
    private val maxY = (area.height - key.height).coerceAtLeast(0).toFloat()
    private val offset: Offset

    init {
        val above = speedBubblePosition(center, area, key, lift, fingerGap)
        val preferred = keepClear(center, above.x.toFloat(), above.y.toFloat())
        val px = preferred.x.toFloat()
        val py = preferred.y.toFloat()
        // 两个轴的临界点只有「原位置、障碍物左右/上下边」。只在测量时求最近可放点，
        // 避字代价过大则保持贴手，由扇层的同形挖空保证不会透出另一档数字。
        val xs = FloatArray(1 + labels.size * 2)
        val ys = FloatArray(xs.size)
        xs[0] = px; ys[0] = py
        labels.forEachIndexed { i, rect ->
            xs[1 + i * 2] = (rect.left - seam - key.width).coerceIn(0f, maxX)
            xs[2 + i * 2] = (rect.right + seam).coerceIn(0f, maxX)
            ys[1 + i * 2] = (rect.top - seam - key.height).coerceIn(0f, maxY)
            ys[2 + i * 2] = (rect.bottom + seam).coerceIn(0f, maxY)
        }
        var best = maxDetour * maxDetour
        var bestX = px
        var bestY = py
        search@ for (x in xs) for (y in ys) {
            val dx = x - px; val dy = y - py
            val cost = dx * dx + dy * dy
            if (cost >= best || !fingerClear(center, x, y) || !hudClear(x, y)) continue
            if (labels.any { overlaps(x, y, it, seam) }) continue
            best = cost; bestX = x; bestY = y
            if (cost == 0f) break@search
        }
        offset = Offset(bestX - center.x, bestY - center.y)
    }

    fun position(finger: Offset): IntOffset = keepClear(finger, finger.x + offset.x, finger.y + offset.y)

    private fun overlaps(x: Float, y: Float, r: Rect, margin: Float): Boolean =
        x < r.right + margin && x + key.width > r.left - margin && y < r.bottom + margin && y + key.height > r.top - margin

    private fun hudClear(x: Float, y: Float) = hud.isEmpty || !overlaps(x, y, hud, seam)
    private fun fingerClear(p: Offset, x: Float, y: Float) =
        x >= p.x + fingerGap || x + key.width <= p.x - fingerGap || y >= p.y + fingerGap || y + key.height <= p.y - fingerGap

    private fun keepClear(p: Offset, requestedX: Float, requestedY: Float): IntOffset {
        val px = requestedX.coerceIn(0f, maxX)
        val py = requestedY.coerceIn(0f, maxY)
        if (fingerClear(p, px, py) && hudClear(px, py)) return IntOffset(px.roundToInt(), py.roundToInt())
        var best = Float.POSITIVE_INFINITY
        var leastFingerOverlap = Float.POSITIVE_INFINITY
        var leastHudOverlap = Float.POSITIVE_INFINITY
        var bestX = px
        var bestY = py
        // 正常同档移动只走上面的 O(1) 快路。挤到边缘时，考虑实际 HUD 和指腹的边，
        // 最多 5×5 个标量位置，不建立矩形/数组，也不重算扇形路径。
        val count = if (hud.isEmpty) 3 else 5
        for (i in 0 until count) {
            val x = when (i) {
                0 -> px; 1 -> p.x - fingerGap - key.width; 2 -> p.x + fingerGap
                3 -> hud.left - seam - key.width; else -> hud.right + seam
            }.coerceIn(0f, maxX)
            for (j in 0 until count) {
                val y = when (j) {
                    0 -> py; 1 -> p.y - fingerGap - key.height; 2 -> p.y + fingerGap
                    3 -> hud.top - seam - key.height; else -> hud.bottom + seam
                }.coerceIn(0f, maxY)
                val fingerOverlap = (fingerGap - maxOf(maxOf(x - p.x, p.x - x - key.width),
                    maxOf(y - p.y, p.y - y - key.height))).coerceAtLeast(0f)
                val hudOverlap = if (hud.isEmpty) 0f else minOf(
                    minOf(x + key.width - hud.left + seam, hud.right + seam - x),
                    minOf(y + key.height - hud.top + seam, hud.bottom + seam - y),
                ).coerceAtLeast(0f)
                val dx = x - px; val dy = y - py
                val cost = dx * dx + dy * dy
                if (fingerOverlap < leastFingerOverlap || fingerOverlap == leastFingerOverlap &&
                    (hudOverlap < leastHudOverlap || hudOverlap == leastHudOverlap && cost < best)) {
                    leastFingerOverlap = fingerOverlap; leastHudOverlap = hudOverlap
                    best = cost; bestX = x; bestY = y
                }
            }
        }
        return IntOffset(bestX.roundToInt(), bestY.roundToInt())
    }
}
