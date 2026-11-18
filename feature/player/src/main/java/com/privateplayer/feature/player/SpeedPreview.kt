package com.privateplayer.feature.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 一次手势绑定一个播放会话；原速不从试播中的引擎快照重读。仅由主线程手势端驱动。 */
internal class SpeedPreview(
    val originalSpeed: Float,
    private val apply: (owner: SpeedPreview, changedSpeed: Float?, finished: Boolean) -> Unit,
) {
    private var target = originalSpeed
    private var finished = false
    private val invalidated = MutableStateFlow(false)
    val revoked: StateFlow<Boolean> get() = invalidated

    /** 会话可被另一条命令接管，即使新命令碰巧请求相同速度，也要撤掉旧手指的反馈。 */
    fun revoke() { invalidated.value = true }

    fun update(speed: Float, finish: Boolean = false) {
        if (finished || revoked.value || (!finish && speed == target)) return
        val changed = speed.takeIf { it != target }
        target = speed
        finished = finish
        // 在当前试播档抬手只交还所有权，不发一次恢复和一次重复选速。
        apply(this, changed, finish)
    }

    fun cancel() = update(originalSpeed, finish = true)
}
