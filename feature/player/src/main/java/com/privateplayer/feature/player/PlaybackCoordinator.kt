package com.privateplayer.feature.player

import android.content.Context
import android.view.Surface
import com.privateplayer.core.common.di.ApplicationScope
import com.privateplayer.core.common.di.IoDispatcher
import com.privateplayer.core.playback.PlaybackEngine
import com.privateplayer.core.playback.PlaybackEngineProvider
import com.privateplayer.core.playback.session.SessionMedia
import com.privateplayer.domain.model.OperationOutcome
import com.privateplayer.domain.model.VideoAspect
import com.privateplayer.domain.repository.FileTreeRepository
import com.privateplayer.domain.repository.MediaFileLocator
import com.privateplayer.domain.repository.MediaRepository
import com.privateplayer.domain.repository.SettingsRepository
import com.privateplayer.domain.repository.SubtitleStore
import com.privateplayer.domain.usecase.SavePlaybackProgressUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 由应用层实现：从可见页面启动媒体服务，并在关闭播放页时结束它。 */
interface PlaybackServiceStarter {
    /** 启动媒体服务；失败时返回可向用户展示的原因。 */
    fun start(): String?

    /** 请求已运行的媒体服务结束播放并释放引擎；服务未运行时什么都不做。 */
    fun stop()
}

internal data class PlaybackProgress(
    val mediaId: Long,
    val positionMs: Long,
    val durationMs: Long,
    val watchedMs: Long,
    val playIncrement: Int,
)

private sealed interface ProgressWork {
    data class Save(val entry: PlaybackProgress) : ProgressWork
    data class Barrier(val completion: CompletableDeferred<Unit>) : ProgressWork
}

/** Media3 适配器与应用播放器共享的同一播放实例。 */
data class PlaybackLease(
    val engine: PlaybackEngine,
    val media: StateFlow<SessionMedia?>,
    val executeForMedia: suspend (Long, () -> Unit) -> Boolean,
    val registerCommand: (Int) -> Long,
    val isCommandCurrent: (Int, Long) -> Boolean,
    val registerSpeedRequest: (Float) -> Long,
    val applySpeedRequest: (Long, Float) -> Unit,
    val discardSpeedRequest: (Long) -> Unit,
)

/** 唯一协调器；服务管理实例寿命，页面只借用状态和操作。所有入口均在主线程调用。 */
@Singleton
class PlaybackCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaRepository: MediaRepository,
    private val fileTreeRepository: FileTreeRepository,
    private val fileLocator: MediaFileLocator,
    private val subtitleStore: SubtitleStore,
    private val settingsRepository: SettingsRepository,
    private val engineProvider: PlaybackEngineProvider,
    private val savePlaybackProgress: SavePlaybackProgressUseCase,
    private val serviceStarter: PlaybackServiceStarter,
    @ApplicationScope private val applicationScope: CoroutineScope,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private class FocusCallbacks(
        @Volatile var prepare: () -> Boolean,
        @Volatile var abandoned: () -> Unit,
    )

    private data class Active(
        var owner: Any,
        val session: PlaybackSession,
        val scope: CoroutineScope,
        val predecessor: Deferred<Unit>,
        val focusCallbacks: FocusCallbacks,
    )

    private val state = MutableStateFlow(PlayerUiState(engineLabel = engineProvider.backendLabel))
    val uiState: StateFlow<PlayerUiState> = state
    private val progress = Channel<ProgressWork>(Channel.UNLIMITED)
    @Volatile private var active: Active? = null
    private var pendingMediaId: Long? = null
    private var pendingAutoStart = true
    private var lastMediaId: Long? = null
    @Volatile private var openGeneration = 0L
    @Volatile private var openingMediaId: Long? = null
    private var surfaceOwner: Any? = null
    private var surface: Surface? = null
    private var surfaceWidth = 0
    private var surfaceHeight = 0
    private var releaseBarrier: Deferred<Unit> = CompletableDeferred<Unit>().apply { complete(Unit) }

    init {
        // 单一写入队列保证旧进度不会在新进度之后落库，页面或服务销毁也不会取消已提交的记录。
        applicationScope.launch(ioDispatcher) {
            for (work in progress) {
                if (work is ProgressWork.Barrier) {
                    work.completion.complete(Unit)
                    continue
                }
                val entry = (work as ProgressWork.Save).entry
                val error = try {
                    when (val result = savePlaybackProgress(
                        entry.mediaId, entry.positionMs, entry.durationMs, entry.watchedMs, entry.playIncrement,
                    )) {
                        is OperationOutcome.Failure -> result.message
                        OperationOutcome.Success -> null
                    }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (exception: Exception) {
                    exception.message ?: "保存播放进度失败"
                }
                if (error != null) launch(Dispatchers.Main.immediate) { reportError("保存播放进度失败：$error") }
            }
        }
    }

    /** 服务 onCreate 调用；重复创建只借用已有实例，旧服务销毁不能释放新服务的实例。 */
    fun acquire(
        owner: Any,
        prepareForPlayback: () -> Boolean = { true },
        onPlaybackPaused: () -> Unit = {},
    ): PlaybackLease {
        active?.let { return createLease(it.session) }
        val predecessor = releaseBarrier
        val focusCallbacks = FocusCallbacks(prepareForPlayback, onPlaybackPaused)
        val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
        lateinit var session: PlaybackSession
        val engine = FocusPlaybackEngine(
            context = context,
            delegate = engineProvider.create(),
            awaitPreviousRelease = { predecessor.await() },
            onError = { message -> scope.launch(Dispatchers.Main.immediate) { reportError(message) } },
            prepareForeground = { focusCallbacks.prepare() },
            onFocusAbandoned = { focusCallbacks.abandoned() },
        )
        session = PlaybackSession(
            engine = engine,
            engineDetail = engineProvider.backendDetail,
            mediaRepository = mediaRepository,
            fileTreeRepository = fileTreeRepository,
            fileLocator = fileLocator,
            subtitleStore = subtitleStore,
            settingsRepository = settingsRepository,
            saveProgress = { progress.trySend(ProgressWork.Save(it)) },
            scope = scope,
            ioDispatcher = ioDispatcher,
        )
        active = Active(owner, session, scope, predecessor, focusCallbacks)
        scope.launch { session.uiState.collect { if (active?.session === session) state.value = it } }
        surface?.let {
            engine.attachSurface(it)
            if (surfaceWidth > 0 && surfaceHeight > 0) engine.resizeSurface(surfaceWidth, surfaceHeight)
        }
        pendingMediaId?.let { queueOpen(session, it, pendingAutoStart) }
        pendingMediaId = null
        return createLease(session)
    }

    /** 新服务的 MediaSession 构造成功后才交接，失败的重复创建不能释放旧实例。 */
    fun claim(
        owner: Any,
        prepareForPlayback: () -> Boolean = { true },
        onPlaybackPaused: () -> Unit = {},
    ) {
        val current = active ?: return
        current.owner = owner
        current.focusCallbacks.prepare = prepareForPlayback
        current.focusCallbacks.abandoned = onPlaybackPaused
    }

    private fun createLease(session: PlaybackSession) = PlaybackLease(
        engine = session.engine,
        media = session.media,
        executeForMedia = session::executeForMedia,
        registerCommand = session::registerCommand,
        isCommandCurrent = session::isCommandCurrent,
        registerSpeedRequest = session::registerSpeedRequest,
        applySpeedRequest = session::applySpeedRequest,
        discardSpeedRequest = session::discardSpeedRequest,
    )

    /** 先完成已入队的旧进度，再按最新请求加载，避免重建服务时读取旧断点。 */
    private fun queueOpen(session: PlaybackSession, mediaId: Long, autoStart: Boolean) {
        val current = active?.takeIf { it.session === session } ?: return
        val generation = ++openGeneration
        openingMediaId = mediaId
        applicationScope.launch(ioDispatcher) {
            try {
                current.predecessor.await()
                val done = CompletableDeferred<Unit>()
                progress.send(ProgressWork.Barrier(done))
                done.await()
                withContext(Dispatchers.Main.immediate) {
                    if (openGeneration == generation && active?.session === session) {
                        session.open(mediaId, autoStart)
                        openingMediaId = null
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                withContext(Dispatchers.Main.immediate) {
                    if (openGeneration == generation && active?.session === session) {
                        openingMediaId = null
                        session.reportError("上一个播放实例未能释放：${error.message ?: "未知错误"}")
                    }
                }
            }
        }
    }

    /** 必须在服务先释放 MediaSession 和适配器后调用。 */
    fun release(owner: Any): Deferred<Unit>? {
        val current = active ?: return null
        if (current.owner !== owner) return null
        lastMediaId = current.session.media.value?.mediaId ?: lastMediaId
        openGeneration++
        openingMediaId = null
        current.session.engine.beginClosing()
        current.focusCallbacks.prepare = { false }
        current.focusCallbacks.abandoned = {}
        current.scope.cancel()
        val predecessor = releaseBarrier
        val cleanup = applicationScope.async(ioDispatcher, start = CoroutineStart.LAZY) {
            var failure: Throwable? = try {
                predecessor.await()
                null
            } catch (error: Throwable) {
                if (error is CancellationException || (error !is Exception && error !is LinkageError)) throw error
                error
            }
            try {
                current.session.closeAndRelease()
            } catch (error: Throwable) {
                if (error is CancellationException || (error !is Exception && error !is LinkageError)) throw error
                if (failure == null) failure = error else failure?.addSuppressed(error)
            }
            failure?.let { error ->
                applicationScope.launch(Dispatchers.Main.immediate) {
                    reportError("释放播放引擎失败：${error.message ?: error.javaClass.simpleName}")
                }
                throw IllegalStateException("上一个播放实例未能释放", error)
            }
            Unit
        }
        releaseBarrier = cleanup
        active = null
        state.update { it.copy(isLoading = false, playback = com.privateplayer.core.playback.PlaybackSnapshot()) }
        cleanup.start()
        return cleanup
    }

    /** 只从页面 ON_START 调用，保证媒体服务启动发生在应用可见时。 */
    fun openFromVisibleUi(mediaId: Long, requestMedia: Boolean): Boolean {
        val error = try {
            serviceStarter.start()
        } catch (exception: Exception) {
            exception.message ?: "无法启动播放服务"
        }
        if (error != null) {
            reportError("无法启动后台播放：$error")
            return false
        }
        val session = active?.session
        if (requestMedia) {
            lastMediaId = mediaId
            if (session == null) {
                pendingMediaId = mediaId
                pendingAutoStart = true
            } else {
                queueOpen(session, mediaId, true)
            }
        } else if (pendingMediaId == null && openingMediaId == null &&
            (session == null || session.media.value == null)
        ) {
            val restoreId = lastMediaId ?: mediaId
            if (session == null) {
                pendingMediaId = restoreId
                pendingAutoStart = false
            } else {
                queueOpen(session, restoreId, false)
            }
        }
        return true
    }

    /** 用户关闭播放页时结束本次播放；按主页键退到后台不属于关闭，服务继续播放。 */
    fun closeFromPlayerUi() {
        if (active == null) return
        try {
            serviceStarter.stop()
        } catch (exception: Exception) {
            reportError("无法停止播放：${exception.message ?: "播放服务不可用"}")
        }
    }

    /** 供系统服务 dump 定位请求、装载与真实引擎不一致；只读，不创建或借用新实例。 */
    fun diagnosticState(): String {
        val current = active
        val sessionMedia = current?.session?.media?.value
        return "pendingMediaId=$pendingMediaId, openingMediaId=$openingMediaId, lastMediaId=$lastMediaId, " +
            "load=${current?.session?.diagnosticState()}, " +
            "sessionMediaId=${sessionMedia?.mediaId}, artworkBytes=${sessionMedia?.artworkData?.size}, " +
            "engine=${current?.session?.engine?.snapshot?.value}, ui=${uiState.value}"
    }

    fun reportError(message: String) {
        val session = active?.session
        if (session != null) session.reportError(message)
        else state.update { it.copy(isLoading = false, error = message) }
    }

    fun onForegroundServiceFailure(owner: Any, detail: String? = null) {
        val current = active ?: return
        if (current.owner !== owner) return
        current.session.pauseForFailure()
        reportError(if (detail.isNullOrBlank()) "系统不允许启动前台播放服务，请返回播放器后重试" else "无法启动前台播放服务：$detail")
    }

    fun activateSurfaceOwner(owner: Any) {
        if (surfaceOwner === owner) return
        if (surfaceOwner != null) active?.session?.endTemporarySpeed()
        active?.session?.engine?.attachSurface(null)
        surfaceOwner = owner
        surface = null
        surfaceWidth = 0
        surfaceHeight = 0
    }

    fun attachSurface(owner: Any, newSurface: Surface) {
        if (surfaceOwner !== owner) return
        surface = newSurface
        active?.session?.engine?.attachSurface(newSurface)
    }

    fun resizeSurface(owner: Any, target: Surface, width: Int, height: Int) {
        if (surfaceOwner !== owner || surface !== target) return
        surfaceWidth = width
        surfaceHeight = height
        active?.session?.engine?.resizeSurface(width, height)
    }

    fun detachSurface(owner: Any, target: Surface? = null) {
        if (surfaceOwner !== owner || (target != null && surface !== target)) return
        surface = null
        surfaceWidth = 0
        surfaceHeight = 0
        active?.session?.engine?.attachSurface(null)
        if (target == null) {
            surfaceOwner = null
            active?.session?.endTemporarySpeed()
        }
    }

    fun playPause() { active?.session?.playPause() }
    fun seekTo(positionMs: Long) { active?.session?.seekTo(positionMs) }
    fun seekBy(deltaMs: Long) { active?.session?.seekBy(deltaMs) }
    fun setSpeed(speed: Float) { active?.session?.setSpeed(speed) }
    fun setAspect(aspect: VideoAspect) { active?.session?.setAspect(aspect) }
    fun selectAudioTrack(id: String?) { active?.session?.selectAudioTrack(id) }
    fun selectSubtitleTrack(id: String?) { active?.session?.selectSubtitleTrack(id) }
    fun adjustSubtitleDelay(deltaMs: Long) { active?.session?.adjustSubtitleDelay(deltaMs) }
    fun resetSubtitleDelay() { active?.session?.resetSubtitleDelay() }
    fun toggleControls() { active?.session?.toggleControls() }
    fun setControlsVisible(visible: Boolean) { active?.session?.setControlsVisible(visible) }
    fun toggleLock() { active?.session?.toggleLock() }
    fun setPlayMode(mode: PlayMode) { active?.session?.setPlayMode(mode) }
    fun playNext() { active?.session?.playNext() }
    fun playPrevious() { active?.session?.playPrevious() }
    fun beginTemporarySpeed(speed: Float) { active?.session?.beginTemporarySpeed(speed) }
    fun endTemporarySpeed() { active?.session?.endTemporarySpeed() }
    fun consumeError() {
        active?.session?.consumeError() ?: state.update { it.copy(error = null) }
    }
}
