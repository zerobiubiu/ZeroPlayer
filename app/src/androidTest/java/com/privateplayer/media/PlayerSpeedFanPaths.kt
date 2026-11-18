package com.privateplayer.media

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** 只用屏上实际面板/标签测量：2.5×、4× 同环，4×、5× 同扇。没有生产 hit、档位表或阈值的依赖。 */
internal class PlayerSpeedFanPaths(private val pane: Rect, cells: Map<Float, Rect>, private val dp: Float) {
    data class Point(val stage: String, val at: Offset, val allowed: Set<Float>)

    private val vertex = pane.bottomLeft
    private fun radius(center: Offset) = (center - vertex).getDistance()
    private fun angle(center: Offset) = atan2(center.x - vertex.x, vertex.y - center.y)
    private val rMid = radius(cells.getValue(4f).center)
    private val rOuter = radius(cells.getValue(5f).center)
    private val boundaryR = (rMid + rOuter) / 2
    private val aLeft = angle(cells.getValue(2.5f).center)
    private val aRight = angle(cells.getValue(4f).center)
    private val boundaryA = (aLeft + aRight) / 2
    private fun polar(r: Float, a: Float) = Offset(vertex.x + r * sin(a), vertex.y - r * cos(a))

    /** 固定在中环：从第一扇连续扫到第四扇，再折回第三扇中线抬手。 */
    fun burst(): List<Offset> = listOf(14, 22, 31, 43, 54, 64, 74, 79, 74, 64, 56)
        .map { degrees -> polar(rMid, (degrees * PI / 180).toFloat()) }

    // 这个点既不是角落之外，也不是标签中心：恰在外弧外侧 2dp，且仍在面板外接正方形内。
    val outsideArc = polar(pane.width + 2 * dp, (PI / 4).toFloat())

    /** 确定种子只扰动测量边界两侧；慢跨界容许一次 A→B，但不容许中间第三档或倒跳。 */
    fun trace(seed: Int, samples: Int): List<Point> = buildList {
        val random = Random(seed)
        fun put(stage: String, p: Offset, vararg allowed: Float) { add(Point(stage, p, allowed.toSet())) }
        fun jitter(i: Int): Float = (if (i % 2 == 0) 1 else -1) * (0.45f + 0.45f * random.nextFloat()) * dp
        fun edge(r: Float, tangent: Float) = polar(r, boundaryA + tangent / r)
        put("角界入", polar(rMid, aLeft), 2.5f)
        repeat(samples) { i -> put("角界颤动", edge(rMid, jitter(i)), 2.5f) }
        for (i in 1..samples) put("慢跨角界", edge(rMid, 10 * dp * i / samples), 2.5f, 4f)
        put("角界抵达", edge(rMid, 10 * dp), 4f)
        repeat(samples) { i -> put("环界颤动", polar(boundaryR + jitter(i), aRight), 4f) }
        put("环界抵达", polar(boundaryR + 10 * dp, aRight), 5f)
        repeat(samples) { i -> put("交线颤动", edge(boundaryR + jitter(i), jitter(i / 2)), 5f) }
        put("斜跨交线", edge(boundaryR - 10 * dp, -10 * dp), 2.5f)
        put("跨多档", polar(rOuter, (78.75 * PI / 180).toFloat()), 8f)
        put("马上折返", polar(rMid, aLeft), 2.5f)
        put("外弧内缘", polar(pane.width - 2 * dp, aRight), 5f)
        put("外弧外缘", polar(pane.width + 2 * dp, aRight), 2f)
        put("外弧再入", polar(pane.width - 2 * dp, aRight), 5f)
    }
}
