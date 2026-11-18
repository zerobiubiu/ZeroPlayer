package com.privateplayer.feature.player

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.privateplayer.domain.model.PlaybackSpeedLimits
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt

/** 方形原点，其后依次是从竖边到横边的四扇；每扇按由近到远排列。 */
internal val SpeedPresets = floatArrayOf(
    1f, 0.75f, 0.5f, PlaybackSpeedLimits.MIN, 2f, 2.5f, 3f, 3.5f, 4f, 5f, 6f, 7f, PlaybackSpeedLimits.MAX,
)

/** 绘图、标签布局、轻点和跨控件的滑选全用同一把极坐标尺。局部坐标的顶点在左下。 */
internal class SpeedFanGeometry private constructor(
    val topLeft: Offset,
    val radius: Float,
    private val originSize: Float,
    private val density: Float,
) {
    val origin = Rect(0f, radius - originSize, originSize, radius)
    private val firstRadius = maxOf(originSize * sqrt(2f), radius * 0.36f)
    private val band = (radius - firstRadius) / Rings
    private val labels = Array(SpeedPresets.size) { index ->
        if (index == Origin) return@Array origin
        val sector = sector(index)
        val r = firstRadius + (ring(index) + 0.5f) * band
        val axis = midpoints[sector]
        val center = Offset(r * axis.x, radius - r * axis.y)
        // 水平文字盒完全落在本扇内；即便相邻的标签都获得焦点装饰，也不侵入对方。
        val halfHeight = minOf(band * 0.27f, r * 0.12f)
        val up = radius - center.y
        val outer = outerRadius(ring(index))
        val inner = innerRadius(ring(index))
        var halfWidth = minOf(center.x, sqrt((outer * outer - (up + halfHeight) * (up + halfHeight)).coerceAtLeast(0f)) - center.x)
        if (up - halfHeight < inner) {
            halfWidth = minOf(halfWidth, center.x - sqrt(inner * inner - (up - halfHeight) * (up - halfHeight)))
        }
        val distance = r * halfSine
        for (edge in sector..sector + 1) {
            val ray = rays[edge]
            if (ray.y > 0f) halfWidth = minOf(halfWidth, (distance - ray.x * halfHeight) / ray.y)
        }
        Rect(center.x - halfWidth, center.y - halfHeight, center.x + halfWidth, center.y + halfHeight)
    }

    fun labelBounds(index: Int): Rect = labels[index]
    fun innerRadius(ring: Int): Float = if (ring == 0) 0f else firstRadius + ring * band
    fun outerRadius(ring: Int): Float = if (ring == Rings - 1) radius else innerRadius(ring + 1)
    fun pointOnRay(edge: Int, r: Float = radius) = Offset(rays[edge].x * r, radius - rays[edge].y * r)

    /** 轻点、读屏和绘图的静态几何不随手势改变。 */
    fun hit(local: Offset): Int = resolve(local, Outside, null)

    internal fun resolve(local: Offset, previous: Int, motion: SpeedFanStroke?): Int {
        val x = local.x
        val up = radius - local.y
        val squared = x * x + up * up
        if (x < 0f || up < 0f || !(squared <= radius * radius)) return Outside
        if (x <= originSize && up <= originSize) return Origin
        // 交界归顺时针及外侧。外弧、负坐标和进入 1× 均先于内部滞回判定。
        var sector = 0
        while (sector < Sectors - 1 && distanceToRay(x, up, sector + 1) >= 0f) sector++
        val r = sqrt(squared)
        val ring = when {
            r < outerRadius(0) -> 0
            r < outerRadius(1) -> 1
            else -> 2
        }
        val raw = 1 + sector * Rings + ring
        if (motion == null || raw == previous || previous == Outside) return raw
        if (previous == Origin) {
            // 回原点直接生效；刚离开方块一线时不因手抖反复离开/重入。
            val depth = maxOf(x, up) - originSize
            val projection = if (x >= up) motion.x else motion.up
            return if (ring != 0 || crossed(depth, projection, originSize, motion.slop)) raw else Origin
        }
        val priorSector = SpeedFanGeometry.sector(previous)
        val priorRing = SpeedFanGeometry.ring(previous)
        if (abs(sector - priorSector) > 1 || abs(ring - priorRing) > 1) return raw
        if (sector != priorSector) {
            val edge = maxOf(sector, priorSector)
            val sign = if (sector > priorSector) 1f else -1f
            // 到辐条的有符号垂距 = r·sin(角差)，不是把内、外圈的角度当成同一段指程。
            if (crossed(sign * distanceToRay(x, up, edge), sign * distanceToRay(motion.x, motion.up, edge),
                    2f * r * halfSine, motion.slop)) return raw
        }
        if (ring != priorRing) {
            val sign = if (ring > priorRing) 1f else -1f
            if (crossed(sign * (r - innerRadius(maxOf(ring, priorRing))), sign * (x * motion.x + up * motion.up) / r,
                    band, motion.slop)) return raw
        }
        // 交点只可能留下旧档或采用整个真实命中；不把上次扇号与这次环号拼成第三个档。
        return previous
    }

    private fun crossed(depth: Float, projectedTravel: Float, localWidth: Float, slop: Float): Boolean {
        val high = minOf(7f * density, slop * 0.85f, localWidth * 0.18f)
        if (depth >= high) return true
        val low = minOf(high, maxOf(2.25f * density, slop * 0.28f))
        if (depth < low) return false
        val intent = (projectedTravel / slop).coerceIn(0f, 1f)
        return depth >= high - (high - low) * intent
    }

    private fun distanceToRay(x: Float, up: Float, edge: Int): Float = x * rays[edge].y - up * rays[edge].x

    companion object {
        const val Outside = -1
        const val Origin = 0
        const val Sectors = 4
        const val Rings = 3
        const val Sweep = 90f / Sectors
        fun sector(index: Int): Int = (index - 1) / Rings
        fun ring(index: Int): Int = (index - 1) % Rings
        private fun direction(step: Double): Offset {
            val angle = step * PI / (2 * Sectors)
            return Offset(sin(angle).toFloat(), cos(angle).toFloat())
        }
        private val rays = Array(Sectors + 1) { if (it == Sectors) Offset(1f, 0f) else direction(it.toDouble()) }
        private val midpoints = Array(Sectors) { direction(it + 0.5) }
        private val halfSine = midpoints[0].x

        fun fit(anchor: Rect, safe: Rect, density: Float): SpeedFanGeometry? {
            if (anchor.isEmpty || safe.isEmpty || density <= 0f) return null
            val preferredRadius = PlayerTokens.SpeedFanRadius.value * density
            val center = anchor.center
            val reach = minOf(safe.right - center.x, center.y - safe.top)
            // 原点以按钮短边为方边，与胶囊同心，同时保留底栏本有的下方留白。
            val side = minOf(PlayerTokens.MinTouchTarget.value * density, anchor.width, anchor.height,
                (center.x - safe.left) * 2f, (safe.bottom - center.y) * 2f, reach * 0.36f)
            if (side < 1f) return null
            val left = round(center.x - side / 2f)
            val bottom = round(center.y + side / 2f)
            val r = minOf(preferredRadius, safe.right - left, bottom - safe.top).toInt().toFloat()
            if (r < 1f) return null
            return SpeedFanGeometry(Offset(left, bottom - r), r, side, density)
        }
    }
}
