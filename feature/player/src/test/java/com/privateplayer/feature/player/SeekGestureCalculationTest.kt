package com.privateplayer.feature.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SeekGestureCalculationTest {
    @Test
    fun `慢滑保持逐秒精准并按位置生效`() {
        val forward = calculateSeekGesture(90_000, 300_000, 24f, 0.05f)!!
        val backward = calculateSeekGesture(90_000, 300_000, -24f, 0.05f)!!
        assertFalse(forward.accelerated)
        assertEquals(1, forward.tickSeconds)
        assertEquals(93_000L, forward.targetMs)
        assertEquals(3_000L, forward.deltaMs)
        assertEquals(87_000L, backward.targetMs)
        assertEquals(-3_000L, backward.deltaMs)
        // 小于半个刻度不动，避免抖动。
        assertEquals(0L, calculateSeekGesture(90_000, 300_000, 3f, 0.05f)!!.deltaMs)
    }

    @Test
    fun `滑动越快刻度与跳幅越大`() {
        val slow = calculateSeekGesture(0, 3_600_000, 100f, 0.1f)!!
        val medium = calculateSeekGesture(0, 3_600_000, 100f, 0.3f)!!
        val fast = calculateSeekGesture(0, 3_600_000, 100f, 0.6f)!!
        val faster = calculateSeekGesture(0, 3_600_000, 100f, 0.9f)!!
        val fastest = calculateSeekGesture(0, 3_600_000, 100f, 1.2f)!!
        assertEquals(listOf(1, 2, 5, 10, 20), listOf(
            slow.tickSeconds, medium.tickSeconds, fast.tickSeconds,
            faster.tickSeconds, fastest.tickSeconds,
        ))
        assertEquals(13_000L, slow.deltaMs)
        assertEquals(26_000L, medium.deltaMs)
        assertEquals(50_000L, fast.deltaMs)
        assertEquals(100_000L, faster.deltaMs)
        assertEquals(200_000L, fastest.deltaMs)
        assertTrue(fastest.accelerated)
        // 超过最高档仍按 16 倍计算。
        assertEquals(200_000L, calculateSeekGesture(0, 3_600_000, 100f, 50f)!!.deltaMs)
    }

    @Test
    fun `滑动距离不再有固定上限`() {
        // 旧实现把慢滑限制在 30 秒；现在按位移线性增长。
        assertEquals(125_000L, calculateSeekGesture(0, 3_600_000, 1_000f, 0.05f)!!.deltaMs)
        // 快滑长距离可以跨越大半部影片，只受视频边界限制。
        val jump = calculateSeekGesture(0, 7_200_000, 300f, 1.5f)!!
        assertEquals(600_000L, jump.deltaMs)
    }

    @Test
    fun `目标和实际增量限制在视频边界内`() {
        val start = calculateSeekGesture(1_500, 120_000, -20f, 0.05f)!!
        assertEquals(0L, start.targetMs)
        assertEquals(-1_500L, start.deltaMs)
        val end = calculateSeekGesture(119_500, 120_000, 20f, 0.05f)!!
        assertEquals(120_000L, end.targetMs)
        assertEquals(500L, end.deltaMs)
        assertEquals(
            Long.MAX_VALUE,
            calculateSeekGesture(Long.MAX_VALUE - 50, Long.MAX_VALUE, 20f, 0.05f)!!.targetMs,
        )
        assertEquals(0L, calculateSeekGesture(-10, 120_000, -20f, 0.05f)!!.targetMs)
    }

    @Test
    fun `零位移只返回当前刻度`() {
        val preview = calculateSeekGesture(90_000, 120_000, 0f, 1.0f)!!
        assertEquals(90_000L, preview.targetMs)
        assertEquals(0L, preview.deltaMs)
        assertEquals(20, preview.tickSeconds)
        assertTrue(preview.accelerated)
    }

    @Test
    fun `无时长和无效输入不生成预览`() {
        assertNull(calculateSeekGesture(0, 0, 20f, 0.1f))
        assertNull(calculateSeekGesture(0, -1, 20f, 0.1f))
        assertNull(calculateSeekGesture(0, 100_000, Float.NaN, 0.1f))
        assertNull(calculateSeekGesture(0, 100_000, 20f, Float.POSITIVE_INFINITY))
    }
}
