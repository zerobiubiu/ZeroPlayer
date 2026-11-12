package com.privateplayer.feature.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 队列跳转受理判定（F-20 受控 API 的纯逻辑部分）。
 *
 * UI 只表达意图；空队列、越界、重复目标与切换中的请求都必须是无操作，
 * 避免快速连点造成反复切源。真实切换仍由会话在切源门内执行。
 */
class QueueSelectionTest {

    private fun accept(
        index: Int,
        queueSize: Int = 3,
        currentIndex: Int = 0,
        targetMediaId: Long = 10L + index,
        currentMediaId: Long = 10L,
        switchInFlight: Boolean = false,
    ) = QueueSelection.shouldAccept(
        index = index,
        queueSize = queueSize,
        currentIndex = currentIndex,
        targetMediaId = targetMediaId,
        currentMediaId = currentMediaId,
        switchInFlight = switchInFlight,
    )

    @Test
    fun emptyQueueRejectsAnyIndex() {
        assertFalse(accept(index = 0, queueSize = 0))
        assertFalse(accept(index = 1, queueSize = 0))
    }

    @Test
    fun negativeIndexIsRejected() {
        assertFalse(accept(index = -1))
    }

    @Test
    fun indexBeyondLastItemIsRejected() {
        assertFalse(accept(index = 3, queueSize = 3))
        assertFalse(accept(index = 99, queueSize = 3))
    }

    @Test
    fun lastValidIndexIsAccepted() {
        assertTrue(accept(index = 2, queueSize = 3))
    }

    @Test
    fun currentIndexIsRejectedAsDuplicateClick() {
        assertFalse(accept(index = 1, currentIndex = 1))
    }

    @Test
    fun sameMediaAsCurrentIsRejectedEvenIfIndexChanged() {
        // 队列重建后索引可能变化，但媒体仍是当前正在播放的那一个。
        assertFalse(accept(index = 2, currentIndex = 0, targetMediaId = 42L, currentMediaId = 42L))
    }

    @Test
    fun unknownMediaIdDoesNotBlockValidIndex() {
        // mediaId 为 0 表示尚未入库，交由会话经 ensureMedia 解析，不在受理阶段误杀。
        assertTrue(accept(index = 1, targetMediaId = 0L, currentMediaId = 0L))
    }

    @Test
    fun rapidClicksWhileSwitchingAreIgnored() {
        assertFalse(accept(index = 1, switchInFlight = true))
        assertFalse(accept(index = 2, switchInFlight = true))
    }

    @Test
    fun nextClickIsAcceptedAfterSwitchCompletes() {
        assertFalse(accept(index = 1, switchInFlight = true))
        assertTrue(accept(index = 1, switchInFlight = false))
    }

    @Test
    fun differentTargetIsAccepted() {
        assertTrue(accept(index = 1, targetMediaId = 11L, currentMediaId = 10L))
    }
}
