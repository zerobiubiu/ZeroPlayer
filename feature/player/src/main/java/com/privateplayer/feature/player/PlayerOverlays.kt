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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material.icons.outlined.ScreenRotation
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.privateplayer.core.common.format.formatDuration
import com.privateplayer.feature.player.R
import com.privateplayer.core.playback.PlaybackSnapshot
import com.privateplayer.core.playback.PlaybackStatus
import kotlin.math.abs

/** 播放器覆盖层的可读文本色板：固定白色系，不跟随主题。 */
private val PlayerText = Color.White
private val PlayerTextSecondary = Color.White.copy(alpha = 0.72f)
private val PlayerAccent = Color(0xFF4A84FF)

@Composable
fun PlayerVideoLayer(
    onSurfaceCreated: (android.view.Surface) -> Unit,
    onSurfaceChanged: (android.view.Surface, Int, Int) -> Unit,
    onSurfaceDestroyed: (android.view.Surface) -> Unit,
    modifier: Modifier = Modifier,
) {
    val surfaceDescription = stringResource(R.string.player_video_surface)
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
            modifier = Modifier.fillMaxSize().semantics { contentDescription = surfaceDescription },
        )
    }
}

/**
 * 顶部控制条：返回、片名与四个常驻入口（画面比例、旋转屏幕、字幕、更多）。
 * 画面比例与旋转是两个独立动作：前者打开比例面板，后者直接切换横竖屏。
 * 安全区只作用于按钮本身，视频画面仍可铺满异形屏的缺口区域。
 */
@Composable
fun PlayerTopBar(
    title: String,
    onBack: () -> Unit,
    onOpenAspect: () -> Unit,
    onRotate: () -> Unit,
    onOpenSubtitle: () -> Unit,
    onOpenMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)))
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
            )
            .padding(horizontal = 4.dp, vertical = 2.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.player_back), tint = PlayerText)
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
        IconButton(onClick = onOpenAspect) {
            Icon(Icons.Outlined.AspectRatio, contentDescription = stringResource(R.string.player_aspect), tint = PlayerText)
        }
        IconButton(onClick = onRotate) {
            Icon(Icons.Outlined.ScreenRotation, contentDescription = stringResource(R.string.player_rotate), tint = PlayerText)
        }
        IconButton(onClick = onOpenSubtitle) {
            Icon(Icons.Outlined.ClosedCaption, contentDescription = stringResource(R.string.player_subtitle), tint = PlayerText)
        }
        IconButton(onClick = onOpenMore) {
            Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.player_more), tint = PlayerText)
        }
    }
}

/**
 * 底部控制条：时间行、进度条与常用操作。
 * 与顶部一样只让控件避开安全区，底部渐变可以延伸到手势条后面。
 */
@Composable
fun PlayerBottomControls(
    snapshot: PlaybackSnapshot,
    seekStepSeconds: Int,
    onSeekPreview: (Float) -> Unit,
    onSeekCommit: (Float) -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onPlayPause: () -> Unit,
    onOpenSpeed: () -> Unit,
    onOpenQueue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var scrubValue by remember { mutableStateOf<Float?>(null) }
    val duration = snapshot.durationMs.coerceAtLeast(1L)
    val sliderValue = scrubValue ?: (snapshot.positionMs.toFloat() / duration).coerceIn(0f, 1f)
    val shownPosition = (sliderValue * duration).toLong()
    val remaining = (duration - shownPosition).coerceAtLeast(0L)
    val progressDescription = stringResource(R.string.player_progress)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
            )
            .padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(R.string.player_time_format, formatDuration(shownPosition), formatDuration(duration)),
                style = MaterialTheme.typography.labelLarge,
                color = PlayerText,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(R.string.player_remaining_format, formatDuration(remaining)),
                style = MaterialTheme.typography.labelLarge,
                color = PlayerTextSecondary,
            )
        }
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
                thumbColor = PlayerText,
                activeTrackColor = PlayerAccent,
                inactiveTrackColor = PlayerText.copy(alpha = 0.28f),
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .semantics { contentDescription = progressDescription },
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            PlayerSpeedPill(speed = snapshot.speed, onClick = onOpenSpeed)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(26.dp),
            ) {
                PlayerTransportButton(
                    icon = Icons.Filled.Replay10,
                    contentDescription = stringResource(R.string.player_rewind_seconds, seekStepSeconds),
                    onClick = onSeekBack,
                )
                PlayerTransportButton(
                    icon = if (snapshot.status == PlaybackStatus.PLAYING) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = stringResource(if (snapshot.status == PlaybackStatus.PLAYING) R.string.player_pause else R.string.player_play),
                    onClick = onPlayPause,
                    large = true,
                    loading = snapshot.status == PlaybackStatus.LOADING,
                )
                PlayerTransportButton(
                    icon = Icons.Filled.Forward10,
                    contentDescription = stringResource(R.string.player_forward_seconds, seekStepSeconds),
                    onClick = onSeekForward,
                )
            }
            IconButton(onClick = onOpenQueue) {
                Icon(Icons.Outlined.QueueMusic, contentDescription = stringResource(R.string.player_queue), tint = PlayerText)
            }
        }
    }
}

@Composable
private fun PlayerSpeedPill(speed: Float, onClick: () -> Unit) {
    Surface(
        color = PlayerText.copy(alpha = 0.14f),
        shape = RoundedCornerShape(50),
        onClick = onClick,
    ) {
        Text(
            text = stringResource(R.string.player_speed_format, formatSpeed(speed)),
            style = MaterialTheme.typography.labelLarge,
            color = PlayerText,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun PlayerTransportButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    large: Boolean = false,
    loading: Boolean = false,
) {
    Surface(
        color = Color.Transparent,
        shape = CircleShape,
        onClick = onClick,
        modifier = Modifier.size(if (large) 56.dp else 44.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (loading) {
                CircularProgressIndicator(
                    color = PlayerText,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(22.dp),
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = PlayerText,
                    modifier = Modifier.size(if (large) 40.dp else 28.dp),
                )
            }
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
    val mode = stringResource(if (preview.accelerated) R.string.player_seek_mode_accelerated else R.string.player_seek_mode_fine)
    val fraction = (preview.targetMs.toDouble() / preview.durationMs.toDouble()).toFloat().coerceIn(0f, 1f)
    val seekDescription = stringResource(R.string.player_seek_description, targetText, deltaText, mode, preview.tickSeconds)

    Surface(
        color = Color.Black.copy(alpha = 0.9f),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .widthIn(max = 380.dp)
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = seekDescription
            },
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.player_seek_target, targetText),
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
                text = stringResource(R.string.player_seek_tick, mode, preview.tickSeconds),
                style = MaterialTheme.typography.labelSmall,
                color = PlayerTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun formatSeekDelta(deltaMs: Long): String {
    if (deltaMs == 0L) return stringResource(R.string.player_seek_delta_zero)
    val magnitude = abs(deltaMs)
    val remainder = magnitude % 1_000L
    val decimals = if (remainder == 0L) "" else ".${remainder.toString().padStart(3, '0').trimEnd('0')}"
    val sign = if (deltaMs > 0L) "+" else "−"
    return stringResource(R.string.player_seek_delta_format, sign, "${magnitude / 1_000L}$decimals")
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
            Text(text = stringResource(R.string.player_locked_hint), style = MaterialTheme.typography.labelSmall, color = PlayerText)
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
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.player_dismiss)) }
        }
    }
}

private fun formatSpeed(speed: Float): String {
    val rounded = (speed * 100).toInt()
    return if (rounded % 100 == 0) (rounded / 100).toString() else (rounded / 100f).toString().trimEnd('0').trimEnd('.')
}

