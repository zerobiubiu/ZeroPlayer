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
import android.view.ViewTreeObserver
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
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
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.privateplayer.core.common.format.formatDuration
import com.privateplayer.core.design.components.ActionSheet
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
    val seekDeltaFormat = stringResource(R.string.player_seek_delta_format)

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
    fun showFeedback(text: String) {
        gestureMessage = text
        gestureMessageRevision++
    }
    var showSubtitleSheet by remember { mutableStateOf(false) }
    var editingSubtitleAppearance by remember { mutableStateOf(false) }
    var showAudioSheet by remember { mutableStateOf(false) }
    var showAspectSheet by remember { mutableStateOf(false) }
    var showMoreSheet by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }
    val controlsVisible = state.controlsVisible && !state.locked
    val speedMediaId = state.mediaId
    val speedControl = remember(viewModel, view, speedMediaId, controlsVisible, LocalConfiguration.current) {
        PlayerSpeedControlState(
            beginPreview = { viewModel.beginSpeedPreview(surfaceOwner, speedMediaId) },
            hapticsEnabled = { viewModel.uiState.value.gesturePrefs.hapticsEnabled },
            haptic = { feedback ->
                view.performHapticFeedback(if (feedback == SpeedHaptic.LongPress) HapticFeedbackConstants.LONG_PRESS else HapticFeedbackConstants.CLOCK_TICK)
            },
        )
    }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) {
        speedControl.dismiss()
        viewModel.endTemporarySpeed()
    }
    DisposableEffect(view, speedControl) {
        val listener = ViewTreeObserver.OnWindowFocusChangeListener { focused ->
            if (!focused) {
                speedControl.dismiss()
                viewModel.endTemporarySpeed()
            }
        }
        view.viewTreeObserver.addOnWindowFocusChangeListener(listener)
        onDispose {
            if (view.viewTreeObserver.isAlive) view.viewTreeObserver.removeOnWindowFocusChangeListener(listener)
            speedControl.dismiss()
        }
    }

    // 关闭播放页即结束播放；按主页键退到桌面不触发返回，后台播放保留。
    val closePlayer = remember(viewModel, onBack) {
        {
            viewModel.closePlayback()
            onBack()
        }
    }
    BackHandler { if (speedControl.active) speedControl.dismiss() else closePlayer() }

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
                    showFeedback(volumeFormat.format(volume * 100 / maxVolume))
                    if (!seekActive) seekPreview = null
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

    // 独立面板由各自窗口维持沉浸式；选速板与视频共用窗口，不改系统栏。

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
    LaunchedEffect(controlsVisible, state.playback.status, seekActive, speedControl.active) {
        if (controlsVisible && state.playback.status == PlaybackStatus.PLAYING && !seekActive && !speedControl.active) {
            delay(4_000)
            if (!speedControl.active) viewModel.setControlsVisible(false)
        }
    }

    LaunchedEffect(state.error) {
        if (state.error != null) {
            speedControl.dismiss()
            viewModel.setControlsVisible(true)
        }
    }

    val handleGesture: (PlayerGesture) -> Unit = { gesture ->
        when (gesture) {
                    PlayerGesture.SingleTap -> viewModel.toggleControls()

                    PlayerGesture.DoubleTap.PlayPause -> if (!state.locked) viewModel.playPause()

                    PlayerGesture.DoubleTap.Rewind, PlayerGesture.DoubleTap.Forward -> {
                        if (!state.locked && state.playback.durationMs > 0L) {
                            val rewind = gesture == PlayerGesture.DoubleTap.Rewind
                            val seconds = state.playbackPrefs.seekStepSeconds
                            viewModel.seekBy((if (rewind) -seconds else seconds) * 1_000L)
                            seekPreview = null
                            showFeedback(seekDeltaFormat.format(if (rewind) "−" else "+", seconds.toString()))
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
                                showFeedback(brightnessFormat.format((brightness * 100).toInt()))
                            }

                            DragMode.VOLUME -> {
                                volume = (baseVolume - gesture.dyFraction * maxVolume * 1.5f).roundToInt()
                                    .coerceIn(0, maxVolume)
                                audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
                                showFeedback(volumeFormat.format(volume * 100 / maxVolume))
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
                        }
                    }

                    PlayerGesture.LongPressEnd -> {
                        viewModel.endTemporarySpeed()
                    }
                }
    }

    PlayerSpeedMenuHost(speedControl, state.playback.speed, feedback = { candidate ->
        val error = state.error
        val fromFan = candidate >= SpeedFanGeometry.Origin
        val actual = state.playback.speed
        val confirmed = state.playback.mediaId == state.mediaId &&
            (if (fromFan) SpeedPresets[candidate] == actual else state.surfaceHoldSpeed == actual)
        if (error != null || confirmed) PlayerFeedbackLayer(avoidFan = if (fromFan && error == null) speedControl.geometry else null) {
            if (error != null) PlayerErrorBanner(error, onDismiss = viewModel::consumeError)
            else PlayerTemporarySpeedHud(actual, describedByFan = fromFan, modifier = if (fromFan) Modifier.onGloballyPositioned {
                speedControl.bubble.hudInRoot = it.boundsInRoot()
            } else Modifier)
        }
    }) {
        // 手势只盖在视频画面上：按钮所在的控件层在它之上，不会被滑动识别抢走点击。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .playerGestures(enabled = !state.locked && !speedControl.active) { gesture ->
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
        val preview = seekPreview
        val message = gestureMessage
        val showReplay = state.playback.status == PlaybackStatus.ENDED && state.controlsVisible && !state.locked
        if (state.error == null && state.surfaceHoldSpeed == null && !speedControl.expanded && (preview != null || message != null || showReplay)) {
            PlayerFeedbackLayer {
                when {
                    preview != null -> PlayerSeekGestureOverlay(preview)
                    message != null -> PlayerFeedbackPill(message)
                    else -> Surface(color = Color.Black.copy(alpha = 0.7f), shape = MaterialTheme.shapes.medium, onClick = viewModel::playPause) {
                        Text(stringResource(R.string.player_ended_replay), style = MaterialTheme.typography.titleSmall, color = Color.White,
                            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md))
                    }
                }
            }
        }

        // 控件层独立：顶部、底部与浮层按钮优先接收触摸。
        // 扇面上方不露出被截断的片名或时间；只停绘，按钮布局与 DOWN 已建立的指针链仍在。
        Box(modifier = Modifier.fillMaxSize().drawWithContent { if (!speedControl.expanded) drawContent() }) {
            Column(modifier = Modifier.fillMaxSize()) {
                AnimatedVisibility(visible = controlsVisible, enter = fadeIn(), exit = fadeOut()) {
                    PlayerTopBar(
                        title = state.title,
                        onBack = closePlayer,
                        onRotate = { activity?.toggleOrientation() },
                        onOpenSubtitle = { showSubtitleSheet = true },
                        onOpenMore = { showMoreSheet = true },
                    )
                }
                Spacer(Modifier.weight(1f))
                AnimatedVisibility(visible = controlsVisible, enter = fadeIn(), exit = fadeOut()) {
                    PlayerBottomControls(
                        snapshot = state.playback,
                        seekStepSeconds = state.playbackPrefs.seekStepSeconds,
                        onSeekPreview = { fraction ->
                            val duration = state.playback.durationMs
                            if (duration > 0) {
                                seekPreview = null
                                val target = (fraction * duration).toLong()
                                showFeedback(if (target == 0L) "00:00" else formatDuration(target))
                            }
                        },
                        onSeekCommit = { fraction ->
                            val duration = state.playback.durationMs
                            if (duration > 0) viewModel.seekTo((fraction * duration).toLong())
                        },
                        onSeekBack = { viewModel.seekBy(-state.playbackPrefs.seekStepSeconds * 1_000L) },
                        onSeekForward = { viewModel.seekBy(state.playbackPrefs.seekStepSeconds * 1_000L) },
                        onPlayPause = viewModel::playPause,
                        speedControl = { modifier -> PlayerSpeedButton(state.playback.speed, speedControl, modifier) },
                        onOpenQueue = { showQueueSheet = true },
                    )
                }
            }
        }

        if (editingSubtitleAppearance) {
            PlayerSubtitleAppearancePanel(
                preferences = state.subtitlePrefs,
                actions = remember(viewModel) {
                    com.privateplayer.core.design.components.SubtitleAppearanceActions(
                        viewModel::setSubtitleFontScale, viewModel::setSubtitleFont, viewModel::setSubtitleFontStyle,
                        viewModel::setSubtitleTextColor, viewModel::setSubtitleBackground,
                        viewModel::setSubtitleVerticalOffset, viewModel::setSubtitleOverrideAss,
                    )
                },
                onBack = { editingSubtitleAppearance = false; showSubtitleSheet = true },
                onDone = { editingSubtitleAppearance = false },
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }

        if (state.locked) {
            LockedIndicator(
                onUnlock = viewModel::toggleLock,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp),
            )
        }

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
            title = stringResource(R.string.player_more),
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
            onAppearance = { showSubtitleSheet = false; editingSubtitleAppearance = true },
            onDismiss = { showSubtitleSheet = false },
        )
    }
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
        onClick = onLock,
    ),
    SheetAction(
        label = stringResource(R.string.player_audio_sheet_title),
        icon = Icons.Outlined.VolumeUp,
        kind = SheetActionKind.Navigation,
        supportingText = state.audioTracks.firstOrNull { it.id == state.selectedAudioTrackId }?.label,
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


/**
 * 单选行：字幕轨、播放队列条目与播放模式共用。
 *
 * 当前项显示勾选并用容器色高亮，读屏播报“已选中”，与其它面板的
 * 单选语义保持一致（F-16）。
 */
@Composable
internal fun SelectionRow(label: String, selected: Boolean, maxLines: Int = 1, onClick: () -> Unit) {
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
                maxLines = maxLines,
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
        Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(bottom = Spacing.lg)) {
            Text(
                text = when {
                    state.queue.isEmpty() -> stringResource(R.string.player_queue_empty_title)
                    state.queueIndex in state.queue.indices -> stringResource(R.string.player_queue_title, state.queueIndex + 1, state.queue.size)
                    else -> stringResource(R.string.player_queue)
                },
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            )
            if (state.queue.isNotEmpty()) {
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
