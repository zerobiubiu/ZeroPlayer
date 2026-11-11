package com.privateplayer.core.common.format

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

private const val BYTES_PER_KIB = 1024.0
private val UNITS = arrayOf("B", "KiB", "MiB", "GiB", "TiB")

/**
 * 人类可读的字节大小，例如 `1.2 GiB`。
 * 按 1024 进制换算，并使用对应的 IEC 二进制单位。
 */
fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= BYTES_PER_KIB && unitIndex < UNITS.lastIndex) {
        value /= BYTES_PER_KIB
        unitIndex++
    }
    return if (unitIndex == 0) {
        "${value.toLong()} ${UNITS[unitIndex]}"
    } else {
        String.format(Locale.US, "%.1f %s", value, UNITS[unitIndex])
    }
}

/**
 * 播放时长，例如 `1:02:03`、`02:03`。
 * 时长未知（<= 0）时返回 `--:--`。
 */
fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0L) return "--:--"
    val totalSeconds = durationMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

/** 剩余时长，例如 `剩余 32:10`；用于播放器进度条辅助信息。 */
fun formatRemaining(positionMs: Long, durationMs: Long): String {
    if (durationMs <= 0L) return "--:--"
    val remaining = (durationMs - positionMs).coerceAtLeast(0L)
    // 已播完时显示 00:00，而不是未知时长占位符。
    return if (remaining == 0L) "00:00" else formatDuration(remaining)
}

private val DATE_TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.CHINA)

/** 本地时区的日期时间文本，用于导入时间、最近播放时间。 */
fun formatDateTime(epochMillis: Long?): String {
    if (epochMillis == null || epochMillis <= 0L) return "—"
    return DATE_TIME_FORMATTER.format(
        Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()),
    )
}

/** 首页使用的相对时间描述：刚刚 / N 分钟前 / N 小时前 / N 天前 / 具体日期。 */
fun formatRelativeTime(epochMillis: Long?, now: Long = System.currentTimeMillis()): String {
    if (epochMillis == null || epochMillis <= 0L) return "—"
    val diff = now - epochMillis
    val minutes = diff / 60_000
    return when {
        diff < 0 -> formatDateTime(epochMillis)
        minutes < 1 -> "刚刚"
        minutes < 60 -> "$minutes 分钟前"
        minutes < 60 * 24 -> "${minutes / 60} 小时前"
        minutes < 60 * 24 * 7 -> "${minutes / (60 * 24)} 天前"
        else -> formatDateTime(epochMillis)
    }
}

/** 百分比 0..1 转整数百分数文本；进度异常时回退为 0%。 */
fun formatPercent(progress: Float): String {
    val safe = if (progress.isNaN()) 0f else progress.coerceIn(0f, 1f)
    return "${(safe * 100).toInt()}%"
}

/** 校验码等场景下的安全取整，避免 `abs(Long.MIN_VALUE)` 溢出。 */
internal fun safeAbs(value: Long): Long = if (value == Long.MIN_VALUE) Long.MAX_VALUE else abs(value)
