package com.privateplayer.feature.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.privateplayer.core.common.format.formatDuration
import com.privateplayer.core.design.components.ActionSheet
import com.privateplayer.core.design.components.SheetAction
import com.privateplayer.core.playback.PlaybackStatus
import com.privateplayer.domain.model.VideoAspect
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private enum class DragMode { SEEK, VOLUME, BRIGHTNESS, IGNORED }

/** 沉浸式播放器页面；画面和控制均经播放引擎接口。 */
@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val view = LocalView.current
    val context = LocalView.current.context
    val activity = remember(context) { context.findActivity() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val audioManager = remember(context) { context.getSystemService(AudioManager::class.java) }
    val surfaceOwner = remember(viewModel) { Any() }

    var brightness by remember { mutableFloatStateOf(readBrightness(activity)) }
    var volume by remember { mutableIntStateOf(audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0) }
    var maxVolume by remember { mutableIntStateOf((audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 1).coerceAtLeast(1)) }

    var dragMode by remember { mutableStateOf<DragMode?>(null) }
    var baseBrightness by remember { mutableFloatStateOf(brightness) }
    var dragOnLeftHalf by remember { mutableStateOf(false) }
    var baseVolume by remember { mutableIntStateOf(volume) }
    var baseSeekMs by remember { mutableLongStateOf(0L) }
    var baseSeekMediaId by remember { mutableLongStateOf(0L) }
    var seekPeakVelocity by remember { mutableFloatStateOf(0f) }
    var seekPreview by remember { mutableStateOf<SeekGesturePreview?>(null) }
    var seekActive by remember { mutableStateOf(false) }
    var lastSeekHapticTarget by remember { mutableStateOf<Long?>(null) }
    var lastSeekHapticAt by remember { mutableLongStateOf(0L) }
    var gestureMessage by remember { mutableStateOf<String?>(null) }
    var gestureMessageRevision by remember { mutableIntStateOf(0) }
    var showSpeedSheet by remember { mutableStateOf(false) }
    var showSubtitleSheet by remember { mutableStateOf(false) }
    var showAudioSheet by remember { mutableStateOf(false) }
    var showAspectSheet by remember { mutableStateOf(false) }
    var showMoreSheet by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }

    // 关闭播放页即结束播放；按主页键退到桌面不触发返回，后台播放保留。
    val closePlayer = remember(viewModel, onBack) {
        {
            viewModel.closePlayback()
            onBack()
        }
    }
    BackHandler(onBack = closePlayer)

    // 沉浸式：隐藏系统栏 + 常亮 + 允许画面进入缺口；离开页面时恢复。
    DisposableEffect(Unit) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val originalOrientation = activity?.requestedOrientation
        var originalCutout: Int? = null
        if (window != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            originalCutout = window.attributes.layoutInDisplayCutoutMode
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                } else {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
        }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (originalOrientation != null) activity?.requestedOrientation = originalOrientation
            val restoreCutout = originalCutout
            if (window != null && restoreCutout != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.attributes = window.attributes.apply { layoutInDisplayCutoutMode = restoreCutout }
            }
        }
    }

    DisposableEffect(activity, audioManager) {
        val host = activity as? PlayerVolumeKeyHost
        host?.setPlayerVolumeKeyHandler { event ->
            val direction = when (event.keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> AudioManager.ADJUST_RAISE
                KeyEvent.KEYCODE_VOLUME_DOWN -> AudioManager.ADJUST_LOWER
                else -> null
            }
            if (direction == null || audioManager == null) {
                false
            } else {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0)
                    maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                    volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).coerceIn(0, maxVolume)
                    gestureMessage = "音量 ${volume * 100 / maxVolume}%"
                    if (!seekActive) seekPreview = null
                    gestureMessageRevision++
                }
                true
            }
        }
        onDispose { host?.setPlayerVolumeKeyHandler(null) }
    }
    DisposableEffect(viewModel, surfaceOwner) {
        viewModel.activateSurfaceOwner(surfaceOwner)
        onDispose { viewModel.detachSurface(surfaceOwner) }
    }


    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) viewModel.onStart()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 弹出底部面板/系统确实可能让系统栏短暂出现，这里在面板状态变化后重新隐藏。
    LaunchedEffect(showSpeedSheet, showSubtitleSheet, showAudioSheet, showAspectSheet, showMoreSheet) {
        activity?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    LaunchedEffect(gestureMessage, gestureMessageRevision) {
        if (gestureMessage != null) {
            delay(1200)
            gestureMessage = null
        }
    }

    LaunchedEffect(seekPreview, seekActive) {
        if (seekPreview != null && !seekActive) {
            delay(1200)
            seekPreview = null
        }
    }

    LaunchedEffect(state.mediaId, state.locked, state.gesturePrefs.seekGestureEnabled, state.playback.durationMs > 0L) {
        if (state.locked || !state.gesturePrefs.seekGestureEnabled ||
            state.playback.durationMs <= 0L || baseSeekMediaId != state.mediaId
        ) {
            seekPreview = null
            seekActive = false
            if (dragMode == DragMode.SEEK) dragMode = DragMode.IGNORED
        }
    }

    // 播放中 4 秒后自动隐藏控制层。
    LaunchedEffect(state.controlsVisible, state.playback.status, state.locked, seekActive) {
        if (state.controlsVisible && state.playback.status == PlaybackStatus.PLAYING && !state.locked && !seekActive) {
            delay(4_000)
            viewModel.setControlsVisible(false)
        }
    }

    LaunchedEffect(state.error) {
        if (state.error != null) viewModel.setControlsVisible(true)
    }

    val handleGesture: (PlayerGesture) -> Unit = { gesture ->
        when (gesture) {
                    PlayerGesture.SingleTap -> viewModel.toggleControls()

                    is PlayerGesture.DoubleTap -> {
                        if (!state.locked && state.playback.durationMs > 0L) {
                            val step = state.playbackPrefs.seekStepSeconds * 1_000L
                            viewModel.seekBy(if (gesture.leftSide) -step else step)
                            seekPreview = null
                            gestureMessage = if (gesture.leftSide) {
                                "快退 ${state.playbackPrefs.seekStepSeconds} 秒"
                            } else {
                                "快进 ${state.playbackPrefs.seekStepSeconds} 秒"
                            }
                        }
                    }

                    is PlayerGesture.DragStart -> {
                        dragMode = null
                        baseBrightness = brightness
                        maxVolume = (audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 1).coerceAtLeast(1)
                        volume = (audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: volume).coerceIn(0, maxVolume)
                        baseVolume = volume
                        dragOnLeftHalf = gesture.position.x < gesture.size.width / 2f
                        baseSeekMs = state.playback.positionMs
                        baseSeekMediaId = state.mediaId
                        seekPreview = null
                        seekActive = false
                        seekPeakVelocity = 0f
                        lastSeekHapticTarget = null
                        lastSeekHapticAt = 0L
                    }

                    is PlayerGesture.Drag -> {
                        val mode = dragMode ?: run {
                            val horizontal = abs(gesture.dxDp) > abs(gesture.dyDp)
                            when {
                                horizontal && state.gesturePrefs.seekGestureEnabled &&
                                    !state.locked && state.playback.durationMs > 0L -> DragMode.SEEK
                                !horizontal && dragOnLeftHalf && state.gesturePrefs.brightnessGestureEnabled -> DragMode.BRIGHTNESS
                                !horizontal && !dragOnLeftHalf && state.gesturePrefs.volumeGestureEnabled -> DragMode.VOLUME
                                else -> DragMode.IGNORED
                            }
                        }.also { dragMode = it }

                        when (mode) {
                            DragMode.SEEK -> {
                                if (!state.locked && state.gesturePrefs.seekGestureEnabled &&
                                    state.playback.durationMs > 0L && state.mediaId == baseSeekMediaId
                                ) {
                                    val velocity = if (gesture.elapsedMs > 0L) {
                                        abs(gesture.dxDp) / gesture.elapsedMs
                                    } else {
                                        0f
                                    }
                                    if (velocity > seekPeakVelocity) seekPeakVelocity = velocity
                                    val preview = calculateSeekGesture(
                                        positionMs = baseSeekMs,
                                        durationMs = state.playback.durationMs,
                                        dxDp = gesture.dxDp,
                                        peakVelocityDpPerMs = seekPeakVelocity,
                                    )
                                    if (preview != null) {
                                        seekPreview = preview
                                        seekActive = true
                                        gestureMessage = null
                                        val now = SystemClock.uptimeMillis()
                                        if (state.gesturePrefs.hapticsEnabled &&
                                            lastSeekHapticTarget != preview.targetMs &&
                                            (lastSeekHapticTarget == null || now - lastSeekHapticAt >= 70L)
                                        ) {
                                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                            lastSeekHapticTarget = preview.targetMs
                                            lastSeekHapticAt = now
                                        }
                                    }
                                } else {
                                    seekPreview = null
                                    seekActive = false
                                    dragMode = DragMode.IGNORED
                                }
                            }

                            DragMode.BRIGHTNESS -> {
                                brightness = (baseBrightness - gesture.dyFraction).coerceIn(0.05f, 1f)
                                applyBrightness(activity, brightness)
                                gestureMessage = "亮度 ${(brightness * 100).toInt()}%"
                            }

                            DragMode.VOLUME -> {
                                volume = (baseVolume - gesture.dyFraction * maxVolume * 1.5f).roundToInt()
                                    .coerceIn(0, maxVolume)
                                audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
                                gestureMessage = "音量 ${(volume * 100 / maxVolume)}%"
                            }
                            DragMode.IGNORED -> Unit
                        }
                    }

                    PlayerGesture.DragEnd -> {
                        val preview = seekPreview
                        val duration = state.playback.durationMs
                        if (dragMode == DragMode.SEEK && preview != null && preview.deltaMs != 0L &&
                            !state.locked && state.gesturePrefs.seekGestureEnabled &&
                            state.mediaId == baseSeekMediaId && duration > 0L
                        ) {
                            val target = preview.targetMs.coerceIn(0L, duration)
                            if (target != baseSeekMs.coerceIn(0L, duration)) viewModel.seekTo(target)
                        } else if (dragMode == DragMode.SEEK) {
                            seekPreview = null
                        }
                        seekActive = false
                        dragMode = null
                    }

                    PlayerGesture.DragCancel -> {
                        seekPreview = null
                        seekActive = false
                        dragMode = null
                    }

                    PlayerGesture.LongPressStart -> {
                        if (state.gesturePrefs.longPressSpeedEnabled) {
                            viewModel.beginTemporarySpeed(state.playbackPrefs.longPressSpeed)
                            gestureMessage = "${state.playbackPrefs.longPressSpeed}× 临时倍速"
                        }
                    }

                    PlayerGesture.LongPressEnd -> {
                        viewModel.endTemporarySpeed()
                    }
                }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 手势只盖在视频画面上：按钮所在的控件层在它之上，不会被滑动识别抢走点击。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .playerGestures(enabled = !state.locked) { gesture ->
                    handleGesture(gesture)
                },
        ) {
            PlayerVideoLayer(
                onSurfaceCreated = { viewModel.attachSurface(surfaceOwner, it) },
                onSurfaceChanged = { surface, width, height -> viewModel.resizeSurface(surfaceOwner, surface, width, height) },
                onSurfaceDestroyed = { viewModel.detachSurface(surfaceOwner, it) },
            )

            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.CircularProgressIndicator(color = Color.White)
                }
            }
        }

        // 控件层独立：顶部、底部与浮层按钮优先接收触摸。
        Box(modifier = Modifier.fillMaxSize()) {
            val controlsVisible = state.controlsVisible && !state.locked

            Column(modifier = Modifier.fillMaxSize()) {
                AnimatedVisibility(visible = controlsVisible, enter = fadeIn(), exit = fadeOut()) {
                    PlayerTopBar(
                        title = state.title,
                        onBack = closePlayer,
                        onOpenAspect = { showAspectSheet = true },
                        onOpenSubtitle = { showSubtitleSheet = true },
                        onOpenMore = { showMoreSheet = true },
                    )
                }
                Spacer(Modifier.weight(1f))
                PlayerGestureFeedback(
                    preview = seekPreview,
                    message = gestureMessage,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                )
                AnimatedVisibility(visible = controlsVisible, enter = fadeIn(), exit = fadeOut()) {
                    PlayerBottomControls(
                        snapshot = state.playback,
                        seekStepSeconds = state.playbackPrefs.seekStepSeconds,
                        onSeekPreview = { fraction ->
                            val duration = state.playback.durationMs
                            if (duration > 0) {
                                seekPreview = null
                                val target = (fraction * duration).toLong()
                                gestureMessage = if (target == 0L) "00:00" else formatDuration(target)
                            }
                        },
                        onSeekCommit = { fraction ->
                            val duration = state.playback.durationMs
                            if (duration > 0) viewModel.seekTo((fraction * duration).toLong())
                        },
                        onSeekBack = { viewModel.seekBy(-state.playbackPrefs.seekStepSeconds * 1_000L) },
                        onSeekForward = { viewModel.seekBy(state.playbackPrefs.seekStepSeconds * 1_000L) },
                        onPlayPause = viewModel::playPause,
                        onOpenSpeed = { showSpeedSheet = true },
                        onOpenQueue = { showQueueSheet = true },
                    )
                }
            }
        }

        if (state.locked) {
            LockedIndicator(
                onUnlock = viewModel::toggleLock,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp),
            )
        }

        state.error?.let { message ->
            PlayerErrorBanner(
                message = message,
                onDismiss = viewModel::consumeError,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 96.dp),
            )
        }

        AnimatedVisibility(
            visible = state.playback.status == PlaybackStatus.ENDED && state.controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 208.dp),
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.7f),
                shape = RoundedCornerShape(16.dp),
                onClick = viewModel::playPause,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                ) {
                    Text(text = "播放结束，点击重播", style = MaterialTheme.typography.titleSmall, color = Color.White)
                }
            }
        }
    }

    if (showSpeedSheet) {
        ActionSheet(
            title = "播放速度",
            actions = SPEED_OPTIONS.map { speed ->
                SheetAction(
                    label = if (speed == state.playback.speed) "$speed×（当前）" else "$speed×",
                    icon = Icons.Outlined.Check,
                    onClick = {
                        viewModel.setSpeed(speed)
                        showSpeedSheet = false
                    },
                )
            },
            onDismiss = { showSpeedSheet = false },
        )
    }

    if (showAspectSheet) {
        ActionSheet(
            title = "画面比例",
            actions = VideoAspect.entries.map { aspect ->
                SheetAction(
                    label = if (aspect == state.playback.aspect) "${aspect.labelText}（当前）" else aspect.labelText,
                    icon = Icons.Outlined.Check,
                    onClick = {
                        viewModel.setAspect(aspect)
                        showAspectSheet = false
                    },
                )
            },
            onDismiss = { showAspectSheet = false },
        )
    }

    if (showAudioSheet) {
        ActionSheet(
            title = "音轨",
            actions = if (state.audioTracks.isEmpty()) {
                listOf(SheetAction(label = "没有可选音轨", icon = Icons.Outlined.Check, enabled = false, onClick = {}))
            } else {
                state.audioTracks.map { track ->
                    SheetAction(
                        label = if (track.id == state.selectedAudioTrackId) "${track.label}（当前）" else track.label,
                        icon = Icons.Outlined.Check,
                        supportingText = track.codec,
                        onClick = {
                            viewModel.selectAudioTrack(track.id)
                            showAudioSheet = false
                        },
                    )
                }
            },
            onDismiss = { showAudioSheet = false },
        )
    }

    if (showMoreSheet) {
        ActionSheet(
            title = state.title,
            actions = listOf(
                SheetAction(
                    label = "锁定屏幕",
                    icon = Icons.Outlined.Lock,
                    supportingText = "隐藏控制，避免误触",
                    onClick = {
                        viewModel.toggleLock()
                        showMoreSheet = false
                    },
                ),
                SheetAction(
                    label = "音轨",
                    icon = Icons.Outlined.VolumeUp,
                    supportingText = audioLabel(state),
                    onClick = {
                        showMoreSheet = false
                        showAudioSheet = true
                    },
                ),
                SheetAction(
                    label = "画面比例",
                    icon = Icons.Outlined.AspectRatio,
                    supportingText = state.playback.aspect.labelText,
                    onClick = {
                        showMoreSheet = false
                        showAspectSheet = true
                    },
                ),
                SheetAction(
                    label = "旋转屏幕",
                    icon = Icons.Outlined.ScreenRotation,
                    supportingText = "切换横屏 / 竖屏",
                    onClick = {
                        activity?.toggleOrientation()
                        showMoreSheet = false
                    },
                ),
                SheetAction(
                    label = "播放引擎",
                    icon = Icons.Outlined.Check,
                    supportingText = state.engineDetail,
                    enabled = false,
                    onClick = {},
                ),
            ),
            onDismiss = { showMoreSheet = false },
        )
    }

    if (showQueueSheet) {
        ActionSheet(
            title = "播放列表 · ${(state.queueIndex + 1).coerceAtLeast(1)}/${state.queue.size.coerceAtLeast(1)}",
            actions = buildList {
                add(
                    SheetAction(
                        label = "上一集",
                        icon = Icons.Outlined.SkipPrevious,
                        enabled = state.queueIndex > 0,
                        onClick = {
                            viewModel.playPrevious()
                            showQueueSheet = false
                        },
                    ),
                )
                add(
                    SheetAction(
                        label = "下一集",
                        icon = Icons.Outlined.SkipNext,
                        enabled = state.hasNext,
                        onClick = {
                            viewModel.playNext()
                            showQueueSheet = false
                        },
                    ),
                )
                PlayMode.entries.forEach { mode ->
                    add(
                        SheetAction(
                            label = if (mode == state.playMode) "${mode.label}（当前）" else mode.label,
                            icon = Icons.Outlined.Repeat,
                            onClick = {
                                viewModel.setPlayMode(mode)
                                showQueueSheet = false
                            },
                        ),
                    )
                }
            },
            onDismiss = { showQueueSheet = false },
        )
    }

    if (showSubtitleSheet) {
        SubtitleSheet(
            state = state,
            onSelectTrack = viewModel::selectSubtitleTrack,
            onAdjustDelay = viewModel::adjustSubtitleDelay,
            onResetDelay = viewModel::resetSubtitleDelay,
            onDismiss = { showSubtitleSheet = false },
        )
    }
}

@Composable
private fun PlayerGestureFeedback(
    preview: SeekGesturePreview?,
    message: String?,
    modifier: Modifier = Modifier,
) {
    if (preview != null) {
        PlayerSeekGestureOverlay(preview = preview, modifier = modifier)
    } else {
        PlayerGestureOverlay(message = message, modifier = modifier)
    }
}

private fun audioLabel(state: PlayerUiState): String {
    val track = state.audioTracks.firstOrNull { it.id == state.selectedAudioTrackId }
    return track?.label ?: "音轨"
}

private val VideoAspect.labelText: String
    get() = when (this) {
        VideoAspect.FIT -> "适应屏幕"
        VideoAspect.FILL -> "拉伸填充"
        VideoAspect.CROP -> "裁剪填满"
        VideoAspect.RATIO_16_9 -> "16:9"
        VideoAspect.RATIO_4_3 -> "4:3"
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubtitleSheet(
    state: PlayerUiState,
    onSelectTrack: (String?) -> Unit,
    onAdjustDelay: (Long) -> Unit,
    onResetDelay: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                text = "字幕",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            SubtitleRow(
                label = "关闭字幕",
                selected = state.selectedSubtitleTrackId == null,
                onClick = { onSelectTrack(null) },
            )
            state.subtitleTracks.forEach { track ->
                SubtitleRow(
                    label = track.label,
                    selected = track.id == state.selectedSubtitleTrackId,
                    onClick = { onSelectTrack(track.id) },
                )
            }
            if (state.subtitleTracks.isEmpty()) {
                Text(
                    text = "未检测到字幕轨。可在详情页导入外挂字幕（srt / ass / ssa / vtt）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "字幕延迟", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = "${state.subtitleDelayMs} ms",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { onAdjustDelay(-state.subtitlePrefs.delayStepMs.toLong()) }) {
                    Text("-${state.subtitlePrefs.delayStepMs}ms")
                }
                TextButton(onClick = { onAdjustDelay(state.subtitlePrefs.delayStepMs.toLong()) }) {
                    Text("+${state.subtitlePrefs.delayStepMs}ms")
                }
                TextButton(onClick = onResetDelay) { Text("重置") }
            }
            Text(
                text = "字幕字号 ${(state.subtitlePrefs.fontSizeScale * 100).toInt()}% · ${state.subtitlePrefs.background.label} · ${state.subtitlePrefs.textColor.label}（在设置中调整）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun SubtitleRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = Color.Transparent,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (selected) {
                Icon(Icons.Outlined.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

private val SPEED_OPTIONS = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f, 3.0f)

// ---------------------------------------------------------------- 系统能力

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

private fun Activity?.toggleOrientation() {
    val activity = this ?: return
    activity.requestedOrientation = if (activity.resources.configuration.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE
    ) {
        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    } else {
        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }
}

private fun readBrightness(activity: Activity?): Float {
    val windowValue = activity?.window?.attributes?.screenBrightness ?: -1f
    if (windowValue >= 0f) return windowValue
    val resolver = activity?.contentResolver ?: return 0.5f
    val system = runCatching {
        Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS)
    }.getOrDefault(128)
    return (system / 255f).coerceIn(0.05f, 1f)
}

private fun applyBrightness(activity: Activity?, value: Float) {
    val window = activity?.window ?: return
    val attributes = window.attributes
    attributes.screenBrightness = value
    window.attributes = attributes
}

private val com.privateplayer.domain.model.SubtitleBackground.label: String
    get() = when (this) {
        com.privateplayer.domain.model.SubtitleBackground.NONE -> "无背景"
        com.privateplayer.domain.model.SubtitleBackground.SEMI_TRANSPARENT -> "半透明背景"
        com.privateplayer.domain.model.SubtitleBackground.SOLID -> "纯色背景"
    }

private val com.privateplayer.domain.model.SubtitleTextColor.label: String
    get() = when (this) {
        com.privateplayer.domain.model.SubtitleTextColor.WHITE -> "白色"
        com.privateplayer.domain.model.SubtitleTextColor.YELLOW -> "黄色"
        com.privateplayer.domain.model.SubtitleTextColor.CYAN -> "青色"
    }
