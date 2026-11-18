package com.privateplayer.feature.player

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedFanStrokeTest {
    private val step = PI / 8.0
    private val densities = listOf(1f, 1.5f, 2.625f, 3.5f)
    private val sizes = listOf(230 to 190, 320 to 256, 411 to 915, 915 to 411, 1280 to 800)
    private fun anchor(d: Float, height: Int) = Rect(16f * d, (height - 68f) * d, 80f * d, (height - 20f) * d)
    private fun safe(d: Float, w: Int, h: Int) = Rect(4f * d, 4f * d, (w - 4f) * d, (h - 4f) * d)
    private fun geometry(d: Float = 1f, w: Int = 411, h: Int = 915) =
        requireNotNull(SpeedFanGeometry.fit(anchor(d, h), safe(d, w, h), d))
    private fun SpeedFanGeometry.point(r: Float, angle: Double) = Offset(r * sin(angle).toFloat(), radius - r * cos(angle).toFloat())
    private fun SpeedFanGeometry.stroke(d: Float) = SpeedFanStroke(this, origin.center, 0L, 8f * d)
    private fun index(sector: Int, ring: Int) = 1 + 3 * sector + ring

    /** 测试侧的物理真值：只读实际方块和 12 个文字盒；不调用生产命中、边界或防抖公式。 */
    private class VisibleFan(g: SpeedFanGeometry) {
        private val square = g.labelBounds(0)
        private val vertex = square.bottomLeft
        private val radii = DoubleArray(3) { ring ->
            (0..3).sumOf { sector ->
                val delta = g.labelBounds(1 + 3 * sector + ring).center - vertex
                hypot(delta.x.toDouble(), delta.y.toDouble())
            } / 4
        }

        fun boundary(ring: Int) = (radii[ring - 1] + radii[ring]) / 2

        fun key(p: Offset): Int {
            val x = (p.x - vertex.x).toDouble()
            val up = (vertex.y - p.y).toDouble()
            val r = hypot(x, up)
            // 局部原点在面板左上，方块左下就是圆心，其到上边的距离就是外半径。
            if (x < 0 || up < 0 || r > vertex.y) return -1
            if (x <= square.width && up <= square.height) return 0
            val sector = (atan2(x, up) / (PI / 8)).toInt().coerceIn(0, 3)
            val ring = if (r < boundary(1)) 0 else if (r < boundary(2)) 1 else 2
            return 1 + sector * 3 + ring
        }
    }

    @Test fun allCentersAreReachableFromEveryOtherKeyAcrossWindowSizesAndDensities() {
        assertEquals(listOf(1f, 0.75f, 0.5f, 0.25f, 2f, 2.5f, 3f, 3.5f, 4f, 5f, 6f, 7f, 8f), SpeedPresets.toList())
        for (d in densities) for ((w, h) in sizes) {
            val g = geometry(d, w, h)
            val safe = safe(d, w, h)
            val button = anchor(d, h)
            assertTrue(g.radius <= 224f * d)
            if (h >= 256 && w >= 320) assertEquals(224f * d, g.radius, 1f)
            assertTrue((g.origin.center + g.topLeft - button.center).getDistance() < 1f)
            assertTrue("底边留白", h * d - (g.topLeft.y + g.radius) >= 16 * d)
            val labels = SpeedPresets.indices.map(g::labelBounds)
            labels.forEachIndexed { i, rect ->
                assertTrue("空标签 $i", rect.width > 0f && rect.height > 0f)
                assertTrue(safe.contains(g.topLeft + rect.topLeft) && safe.contains(g.topLeft + rect.bottomRight))
                assertFalse(labels.indices.any { it != i && labels[it].overlaps(rect) })
                assertEquals(i, g.hit(rect.center))
                val s = g.stroke(d)
                s.sample(labels[7].center, 500)
                assertEquals(i, s.sample(rect.center, 550))
                for (j in labels.indices) {
                    assertEquals(j, s.sample(labels[j].center, 550))
                    assertEquals(i, s.sample(rect.center, 720))
                }
            }
            for (sector in 0..3) for (ring in 0..2) {
                val center = labels[index(sector, ring)].center
                val r = (center - Offset(0f, g.radius)).getDistance()
                assertTrue((g.point(r, (sector + 0.5) * step) - center).getDistance() < 0.001f)
                if (ring > 0) assertTrue(r > (labels[index(sector, ring - 1)].center - Offset(0f, g.radius)).getDistance())
            }
        }
    }

    @Test fun everyInternalSeamHoldsTremorAndSwitchesInBothDirectionsWithoutDwell() {
        for (d in densities) {
            val g = geometry(d)
            val visible = VisibleFan(g)
            fun cross(a: Int, b: Int, edge: Offset, normal: Offset) {
                val s = g.stroke(d)
                assertEquals(a, s.sample(g.labelBounds(a).center, 500))
                var t = 500L
                repeat(18) {
                    t += 8
                    assertEquals(a, s.sample(edge + normal * (if (it % 2 == 0) d else -d), t))
                }
                assertEquals(b, s.sample(edge + normal * (7.25f * d), t))
                repeat(18) {
                    t += 33
                    assertEquals(b, s.sample(edge + normal * (if (it % 2 == 0) -d else d), t))
                }
                assertEquals(a, s.sample(edge - normal * (7.25f * d), t + 300))
            }
            for (edge in 1..3) for (ring in 0..2) {
                val angle = edge * step
                val a = index(edge - 1, ring)
                val r = (g.labelBounds(a).center - Offset(0f, g.radius)).getDistance()
                cross(a, index(edge, ring), g.point(r, angle), Offset(cos(angle).toFloat(), sin(angle).toFloat()))
            }
            for (sector in 0..3) for (ring in 1..2) {
                val angle = (sector + 0.5) * step
                cross(index(sector, ring - 1), index(sector, ring), g.point(visible.boundary(ring).toFloat(), angle),
                    Offset(sin(angle).toFloat(), -cos(angle).toFloat()))
            }
        }
    }

    @Test fun crossingSpokeAndRingTogetherNeverInventsAThirdCell() {
        for (d in densities) for (edge in 1..3) for (ring in 1..2) for (sectorDir in listOf(-1, 1)) for (ringDir in listOf(-1, 1)) {
            val g = geometry(d)
            val visible = VisibleFan(g)
            val original = index(if (sectorDir > 0) edge - 1 else edge, if (ringDir > 0) ring - 1 else ring)
            val target = index(if (sectorDir > 0) edge else edge - 1, if (ringDir > 0) ring else ring - 1)
            for ((angularDepth, radialDepth) in listOf(0.8f to 0.8f, 0.8f to 9f, 9f to 0.8f, 9f to 9f)) {
                val s = g.stroke(d)
                assertEquals(original, s.sample(g.labelBounds(original).center, 500))
                val r = visible.boundary(ring).toFloat() + ringDir * radialDepth * d
                val p = g.point(r, edge * step + sectorDir * asin((angularDepth * d / r).toDouble()))
                assertEquals(target, g.hit(p))
                assertEquals(if (maxOf(angularDepth, radialDepth) < 2f) original else target, s.sample(p, 516))
            }
        }
    }

    @Test fun platformSlopAndLocalPhysicalDistanceBoundTheAmbiguousBand() {
        for (d in densities) for (ring in 0..2) {
            val g = geometry(d)
            val a = index(1, ring)
            val b = index(2, ring)
            val angle = 2 * step
            val radius = (g.labelBounds(a).center - Offset(0f, g.radius)).getDistance()
            val edge = g.point(radius, angle)
            val normal = Offset(cos(angle).toFloat(), sin(angle).toFloat())
            for (slop in listOf(4f, 12f)) {
                val s = SpeedFanStroke(g, g.origin.center, 0, slop * d)
                assertEquals(a, s.sample(edge - normal * (4f * d), 500))
                assertEquals(if (slop == 4f) b else a, s.sample(edge + normal * (4.5f * d), 1200))
                assertEquals(b, s.sample(edge + normal * (7.25f * d), 1201))
            }
        }
    }

    @Test fun samePenetrationDependsOnProjectedApproachNotEventCountOrElapsedDwell() {
        for (d in densities) for (frameMs in listOf(8L, 16L, 33L, 64L)) for (direction in listOf(-1, 1)) {
            val g = geometry(d)
            val boundary = VisibleFan(g).boundary(1).toFloat()
            val angle = 1.5 * step
            val original = index(1, if (direction > 0) 0 else 1)
            val target = index(1, if (direction > 0) 1 else 0)
            fun p(depth: Float) = g.point(boundary + direction * d * depth, angle)
            // 同样先停在界内 20dp，再沿法向到界外 4dp；快慢路线只有行进时间不同。
            for (duration in listOf(60L, 960L)) {
                val s = g.stroke(d)
                assertEquals(original, s.sample(p(-20f), 500))
                assertEquals(original, s.sample(p(-20f), 950))
                var t = frameMs
                while (t < duration) {
                    s.sample(p(-20f + 24f * t / duration), 950 + t)
                    t += frameMs
                }
                val expected = if (duration == 60L) target else original
                assertEquals("${frameMs}ms/行进${duration}ms", expected, s.sample(p(4f), 950 + duration))
                assertEquals(expected, s.sample(p(4f), 1950 + duration))
                assertEquals(target, s.sample(p(7.25f), 1950 + duration))
                assertEquals(original, s.sample(p(-8f), 1949 + duration))
            }
        }
    }

    @Test fun seededTrajectoriesOnlyReturnThePhysicalOrPreviousKeyEvenWithDuplicateTimesAndDroppedFrames() {
        val seed = 0x1400
        for (d in densities) for ((w, h) in sizes) {
            val g = geometry(d, w, h)
            val visible = VisibleFan(g)
            val random = Random(seed)
            val s = g.stroke(d)
            var p = g.labelBounds(8).center
            var t = 500L
            var previous = 8
            assertEquals(previous, s.sample(p, t))
            repeat(1200) { step ->
                p = if (step % 11 == 0) g.labelBounds(random.nextInt(SpeedPresets.size)).center else
                    p + Offset((random.nextFloat() - 0.5f) * d * 36f, (random.nextFloat() - 0.5f) * d * 36f)
                t += when (step % 7) { 0 -> 0; 1 -> 8; 2 -> 33; 3 -> 210; else -> 16 }
                val raw = visible.key(p)
                assertEquals("静态命中 seed=$seed step=$step", raw, g.hit(p))
                val result = s.sample(p, t)
                assertTrue("seed=$seed step=$step prev=$previous raw=$raw result=$result", result == raw || result == previous)
                if (raw <= 0 || previous == -1 || step % 11 == 0) assertEquals(raw, result)
                if (previous > 0 && raw > 0 && (abs((raw - 1) / 3 - (previous - 1) / 3) > 1 ||
                        abs((raw - 1) % 3 - (previous - 1) % 3) > 1)) assertEquals(raw, result)
                previous = result
            }
        }
    }
}
