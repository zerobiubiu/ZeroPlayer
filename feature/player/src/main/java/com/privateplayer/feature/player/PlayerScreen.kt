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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PlaylistPlay
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.RepeatOne
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.VolumeOff
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
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
import com.privateplayer.core.design.components.EmptyState
import com.privateplayer.core.design.components.ImmersiveSheetWindowEffect
import com.privateplayer.core.design.components.SheetAction
import com.privateplayer.core.design.components.SheetActionKind
import com.privateplayer.core.design.theme.Radius
import com.privateplayer.core.design.theme.Spacing
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
    // 手势反馈文案在组合期解析模板，回调中只做格式化，配置变化后仍取最新资源。
    val volumeFormat = stringResource(R.string.player_gesture_volume)
    val brightnessFormat = stringResource(R.string.player_gesture_brightness)
    val rewindFormat = stringResource(R.string.player_rewind_seconds)
    val forwardFormat = stringResource(R.string.player_forward_seconds)
    val tempSpeedFormat = stringResource(R.string.player_gesture_temp_speed)

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
                    gestureMessage = volumeFormat.format(volume * 100 / maxVolume)
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

    // 面板是独立窗口，系统栏由各面板自身的窗口处理（见 ActionSheet 的 immersive 参数）。
    // 这里不再按面板状态重复隐藏 Activity 窗口的系统栏：那是旧实现，
    // 对面板窗口无效，且与 DisposableEffect 中的恢复逻辑相互竞争。

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
                            gestureMessage = (if (gesture.leftSide) rewindFormat else forwardFormat)
                                .format(state.playbackPrefs.seekStepSeconds)
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
                                gestureMessage = brightnessFormat.format((brightness * 100).toInt())
                            }

                            DragMode.VOLUME -> {
                                volume = (baseVolume - gesture.dyFraction * maxVolume * 1.5f).roundToInt()
                                    .coerceIn(0, maxVolume)
                                audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
                                gestureMessage = volumeFormat.format(volume * 100 / maxVolume)
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
                            gestureMessage = tempSpeedFormat.format(state.playbackPrefs.longPressSpeed.toString())
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
                        onRotate = { activity?.toggleOrientation() },
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
                    Text(text = stringResource(R.string.player_ended_replay), style = MaterialTheme.typography.titleSmall, color = Color.White)
                }
            }
        }
    }

    if (showSpeedSheet) {
        ActionSheet(
            title = stringResource(R.string.player_speed_sheet_title),
            immersive = true,
            actions = SPEED_OPTIONS.map { speed ->
                SheetAction(
                    label = stringResource(R.string.player_speed_format, speed.toString()),
                    icon = Icons.Outlined.Speed,
                    kind = SheetActionKind.SingleChoice,
                    selected = speed == state.playback.speed,
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
            title = stringResource(R.string.player_aspect),
            immersive = true,
            actions = aspectSheetActions(
                current = state.playback.aspect,
                onPick = { aspect ->
                    viewModel.setAspect(aspect)
                    showAspectSheet = false
                },
            ),
            onDismiss = { showAspectSheet = false },
        )
    }

    if (showAudioSheet) {
        ActionSheet(
            title = stringResource(R.string.player_audio_sheet_title),
            immersive = true,
            actions = if (state.audioTracks.isEmpty()) {
                listOf(
                    SheetAction(
                        label = stringResource(R.string.player_audio_none),
                        icon = Icons.Outlined.VolumeOff,
                        kind = SheetActionKind.Status,
                        enabled = false,
                        onClick = {},
                    ),
                )
            } else {
                state.audioTracks.map { track ->
                    SheetAction(
                        label = track.label,
                        icon = Icons.Outlined.VolumeUp,
                        kind = SheetActionKind.SingleChoice,
                        selected = track.id == state.selectedAudioTrackId,
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
            immersive = true,
            actions = moreSheetActions(
                state = state,
                onLock = {
                    viewModel.toggleLock()
                    showMoreSheet = false
                },
                onOpenAudio = {
                    showMoreSheet = false
                    showAudioSheet = true
                },
                onOpenAspect = {
                    showMoreSheet = false
                    showAspectSheet = true
                },
            ),
            onDismiss = { showMoreSheet = false },
        )
    }

    if (showQueueSheet) {
        PlaybackQueueSheet(
            state = state,
            onSelectItem = viewModel::playQueueItem,
            onPlayPrevious = {
                viewModel.playPrevious()
                showQueueSheet = false
            },
            onPlayNext = {
                viewModel.playNext()
                showQueueSheet = false
            },
            onSelectMode = { mode ->
                viewModel.setPlayMode(mode)
                showQueueSheet = false
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

@Composable
private fun audioLabel(state: PlayerUiState): String {
    val track = state.audioTracks.firstOrNull { it.id == state.selectedAudioTrackId }
    return track?.label ?: stringResource(R.string.player_audio_default)
}

/** 播放模式各自使用语义相符的图标，避免四种模式共用同一个图标。 */
private val PlayMode.icon: ImageVector
    get() = when (this) {
        PlayMode.SEQUENTIAL -> Icons.Outlined.PlaylistPlay
        PlayMode.REPEAT_ONE -> Icons.Outlined.RepeatOne
        PlayMode.REPEAT_ALL -> Icons.Outlined.Repeat
        PlayMode.SHUFFLE -> Icons.Outlined.Shuffle
    }

/**
 * “画面比例”面板的条目（F-19：与“旋转屏幕”完全分离）。
 * 抽成共享构建器，保证页面与仪器化测试断言同一份内容。
 */
@Composable
fun aspectSheetActions(current: VideoAspect, onPick: (VideoAspect) -> Unit): List<SheetAction> =
    VideoAspect.entries.map { aspect ->
        SheetAction(
            label = stringResource(aspect.labelRes),
            icon = Icons.Outlined.AspectRatio,
            kind = SheetActionKind.SingleChoice,
            selected = aspect == current,
            onClick = { onPick(aspect) },
        )
    }

/**
 * “更多”面板的条目：锁定屏幕、音轨与画面比例导航。
 * 旋转屏幕已提为顶栏独立按钮（F-19），此处不再保留第二入口；
 * “播放引擎”技术行已在 F-17 移除。
 */
@Composable
fun moreSheetActions(
    state: PlayerUiState,
    onLock: () -> Unit,
    onOpenAudio: () -> Unit,
    onOpenAspect: () -> Unit,
): List<SheetAction> = listOf(
    SheetAction(
        label = stringResource(R.string.player_lock_screen),
        icon = Icons.Outlined.Lock,
        kind = SheetActionKind.Action,
        supportingText = stringResource(R.string.player_lock_screen_desc),
        onClick = onLock,
    ),
    SheetAction(
        label = stringResource(R.string.player_audio_sheet_title),
        icon = Icons.Outlined.VolumeUp,
        kind = SheetActionKind.Navigation,
        supportingText = audioLabel(state),
        onClick = onOpenAudio,
    ),
    SheetAction(
        label = stringResource(R.string.player_aspect),
        icon = Icons.Outlined.AspectRatio,
        kind = SheetActionKind.Navigation,
        supportingText = stringResource(state.playback.aspect.labelRes),
        onClick = onOpenAspect,
    ),
)

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
        ImmersiveSheetWindowEffect()
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                text = stringResource(R.string.player_subtitle),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            SelectionRow(
                label = stringResource(R.string.player_subtitle_off),
                selected = state.selectedSubtitleTrackId == null,
                onClick = { onSelectTrack(null) },
            )
            state.subtitleTracks.forEach { track ->
                SelectionRow(
                    label = track.label,
                    selected = track.id == state.selectedSubtitleTrackId,
                    onClick = { onSelectTrack(track.id) },
                )
            }
            if (state.subtitleTracks.isEmpty()) {
                Text(
                    text = stringResource(R.string.player_subtitle_none_hint),
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
                    Text(text = stringResource(R.string.player_subtitle_delay), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = stringResource(R.string.player_subtitle_delay_value, state.subtitleDelayMs),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { onAdjustDelay(-state.subtitlePrefs.delayStepMs.toLong()) }) {
                    Text(stringResource(R.string.player_subtitle_delay_minus, state.subtitlePrefs.delayStepMs))
                }
                TextButton(onClick = { onAdjustDelay(state.subtitlePrefs.delayStepMs.toLong()) }) {
                    Text(stringResource(R.string.player_subtitle_delay_plus, state.subtitlePrefs.delayStepMs))
                }
                TextButton(onClick = onResetDelay) { Text(stringResource(R.string.player_subtitle_delay_reset)) }
            }
            Text(
                text = stringResource(
                    R.string.player_subtitle_style_summary,
                    (state.subtitlePrefs.fontSizeScale * 100).toInt(),
                    stringResource(state.subtitlePrefs.background.labelRes),
                    stringResource(state.subtitlePrefs.textColor.labelRes),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
    }
}

/**
 * 单选行：字幕轨、播放队列条目与播放模式共用。
 *
 * 当前项显示勾选并用容器色高亮，读屏播报“已选中”，与其它面板的
 * 单选语义保持一致（F-16）。
 */
@Composable
private fun SelectionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        Color.Transparent
    }
    val titleColor = if (selected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(containerColor)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (selected) {
                Icon(
                    Icons.Outlined.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

/**
 * 播放队列面板（F-20）。
 *
 * 上半部渲染真实队列：仅当前播放项高亮并带勾选（沿用 F-16 单选语义），
 * 点击其它条目只表达跳转意图，受理与执行由播放会话负责（[PlayerViewModel.playQueueItem]）。
 * 下半部保留上一集/下一集与播放模式。队列限高懒加载，打开或当前项变化时定位到当前项。
 * 面板窗口复用 [ImmersiveSheetWindowEffect] 保持沉浸式（F-38）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackQueueSheet(
    state: PlayerUiState,
    onSelectItem: (Int) -> Unit,
    onPlayPrevious: () -> Unit,
    onPlayNext: () -> Unit,
    onSelectMode: (PlayMode) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val listState = rememberLazyListState()
    LaunchedEffect(state.queueIndex) {
        if (state.queueIndex in state.queue.indices) {
            listState.scrollToItem(state.queueIndex)
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        ImmersiveSheetWindowEffect()
        Column(modifier = Modifier.navigationBarsPadding().padding(bottom = Spacing.lg)) {
            Text(
                text = stringResource(
                    R.string.player_queue_title,
                    (state.queueIndex + 1).coerceAtLeast(1),
                    state.queue.size.coerceAtLeast(1),
                ),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            )
            if (state.queue.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.QueueMusic,
                    title = stringResource(R.string.player_queue_empty_title),
                    message = stringResource(R.string.player_queue_empty_message),
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .fillMaxHeight(0.5f)
                        .testTag("播放队列列表"),
                ) {
                    itemsIndexed(state.queue, key = { index, item -> "${item.mediaId}:${item.path}:$index" }) { index, item ->
                        // 隐私：只展示文件名，不展示完整路径。
                        SelectionRow(
                            label = item.path.substringAfterLast('/').ifBlank { item.path },
                            selected = index == state.queueIndex,
                            onClick = { onSelectItem(index) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(Spacing.sm))
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg),
            ) {
                QueueActionButton(
                    label = stringResource(R.string.player_previous),
                    icon = Icons.Outlined.SkipPrevious,
                    enabled = state.queueIndex > 0,
                    onClick = onPlayPrevious,
                    modifier = Modifier.weight(1f),
                )
                QueueActionButton(
                    label = stringResource(R.string.player_next),
                    icon = Icons.Outlined.SkipNext,
                    enabled = state.hasNext,
                    onClick = onPlayNext,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(Spacing.sm))
            PlayMode.entries.forEach { mode ->
                SelectionRow(
                    label = stringResource(mode.labelRes),
                    selected = mode == state.playMode,
                    onClick = { onSelectMode(mode) },
                )
            }
        }
    }
}

/** 队列面板中的上一集/下一集动作行：是动作不是选择，不带任何勾选标记。 */
@Composable
private fun QueueActionButton(
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(Radius.thumbnail),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.heightIn(min = Spacing.xxxl),
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.md),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(Spacing.sm))
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
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
