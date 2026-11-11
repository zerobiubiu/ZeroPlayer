package com.privateplayer.feature.player

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

internal const val FAST_SWIPE_MAX_MS = 250L
internal const val FAST_SWIPE_MAX_DP = 72f
private const val FINE_SEEK_DP_PER_SECOND = 8f
private const val FINE_SEEK_MAX_SECONDS = 30

internal data class SeekGesturePreview(
    val targetMs: Long,
    val deltaMs: Long,
    val durationMs: Long,
    val isFastSwipe: Boolean,
)

/** 将触点位移换算为预览位置；慢滑与视频总时长无关。 */
internal fun calculateSeekGesture(
    positionMs: Long,
    durationMs: Long,
    dxDp: Float,
    maxAbsDxDp: Float,
    elapsedMs: Long,
    stepSeconds: Int,
): SeekGesturePreview? {
    if (durationMs <= 0L || !dxDp.isFinite() || !maxAbsDxDp.isFinite()) return null

    val origin = positionMs.coerceIn(0L, durationMs)
    val distance = abs(dxDp)
    val isFastSwipe = distance > 0f && elapsedMs in 0L..FAST_SWIPE_MAX_MS &&
        max(distance, maxAbsDxDp) <= FAST_SWIPE_MAX_DP
    val seconds = if (isFastSwipe) {
        stepSeconds.coerceAtLeast(0).toLong()
    } else {
        (distance / FINE_SEEK_DP_PER_SECOND).coerceAtMost(FINE_SEEK_MAX_SECONDS.toFloat())
            .roundToInt().toLong()
    }
    val requestedMs = seconds * 1_000L
    // 先限制可移动距离，避免接近 Long 上限的时间相加溢出。
    val target = when {
        dxDp > 0f -> origin + requestedMs.coerceAtMost(durationMs - origin)
        dxDp < 0f -> origin - requestedMs.coerceAtMost(origin)
        else -> origin
    }
    return SeekGesturePreview(
        targetMs = target,
        deltaMs = target - origin,
        durationMs = durationMs,
        isFastSwipe = isFastSwipe,
    )
}
