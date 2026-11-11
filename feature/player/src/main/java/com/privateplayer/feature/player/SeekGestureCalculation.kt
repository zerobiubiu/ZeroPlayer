package com.privateplayer.feature.player

import kotlin.math.abs
import kotlin.math.roundToInt

/** 慢滑基准：每 8dp 约 1 秒，保证小范围微调。 */
private const val SLOW_DP_PER_SECOND = 8f

/** 手势峰值速度每提高 0.25dp/ms 放大一档，最多 4 档（16 倍）。 */
private const val VELOCITY_STEP_DP_PER_MS = 0.25f
private const val MAX_ACCELERATION_STEPS = 4

/** 各档位的时间刻度（秒）；越快的档位刻度越大，显示与吸附都用它。 */
private val TICK_SECONDS = intArrayOf(1, 2, 5, 10, 20)

internal data class SeekGesturePreview(
    val targetMs: Long,
    val deltaMs: Long,
    val durationMs: Long,
    /** 当前档位的跳动刻度（秒）。 */
    val tickSeconds: Int,
    /** 手势足够快，已进入放大档位。 */
    val accelerated: Boolean,
)

/**
 * 把横向滑动换算成跳转目标。
 *
 * 不再有固定的 30 秒上限：跳幅随滑动距离线性增长，并随手势峰值速度放大档位
 * （1×/2×/4×/8×/16×），最后对齐到 1/2/5/10/20 秒刻度。
 * 慢滑时每 8dp 约 1 秒，因此小范围仍然精准；快速长滑可以一次跨越几十分钟。
 */
internal fun calculateSeekGesture(
    positionMs: Long,
    durationMs: Long,
    dxDp: Float,
    peakVelocityDpPerMs: Float,
): SeekGesturePreview? {
    if (durationMs <= 0L || !dxDp.isFinite() || !peakVelocityDpPerMs.isFinite()) return null

    val origin = positionMs.coerceIn(0L, durationMs)
    val distance = abs(dxDp)
    val steps = (peakVelocityDpPerMs.coerceAtLeast(0f) / VELOCITY_STEP_DP_PER_MS)
        .toInt()
        .coerceIn(0, MAX_ACCELERATION_STEPS)
    val acceleration = 1 shl steps
    val tickSeconds = TICK_SECONDS[steps]
    if (distance == 0f) {
        return SeekGesturePreview(origin, 0L, durationMs, tickSeconds, steps > 0)
    }

    val rawSeconds = distance * acceleration / SLOW_DP_PER_SECOND
    val seconds = (rawSeconds / tickSeconds).roundToInt().toLong() * tickSeconds
    // 先限制可移动距离，避免接近 Long 上限的时间相加溢出。
    val requestedMs = seconds * 1_000L
    val target = when {
        dxDp > 0f -> origin + requestedMs.coerceAtMost(durationMs - origin)
        dxDp < 0f -> origin - requestedMs.coerceAtMost(origin)
        else -> origin
    }
    return SeekGesturePreview(
        targetMs = target,
        deltaMs = target - origin,
        durationMs = durationMs,
        tickSeconds = tickSeconds,
        accelerated = steps > 0,
    )
}
