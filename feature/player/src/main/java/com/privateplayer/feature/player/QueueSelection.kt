package com.privateplayer.feature.player

/**
 * 队列跳转请求的受理判定（纯逻辑，供 JVM 单测覆盖）。
 *
 * UI 只表达“想播放队列中的某一项”；是否受理由会话判定：
 * - 空队列、越界索引：无效请求，不受理；
 * - 目标与当前正在播放/装载的索引相同：重复点击，不受理；
 * - 已有跳转在执行（切换中）：忽略后续点击，避免快速连点反复切源。
 *
 * 受理后的真实切换仍由会话在切源门（transitionMutex）内执行，
 * 媒体不存在等运行期失败由会话报告错误，本判定不代替执行。
 */
internal object QueueSelection {
    fun shouldAccept(
        index: Int,
        queueSize: Int,
        currentIndex: Int,
        targetMediaId: Long,
        currentMediaId: Long,
        switchInFlight: Boolean,
    ): Boolean {
        if (switchInFlight) return false
        if (queueSize <= 0 || index !in 0 until queueSize) return false
        if (index == currentIndex) return false
        if (targetMediaId > 0L && targetMediaId == currentMediaId) return false
        return true
    }
}
