package com.privateplayer.feature.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SeekGestureCalculationTest {
    @Test
    fun `短快滑使用配置步长且方向对称`() {
        val forward = calculateSeekGesture(90_000, 300_000, 48f, 48f, 200, 15)!!
        val backward = calculateSeekGesture(90_000, 300_000, -48f, 48f, 200, 15)!!
        assertTrue(forward.isFastSwipe)
        assertEquals(105_000L, forward.targetMs)
        assertEquals(15_000L, forward.deltaMs)
        assertEquals(75_000L, backward.targetMs)
        assertEquals(-15_000L, backward.deltaMs)
        // 快滑遵守配置，即使步长大于慢滑的 30 秒限制。
        assertEquals(150_000L, calculateSeekGesture(90_000, 300_000, 48f, 48f, 200, 60)!!.targetMs)
    }

    @Test
    fun `快滑时间和距离边界精确生效`() {
        assertTrue(calculateSeekGesture(90_000, 300_000, 72f, 72f, 250, 10)!!.isFastSwipe)
        assertFalse(calculateSeekGesture(90_000, 300_000, 72f, 72f, 251, 10)!!.isFastSwipe)
        assertFalse(calculateSeekGesture(90_000, 300_000, 72.1f, 72.1f, 250, 10)!!.isFastSwipe)
        // 来回滑动曾超过距离阈值，不应在退回后误判为快滑。
        assertFalse(calculateSeekGesture(90_000, 300_000, 40f, 73f, 200, 10)!!.isFastSwipe)
        assertEquals(0L, calculateSeekGesture(90_000, 300_000, 0f, 30f, 200, 10)!!.deltaMs)
    }

    @Test
    fun `慢滑按位移逐秒调整且不依赖视频时长`() {
        val shortVideo = calculateSeekGesture(60_000, 120_000, 24f, 24f, 500, 10)!!
        val longVideo = calculateSeekGesture(60_000, 100L * 60 * 60 * 1_000, 24f, 24f, 500, 10)!!
        assertFalse(shortVideo.isFastSwipe)
        assertEquals(3_000L, shortVideo.deltaMs)
        assertEquals(shortVideo.deltaMs, longVideo.deltaMs)
        assertEquals(-3_000L, calculateSeekGesture(60_000, 120_000, -24f, 24f, 500, 10)!!.deltaMs)
        assertEquals(1_000L, calculateSeekGesture(60_000, 120_000, 4f, 4f, 500, 10)!!.deltaMs)
        assertEquals(-1_000L, calculateSeekGesture(60_000, 120_000, -4f, 4f, 500, 10)!!.deltaMs)
        assertEquals(30_000L, calculateSeekGesture(60_000, 120_000, 10_000f, 10_000f, 500, 10)!!.deltaMs)
        assertEquals(-30_000L, calculateSeekGesture(60_000, 120_000, -10_000f, 10_000f, 500, 10)!!.deltaMs)
    }

    @Test
    fun `目标和实际增量限制在视频边界内`() {
        val start = calculateSeekGesture(1_500, 120_000, -20f, 20f, 100, 10)!!
        assertEquals(0L, start.targetMs)
        assertEquals(-1_500L, start.deltaMs)
        val end = calculateSeekGesture(119_500, 120_000, 20f, 20f, 100, 10)!!
        assertEquals(120_000L, end.targetMs)
        assertEquals(500L, end.deltaMs)
        assertEquals(Long.MAX_VALUE, calculateSeekGesture(Long.MAX_VALUE - 50, Long.MAX_VALUE, 20f, 20f, 100, 60)!!.targetMs)
        assertEquals(0L, calculateSeekGesture(-10, 120_000, -20f, 20f, 100, 10)!!.targetMs)
    }

    @Test
    fun `无时长和无效位移不生成预览`() {
        assertNull(calculateSeekGesture(0, 0, 20f, 20f, 100, 10))
        assertNull(calculateSeekGesture(0, -1, 20f, 20f, 100, 10))
        assertNull(calculateSeekGesture(0, 100_000, Float.NaN, 20f, 100, 10))
        assertNull(calculateSeekGesture(0, 100_000, 20f, Float.POSITIVE_INFINITY, 100, 10))
    }
}
