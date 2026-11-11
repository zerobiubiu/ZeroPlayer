package com.privateplayer.feature.player

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.FastForward
import androidx.compose.material.icons.outlined.FastRewind
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Brightness6
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.privateplayer.core.common.format.formatDuration
import com.privateplayer.core.playback.PlaybackStatus
import com.privateplayer.core.playback.PlaybackSnapshot
import com.privateplayer.core.playback.MediaTrack
import com.privateplayer.domain.model.VideoAspect
import kotlin.math.abs

/** 播放器覆盖层的可读文本色板：固定白色系，不跟随主题。 */
private val PlayerText = Color.White
private val PlayerTextSecondary = Color.White.copy(alpha = 0.72f)

@Composable
fun PlayerVideoLayer(
    onSurfaceCreated: (android.view.Surface) -> Unit,
    onSurfaceChanged: (android.view.Surface, Int, Int) -> Unit,
    onSurfaceDestroyed: (android.view.Surface) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            factory = { context ->
                SurfaceView(context).apply {
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            onSurfaceCreated(holder.surface)
                        }

                        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                            onSurfaceChanged(holder.surface, width, height)
                        }

                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                            onSurfaceDestroyed(holder.surface)
                        }
                    })
                }
            },
            onRelease = { onSurfaceDestroyed(it.holder.surface) },
            modifier = Modifier.fillMaxSize().semantics { contentDescription = "视频画面" },
        )
    }
}

@Composable
fun PlayerTopBar(
    title: String,
    locked: Boolean,
    onBack: () -> Unit,
    onToggleLock: () -> Unit,
    onOpenMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.65f), Color.Transparent)))
            .padding(horizontal = 4.dp, vertical = 4.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回", tint = PlayerText)
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = PlayerText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp),
        )
        IconButton(onClick = onToggleLock) {
            Icon(
                imageVector = if (locked) Icons.Outlined.Lock else Icons.Outlined.LockOpen,
                contentDescription = if (locked) "解锁" else "锁定",
                tint = PlayerText,
            )
        }
        IconButton(onClick = onOpenMore) {
            Icon(Icons.Outlined.MoreVert, contentDescription = "更多", tint = PlayerText)
        }
    }
}

@Composable
fun PlayerCenterControls(
    snapshot: PlaybackSnapshot,
    hasPrevious: Boolean,
    hasNext: Boolean,
    seekStepSeconds: Int,
    onPlayPause: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier,
    ) {
        PlayerCircleButton(
            icon = Icons.Outlined.FastRewind,
            contentDescription = "快退 $seekStepSeconds 秒",
            onClick = onSeekBack,
        )
        PlayerCircleButton(
            icon = Icons.Filled.SkipPrevious,
            contentDescription = "上一个",
            onClick = onPrevious,
            small = true,
            enabled = hasPrevious,
        )
        PlayerCircleButton(
            icon = if (snapshot.status == PlaybackStatus.PLAYING) Icons.Outlined.Pause else Icons.Filled.PlayArrow,
            contentDescription = if (snapshot.status == PlaybackStatus.PLAYING) "暂停" else "播放",
            onClick = onPlayPause,
            large = true,
            loading = snapshot.status == PlaybackStatus.LOADING,
        )
        PlayerCircleButton(
            icon = Icons.Filled.SkipNext,
            contentDescription = "下一个",
            onClick = onNext,
            small = true,
            enabled = hasNext,
        )
        PlayerCircleButton(
            icon = Icons.Outlined.FastForward,
            contentDescription = "快进 $seekStepSeconds 秒",
            onClick = onSeekForward,
        )
    }
}

@Composable
private fun PlayerCircleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    large: Boolean = false,
    small: Boolean = false,
    loading: Boolean = false,
    enabled: Boolean = true,
) {
    Surface(
        color = Color.Black.copy(alpha = 0.42f),
        shape = androidx.compose.foundation.shape.CircleShape,
        modifier = Modifier.size(
            when {
                large -> 64.dp
                small -> 38.dp
                else -> 48.dp
            },
        ),
        onClick = onClick,
        enabled = enabled,
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (loading) {
                CircularProgressIndicator(
                    color = PlayerText,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(24.dp),
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = PlayerText.copy(alpha = if (enabled) 1f else 0.35f),
                    modifier = Modifier.size(if (large) 32.dp else if (small) 18.dp else 22.dp),
                )
            }
        }
    }
}

@Composable
fun PlayerBottomControls(
    snapshot: PlaybackSnapshot,
    subtitleLabel: String,
    audioLabel: String,
    onSeekPreview: (Float) -> Unit,
    onSeekCommit: (Float) -> Unit,
    onOpenSpeed: () -> Unit,
    onOpenSubtitle: () -> Unit,
    onOpenAudio: () -> Unit,
    onOpenAspect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var scrubValue by remember { mutableStateOf<Float?>(null) }
    val duration = snapshot.durationMs.coerceAtLeast(1L)
    val sliderValue = scrubValue ?: (snapshot.positionMs.toFloat() / duration).coerceIn(0f, 1f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f))))
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = formatDuration((sliderValue * duration).toLong()),
                style = MaterialTheme.typography.labelMedium,
                color = PlayerText,
            )
            Slider(
                value = sliderValue,
                onValueChange = { value ->
                    scrubValue = value
                    onSeekPreview(value)
                },
                onValueChangeFinished = {
                    val value = scrubValue ?: sliderValue
                    onSeekCommit(value)
                    scrubValue = null
                },
                enabled = snapshot.durationMs > 0L,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = PlayerText.copy(alpha = 0.28f),
                ),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
                    .semantics { contentDescription = "播放进度" },
            )
            Text(
                text = formatDuration(duration),
                style = MaterialTheme.typography.labelMedium,
                color = PlayerTextSecondary,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            PlayerChip(text = "${snapshot.speed}×", icon = Icons.Outlined.Speed, onClick = onOpenSpeed)
            PlayerChip(text = subtitleLabel, icon = Icons.Outlined.ClosedCaption, onClick = onOpenSubtitle)
            PlayerChip(text = audioLabel, icon = Icons.Outlined.VolumeUp, onClick = onOpenAudio)
            PlayerChip(text = snapshot.aspect.label, icon = Icons.Outlined.AspectRatio, onClick = onOpenAspect)
        }
    }
}

@Composable
private fun PlayerChip(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Surface(
        color = PlayerText.copy(alpha = 0.12f),
        shape = RoundedCornerShape(8.dp),
        onClick = onClick,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
        ) {
            Icon(icon, contentDescription = null, tint = PlayerTextSecondary, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(text = text, style = MaterialTheme.typography.labelSmall, color = PlayerText, fontSize = 11.sp)
        }
    }
}

/** 拖动时显示与真实进度条分离的预览，不拦截播放器控制。 */
@Composable
internal fun PlayerSeekGestureOverlay(
    preview: SeekGesturePreview,
    modifier: Modifier = Modifier,
) {
    val targetText = if (preview.targetMs == 0L) "00:00" else formatDuration(preview.targetMs)
    val deltaText = formatSeekDelta(preview.deltaMs)
    val mode = if (preview.isFastSwipe) "快滑" else "微调"
    val fraction = (preview.targetMs.toDouble() / preview.durationMs.toDouble()).toFloat().coerceIn(0f, 1f)

    Surface(
        color = Color.Black.copy(alpha = 0.9f),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .widthIn(max = 380.dp)
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = "预览目标 $targetText，$deltaText，$mode。大范围请使用底部进度条"
            },
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "目标 $targetText",
                    style = MaterialTheme.typography.titleSmall,
                    color = PlayerText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                Text(text = deltaText, style = MaterialTheme.typography.titleSmall, color = PlayerText)
            }
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(PlayerText.copy(alpha = 0.28f), RoundedCornerShape(50)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .height(4.dp)
                        .background(PlayerText, RoundedCornerShape(50)),
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "$mode · 大范围请使用底部进度条",
                style = MaterialTheme.typography.labelSmall,
                color = PlayerTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun formatSeekDelta(deltaMs: Long): String {
    if (deltaMs == 0L) return "0 秒"
    val magnitude = abs(deltaMs)
    val remainder = magnitude % 1_000L
    val decimals = if (remainder == 0L) "" else ".${remainder.toString().padStart(3, '0').trimEnd('0')}"
    return "${if (deltaMs > 0L) "+" else "−"}${magnitude / 1_000L}$decimals 秒"
}

/** 手势反馈浮层：快进/快退、音量、亮度、临时倍速。 */
@Composable
fun PlayerGestureOverlay(
    message: String?,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        Surface(
            color = Color.Black.copy(alpha = 0.62f),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(
                text = message ?: "",
                style = MaterialTheme.typography.titleSmall,
                color = PlayerText,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}

/** 锁定态提示：单击浮层解锁。 */
@Composable
fun LockedIndicator(onUnlock: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        color = Color.Black.copy(alpha = 0.45f),
        shape = RoundedCornerShape(20.dp),
        onClick = onUnlock,
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Icon(Icons.Outlined.Lock, contentDescription = null, tint = PlayerText, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(text = "已锁定，点击解锁", style = MaterialTheme.typography.labelSmall, color = PlayerText)
        }
    }
}

/** 播放器错误提示条。 */
@Composable
fun PlayerErrorBanner(message: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onDismiss) { Text("知道了") }
        }
    }
}

private val VideoAspect.label: String
    get() = when (this) {
        VideoAspect.FIT -> "适应"
        VideoAspect.FILL -> "拉伸"
        VideoAspect.CROP -> "裁剪"
        VideoAspect.RATIO_16_9 -> "16:9"
        VideoAspect.RATIO_4_3 -> "4:3"
    }

private fun trackLabel(track: MediaTrack?): String = track?.label ?: "默认"
