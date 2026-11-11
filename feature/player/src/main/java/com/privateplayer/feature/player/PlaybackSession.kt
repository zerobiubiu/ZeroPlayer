package com.privateplayer.feature.player

import androidx.media3.common.Player
import com.privateplayer.core.playback.EngineMediaInfo
import com.privateplayer.core.playback.MediaTrack
import com.privateplayer.core.playback.PlaybackEvent
import com.privateplayer.core.playback.PlaybackSnapshot
import com.privateplayer.core.playback.PlaybackSource
import com.privateplayer.core.playback.PlaybackStatus
import com.privateplayer.core.playback.session.SessionMedia
import com.privateplayer.domain.model.AppSettings
import com.privateplayer.domain.model.GesturePreferences
import com.privateplayer.domain.model.PlaybackPreferences
import com.privateplayer.domain.model.SubtitlePreferences
import com.privateplayer.domain.model.Media
import com.privateplayer.domain.model.VideoAspect
import com.privateplayer.domain.repository.FileTreeRepository
import com.privateplayer.domain.repository.MediaFileLocator
import com.privateplayer.domain.repository.MediaRepository
import com.privateplayer.domain.repository.SettingsRepository
import com.privateplayer.domain.repository.SubtitleStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object PlayerRouteArgs {
    const val ARG_MEDIA_ID = "mediaId"
}

/** 媒体记录中表示“用户主动关闭字幕”的哨兵值；轨道 ID 均为数字，不会冲突。 */
internal const val SUBTITLE_OFF = "off"

/** 播放模式。 */
enum class PlayMode(val label: String) {
    SEQUENTIAL("顺序播放"),
    REPEAT_ONE("单个循环"),
    REPEAT_ALL("目录循环"),
    SHUFFLE("随机播放"),
}

data class QueueItem(
    val mediaId: Long,
    val path: String,
)

data class PlayerUiState(
    val isLoading: Boolean = true,
    val mediaId: Long = 0L,
    val title: String = "",
    val playback: PlaybackSnapshot = PlaybackSnapshot(),
    val audioTracks: List<MediaTrack> = emptyList(),
    val subtitleTracks: List<MediaTrack> = emptyList(),
    val selectedAudioTrackId: String? = null,
    val selectedSubtitleTrackId: String? = null,
    val subtitleDelayMs: Long = 0L,
    val subtitleFileNames: List<String> = emptyList(),
    val engineLabel: String = "",
    val engineDetail: String = "",
    val mediaInfo: EngineMediaInfo? = null,
    val queue: List<QueueItem> = emptyList(),
    val queueIndex: Int = 0,
    val playMode: PlayMode = PlayMode.SEQUENTIAL,
    val autoPlayNext: Boolean = true,
    val rememberProgress: Boolean = true,
    val controlsVisible: Boolean = true,
    val locked: Boolean = false,
    val error: String? = null,
    val ended: Boolean = false,
    val playbackPrefs: PlaybackPreferences = PlaybackPreferences(),
    val gesturePrefs: GesturePreferences = GesturePreferences(),
    val subtitlePrefs: SubtitlePreferences = SubtitlePreferences(),
) {
    val hasNext: Boolean get() = queueIndex < queue.lastIndex
}

/** 与 MediaSessionService 同生共死的播放业务；页面不拥有引擎。 */
internal class PlaybackSession(
    val engine: FocusPlaybackEngine,
    private val engineDetail: String,
    private val mediaRepository: MediaRepository,
    private val fileTreeRepository: FileTreeRepository,
    private val fileLocator: MediaFileLocator,
    private val subtitleStore: SubtitleStore,
    private val settingsRepository: SettingsRepository,
    private val saveProgress: (PlaybackProgress) -> Unit,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
) {
    @Volatile private var currentMedia: Media? = null
    /** 本次装载是否已按媒体记录恢复过字幕选择。 */
    private var subtitleSelectionRestored = false
    private var requestedMediaId: Long? = null
    private var loadJob: Job? = null
    @Volatile private var loadStage = "尚未装载"
    private val transitionMutex = Mutex()
    private var handlingEnd = false
    private var watchedDeltaMs = 0L
    private var lastTickPosition = 0L
    private var progressJob: Job? = null
    /** 本次装载是否已经计过播放次数：每次装载只 +1，避免每 5 秒保存进度都累加。 */
    private var playCountRecorded = false
    private var hasPlayed = false
    private data class SpeedRequest(val ticket: Long, val speed: Float, var issued: Boolean = false)
    private val speedLock = Any()
    private var nextTicket = 0L
    private val commandTickets = mutableMapOf<Int, Long>()
    private var pendingSpeed: SpeedRequest? = null
    private var speedBeforeTemporary: Float? = null
    private var requestedSpeed = 1.0f
    private val speedRequests = Channel<SpeedRequest>(Channel.CONFLATED)
    private val mutableMedia = MutableStateFlow<SessionMedia?>(null)
    val media: StateFlow<SessionMedia?> = mutableMedia

    private val state = MutableStateFlow(
        PlayerUiState(
            isLoading = true,
            engineLabel = engine.backendLabel,
            engineDetail = engineDetail,
        ),
    )

    val uiState: StateFlow<PlayerUiState> = state

    fun diagnosticState(): String =
        "requestedMediaId=$requestedMediaId, currentMediaId=${currentMedia?.id}, " +
            "relativePath=${currentMedia?.relativePath}, thumbnailPath=${currentMedia?.thumbnailPath}, " +
            "loadActive=${loadJob?.isActive}, loadStage=$loadStage"

    init {
        observeEngine()
        scope.launch(ioDispatcher) {
            for (request in speedRequests) {
                transitionMutex.withLock { applySpeedRequest(request.ticket, request.speed) }
            }
        }
        observeSettings()
    }

    @Synchronized
    fun open(mediaId: Long, autoStart: Boolean = true) {
        if (currentMedia?.id == mediaId) {
            if (!autoStart) return
            when (engine.snapshot.value.status) {
                PlaybackStatus.PAUSED -> {
                    onCurrentMedia(Player.COMMAND_PLAY_PAUSE) {
                        if (engine.snapshot.value.status == PlaybackStatus.PAUSED) engine.play()
                    }
                    return
                }
                PlaybackStatus.ENDED -> {
                    onCurrentMedia(Player.COMMAND_PLAY_PAUSE) {
                        if (engine.snapshot.value.status == PlaybackStatus.ENDED) {
                            engine.seekTo(0L)
                            engine.play()
                        }
                    }
                    return
                }
                PlaybackStatus.ERROR -> Unit
                else -> {
                    if (requestedMediaId != mediaId) loadJob?.cancel()
                    requestedMediaId = mediaId
                    return
                }
            }
        }
        if (requestedMediaId == mediaId && loadJob?.isActive == true) return
        requestedMediaId = mediaId
        loadJob?.cancel()
        loadJob = scope.launch {
            try {
                transitionMutex.withLock { loadInitial(mediaId, autoStart) }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                reportError(error.message ?: "无法打开文件")
            }
        }
    }

    /** 与切源共用门：旧媒体命令不能越过新的 load 作用于新媒体。 */
    suspend fun executeForMedia(expectedMediaId: Long, action: () -> Unit): Boolean =
        transitionMutex.withLock {
            if (mutableMedia.value?.mediaId != expectedMediaId || engine.snapshot.value.mediaId != expectedMediaId) {
                false
            } else {
                action()
                true
            }
        }

    fun registerCommand(command: Int): Long = synchronized(speedLock) {
        (++nextTicket).also { commandTickets[command] = it }
    }

    fun isCommandCurrent(command: Int, ticket: Long): Boolean = synchronized(speedLock) {
        commandTickets[command] == ticket
    }

    private fun onCurrentMedia(command: Int? = null, action: () -> Unit) {
        val mediaId = currentMedia?.id ?: return
        val ticket = command?.let(::registerCommand)
        scope.launch {
            executeForMedia(mediaId) {
                if (command == null || (ticket != null && isCommandCurrent(command, ticket))) action()
            }
        }
    }

    private fun observeSettings() {
        scope.launch {
            settingsRepository.observeSettings().collect { settings ->
                state.update {
                    it.copy(
                        playbackPrefs = settings.playback,
                        gesturePrefs = settings.gesture,
                        subtitlePrefs = settings.subtitle,
                        autoPlayNext = settings.playback.autoPlayNext,
                        rememberProgress = settings.playback.rememberProgress,
                    )
                }
                engine.setSubtitleStyle(settings.subtitle)
            }
        }
    }

    private fun observeEngine() {
        scope.launch {
            engine.snapshot.collect { snapshot ->
                transitionMutex.withLock {
                    if (snapshot.mediaId != currentMedia?.id) return@withLock
                    val previous = state.value.playback
                    if (snapshot.status == PlaybackStatus.PLAYING) {
                        hasPlayed = true
                        if (lastTickPosition > 0 && snapshot.positionMs > lastTickPosition) {
                            val delta = (snapshot.positionMs - lastTickPosition).coerceAtMost((snapshot.speed * 1_000L).toLong() + 500L)
                            watchedDeltaMs += delta
                        }
                    }
                    lastTickPosition = snapshot.positionMs
                    state.update {
                        it.copy(
                            playback = snapshot,
                            ended = snapshot.status == PlaybackStatus.ENDED,
                            error = snapshot.errorMessage ?: it.error,
                            isLoading = snapshot.status == PlaybackStatus.LOADING,
                        )
                    }
                    synchronized(speedLock) {
                        if (pendingSpeed?.let {
                                it.issued && snapshot === engine.snapshot.value &&
                                    kotlin.math.abs(it.speed - snapshot.speed) < 0.001f
                            } == true
                        ) pendingSpeed = null
                        if (pendingSpeed == null && speedBeforeTemporary == null) requestedSpeed = snapshot.speed
                    }
                    if (snapshot.status == PlaybackStatus.PLAYING) handlingEnd = false
                    if (previous.status != PlaybackStatus.PLAYING && snapshot.status == PlaybackStatus.PLAYING) {
                        startProgressTicker()
                        val info = engine.mediaInfo()
                        state.update { it.copy(mediaInfo = info) }
                    }
                    if (snapshot.status != PlaybackStatus.PLAYING) {
                        progressJob?.cancel()
                        progressJob = null
                        if (snapshot.status == PlaybackStatus.PAUSED &&
                            (previous.status != PlaybackStatus.PAUSED || snapshot.positionMs != previous.positionMs)
                        ) persistProgress()
                        if (snapshot.status == PlaybackStatus.ERROR && previous.status != PlaybackStatus.ERROR) {
                            persistProgress()
                            engine.pause()
                        }
                    }
                }
            }
        }
        scope.launch {
            engine.audioTracks.collect { tracks ->
                state.update {
                    it.copy(
                        audioTracks = tracks,
                        selectedAudioTrackId = tracks.firstOrNull { track -> track.isDefault }?.id,
                    )
                }
            }
        }
        scope.launch {
            engine.subtitleTracks.collect { tracks ->
                state.update {
                    it.copy(
                        subtitleTracks = tracks,
                        selectedSubtitleTrackId = tracks.firstOrNull { track -> track.isDefault }?.id,
                    )
                }
                restoreSubtitleSelection(tracks)
            }
        }
        scope.launch {
            engine.events.collect { event ->
                when (event) {
                    is PlaybackEvent.Ended -> handleEnded()
                    is PlaybackEvent.Failed -> {
                        if (engine.snapshot.value.mediaId == currentMedia?.id) {
                            state.update { it.copy(error = event.message) }
                        }
                    }
                    is PlaybackEvent.TrackChanged -> Unit
                }
            }
        }
    }

    private suspend fun loadInitial(mediaId: Long, autoStart: Boolean) {
        loadStage = "读取播放设置"
        val settings = settingsRepository.observeSettings().first()
        loadStage = "读取媒体记录"
        val media = mediaRepository.findById(mediaId) ?: run {
            state.update { it.copy(isLoading = false, error = "媒体不存在或已被删除") }
            return
        }

        loadStage = "建立同目录队列"
        val queue = buildQueue(media)
        val index = queue.indexOfFirst {
            it.mediaId == media.id || it.path == media.relativePath.removePrefix("media/")
        }.coerceAtLeast(0)
        state.update {
            it.copy(
                queue = queue,
                queueIndex = index,
                playMode = if (currentMedia == null) PlayMode.SEQUENTIAL else it.playMode,
                autoPlayNext = settings.playback.autoPlayNext,
                rememberProgress = settings.playback.rememberProgress,
            )
        }
        setSpeed(settings.playback.defaultSpeed)
        engine.setAspect(settings.playback.defaultAspect)
        playMedia(media, settings, autoStart = autoStart)
    }

    private suspend fun buildQueue(media: Media): List<QueueItem> {
        val path = media.relativePath.removePrefix("media/")
        val directory = path.substringBeforeLast('/', "")
        val queue = fileTreeRepository.list(directory).mapNotNull {
            if (!it.isDirectory && it.isVideo) QueueItem(it.mediaId ?: 0L, it.path) else null
        }
        return if (queue.any { it.path == path || it.mediaId == media.id }) queue
        else queue + QueueItem(media.id, path)
    }

    /** 加载并播放指定媒体；保留播放模式与轨道选择之外的引擎设置。 */
    private suspend fun playMedia(media: Media, settings: AppSettings, autoStart: Boolean) {
        loadStage = "解析私有媒体文件"
        val retry = currentMedia?.id == media.id && engine.snapshot.value.status == PlaybackStatus.ERROR
        val retryPosition = engine.snapshot.value.positionMs.takeIf { retry && hasPlayed }
        val fileInfo = fileLocator.mediaFile(media.relativePath)
        if (!fileInfo.exists) {
            persistProgress()
            engine.stop()
            mutableMedia.value = null
            currentMedia = null
            hasPlayed = false
            state.update {
                it.copy(
                    isLoading = false,
                    mediaId = media.id,
                    title = media.displayName,
                    error = "文件缺失：${media.relativePath}",
                )
            }
            return
        }
        val treePath = media.relativePath.removePrefix("media/")
        loadStage = "发现外挂及同目录字幕"
        val subtitles = (subtitleStore.list(media.id).map { it.absolutePath } +
            fileTreeRepository.matchingSubtitles(treePath)).distinct()
        val startPosition = retryPosition ?: if (settings.playback.autoResume && settings.playback.rememberProgress) {
            media.playbackPositionMs.takeIf { it > 5_000L && it < media.durationMs * 0.98 } ?: 0L
        } else {
            0L
        }
        val source = PlaybackSource(
            mediaId = media.id,
            title = media.displayName,
            absolutePath = fileInfo.absolutePath,
            durationHintMs = media.durationMs,
            subtitleFiles = subtitles,
        )
        loadStage = "读取会话封面"
        val artwork = withContext(ioDispatcher) {
            runCatching {
                val file = fileLocator.thumbnailFile(media.thumbnailPath) ?: return@runCatching null
                val expectedLength = file.length()
                if (expectedLength <= 0L || expectedLength > MAX_ARTWORK_BYTES) return@runCatching null
                file.inputStream().use { input ->
                    val bytes = ByteArray(expectedLength.toInt() + 1)
                    var length = 0
                    while (length < bytes.size) {
                        val count = input.read(bytes, length, bytes.size - length)
                        if (count < 0) break
                        if (count == 0) continue
                        length += count
                    }
                    if (length != expectedLength.toInt()) null else bytes.copyOf(length)
                }
            }.getOrNull()
        }
        if (engine.snapshot.value.status != PlaybackStatus.ENDED) persistProgress()
        mutableMedia.value = SessionMedia(
            mediaId = media.id,
            title = media.displayName.substringAfterLast('/').substringAfterLast('\\'),
            durationMs = media.durationMs,
            artworkData = artwork,
        )
        currentMedia = media
        subtitleSelectionRestored = false
        watchedDeltaMs = 0L
        lastTickPosition = startPosition
        if (!retry) {
            playCountRecorded = false
            hasPlayed = false
        }
        handlingEnd = false
        state.update {
            it.copy(
                isLoading = true,
                playback = PlaybackSnapshot(),
                audioTracks = emptyList(),
                subtitleTracks = emptyList(),
                selectedAudioTrackId = null,
                selectedSubtitleTrackId = null,
                mediaInfo = null,
                mediaId = media.id,
                title = media.displayName,
                subtitleFileNames = subtitles.map { path -> path.substringAfterLast('/') },
                error = null,
                ended = false,
            )
        }
        try {
            withContext(ioDispatcher) {
                loadStage = "初始化唯一引擎"
                engine.initialize()
                loadStage = "装载媒体并取得音频焦点"
                engine.load(source, startPosition, autoStart)
                loadStage = "已提交原生装载"
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            engine.pause()
            state.update { it.copy(isLoading = false, error = error.message ?: "无法播放此视频") }
        }
    }

    private fun handleEnded() {
        // 只在当前媒体确实处于结束态时处理，避免上一段播放的迟到事件把刚跳转的媒体顶走。
        scope.launch {
            transitionMutex.withLock {
                if (engine.snapshot.value.status != PlaybackStatus.ENDED ||
                    engine.snapshot.value.mediaId != currentMedia?.id || handlingEnd
                ) return@withLock
                handlingEnd = true
                try {
                    persistProgress(endOfPlayback = true)
                    val settings = settingsRepository.observeSettings().first()
                    val continued = when {
                        state.value.playMode == PlayMode.REPEAT_ONE -> {
                            engine.seekTo(0L)
                            engine.play()
                            true
                        }
                        !settings.playback.autoPlayNext -> false
                        state.value.playMode == PlayMode.SHUFFLE -> playQueueIndex(randomIndex(), settings)
                        state.value.hasNext -> playQueueIndex(state.value.queueIndex + 1, settings)
                        state.value.playMode == PlayMode.REPEAT_ALL -> playQueueIndex(0, settings)
                        else -> false
                    }
                    if (!continued) engine.pause()
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: Exception) {
                    engine.pause()
                    reportError(error.message ?: "无法自动播放下一文件")
                }
            }
        }
    }

    private fun randomIndex(): Int {
        val size = state.value.queue.size
        if (size <= 1) return 0
        var next = state.value.queueIndex
        while (next == state.value.queueIndex) next = (0 until size).random()
        return next
    }

    private suspend fun playQueueIndex(index: Int, settings: AppSettings): Boolean {
        val queue = state.value.queue
        if (index !in queue.indices) return false
        val target = queue[index]
        val mediaId = if (target.mediaId > 0L) target.mediaId else {
            try {
                fileTreeRepository.ensureMedia(target.path)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                state.update { it.copy(error = error.message ?: "无法打开文件") }
                return false
            }
        }
        val media = mediaRepository.findById(mediaId) ?: run {
            reportError("下一文件不存在或已被删除")
            return false
        }
        state.update { it.copy(queueIndex = index) }
        playMedia(media, settings, autoStart = true)
        return true
    }

    // ------------------------------------------------------------- 对外操作


    fun playPause() = onCurrentMedia(Player.COMMAND_PLAY_PAUSE) {
        when (engine.snapshot.value.status) {
            PlaybackStatus.PLAYING -> engine.pause()
            PlaybackStatus.ENDED -> {
                engine.seekTo(0L)
                engine.play()
            }
            else -> engine.play()
        }
    }

    fun pauseForFailure() = onCurrentMedia(Player.COMMAND_PLAY_PAUSE) { engine.pause() }

    fun seekTo(positionMs: Long) = onCurrentMedia(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM) { engine.seekTo(positionMs) }

    fun seekBy(deltaMs: Long) = onCurrentMedia(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM) {
        val snapshot = engine.snapshot.value
        val target = (snapshot.positionMs + deltaMs)
            .coerceIn(0L, snapshot.durationMs.coerceAtLeast(0L))
        engine.seekTo(target)
    }

    fun registerSpeedRequest(speed: Float): Long = synchronized(speedLock) {
        val normalized = speed.coerceIn(0.25f, 4.0f)
        val request = SpeedRequest(++nextTicket, normalized)
        pendingSpeed = request
        requestedSpeed = normalized
        request.ticket
    }

    /** 已持有切源门；旧票据不能覆盖更新的手势或系统命令。 */
    fun applySpeedRequest(ticket: Long, speed: Float) {
        val current = synchronized(speedLock) {
            pendingSpeed?.takeIf { it.ticket == ticket }?.let {
                it.issued = true
                true
            } ?: false
        }
        if (current) engine.setSpeedFromUi(speed.coerceIn(0.25f, 4.0f))
    }

    fun discardSpeedRequest(ticket: Long) {
        synchronized(speedLock) {
            if (pendingSpeed?.let { it.ticket == ticket && !it.issued } == true) {
                pendingSpeed = null
                if (speedBeforeTemporary == null) requestedSpeed = engine.snapshot.value.speed
            }
        }
    }

    private fun enqueueSpeed(request: SpeedRequest) {
        synchronized(speedLock) {
            if (pendingSpeed?.ticket == request.ticket) speedRequests.trySend(request)
        }
    }

    fun setSpeed(speed: Float) {
        if (!speed.isFinite()) return
        val normalized = speed.coerceIn(0.25f, 4.0f)
        val ticket = registerSpeedRequest(normalized)
        enqueueSpeed(SpeedRequest(ticket, normalized))
    }

    fun setAspect(aspect: VideoAspect) = engine.setAspect(aspect)

    fun selectAudioTrack(trackId: String?) = onCurrentMedia { engine.selectAudioTrack(trackId) }

    /** 用户切换字幕时记住选择：具体轨道 ID 或 off（关闭），下次打开同一视频自动恢复。 */
    fun selectSubtitleTrack(trackId: String?) {
        onCurrentMedia {
            engine.selectSubtitleTrack(trackId)
            val mediaId = currentMedia?.id ?: return@onCurrentMedia
            val remembered = trackId ?: SUBTITLE_OFF
            scope.launch {
                runCatching { mediaRepository.setSelectedSubtitleTrack(mediaId, remembered) }
                    .onFailure { error -> reportError("无法保存字幕选择：${error.message ?: "未知错误"}") }
            }
        }
    }

    /** 首次拿到字幕轨时恢复该视频上次的选择；轨道已不存在则保持文件默认。 */
    private fun restoreSubtitleSelection(tracks: List<MediaTrack>) {
        if (subtitleSelectionRestored || tracks.isEmpty()) return
        val media = currentMedia ?: return
        subtitleSelectionRestored = true
        val remembered = media.selectedSubtitleTrackId ?: return
        if (remembered == SUBTITLE_OFF) {
            engine.selectSubtitleTrack(null)
        } else if (tracks.any { it.id == remembered }) {
            engine.selectSubtitleTrack(remembered)
        }
    }

    fun adjustSubtitleDelay(deltaMs: Long) {
        state.update { it.copy(subtitleDelayMs = (it.subtitleDelayMs + deltaMs).coerceIn(-60_000L, 60_000L)) }
        engine.setSubtitleDelay(state.value.subtitleDelayMs)
    }

    fun resetSubtitleDelay() {
        state.update { it.copy(subtitleDelayMs = 0L) }
        engine.setSubtitleDelay(0L)
    }

    fun toggleControls() {
        if (state.value.locked) return
        state.update { it.copy(controlsVisible = !it.controlsVisible) }
    }

    fun setControlsVisible(visible: Boolean) {
        if (state.value.locked && visible) return
        state.update { it.copy(controlsVisible = visible) }
    }

    fun toggleLock() {
        state.update {
            if (it.locked) {
                it.copy(locked = false, controlsVisible = true)
            } else {
                it.copy(locked = true, controlsVisible = false)
            }
        }
    }

    fun setPlayMode(mode: PlayMode) {
        state.update { it.copy(playMode = mode) }
    }

    fun playNext() {
        scope.launch {
            transitionMutex.withLock {
                val settings = settingsRepository.observeSettings().first()
                when (state.value.playMode) {
                    PlayMode.SHUFFLE -> playQueueIndex(randomIndex(), settings)
                    else -> if (state.value.hasNext) playQueueIndex(state.value.queueIndex + 1, settings)
                }
                Unit
            }
        }
    }

    fun playPrevious() {
        scope.launch {
            transitionMutex.withLock {
                val settings = settingsRepository.observeSettings().first()
                if (state.value.queueIndex > 0) playQueueIndex(state.value.queueIndex - 1, settings)
                Unit
            }
        }
    }


    /** 长按临时倍速：按下提高倍速，松开恢复。 */
    fun beginTemporarySpeed(multiplier: Float) {
        if (!multiplier.isFinite()) return
        val request = synchronized(speedLock) {
            if (speedBeforeTemporary != null) return
            speedBeforeTemporary = requestedSpeed
            val speed = multiplier.coerceIn(0.25f, 4.0f)
            SpeedRequest(registerSpeedRequest(speed), speed)
        }
        enqueueSpeed(request)
    }

    fun endTemporarySpeed() {
        val request = synchronized(speedLock) {
            val previous = speedBeforeTemporary ?: return
            speedBeforeTemporary = null
            SpeedRequest(registerSpeedRequest(previous), previous)
        }
        enqueueSpeed(request)
    }

    fun consumeError() {
        state.update { it.copy(error = null) }
    }

    // ------------------------------------------------------------- 进度持久化

    private fun startProgressTicker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                delay(PROGRESS_SAVE_INTERVAL_MS)
                transitionMutex.withLock { persistProgress() }
            }
        }
    }

    private fun persistProgress(endOfPlayback: Boolean = false) {
        val media = currentMedia ?: return
        val snapshot = engine.snapshot.value
        if (!hasPlayed || snapshot.mediaId != media.id) return
        val watched = watchedDeltaMs
        watchedDeltaMs = 0L
        val position = if (endOfPlayback && snapshot.durationMs > 0L) snapshot.durationMs else snapshot.positionMs
        val playIncrement = if (!playCountRecorded) 1 else 0
        if (playIncrement > 0) playCountRecorded = true
        saveProgress(PlaybackProgress(media.id, position, snapshot.durationMs, watched, playIncrement))
    }

    fun reportError(message: String) {
        state.update { it.copy(isLoading = false, error = message) }
    }

    /** 与切源及外部命令串行；完成持久化入队后才销毁唯一原生引擎。 */
    suspend fun closeAndRelease() = transitionMutex.withLock {
        persistProgress()
        mutableMedia.value = null
        speedRequests.close()
        engine.pause()
        engine.release()
    }

    companion object {
        private const val PROGRESS_SAVE_INTERVAL_MS = 5_000L
        private const val MAX_ARTWORK_BYTES = 256 * 1024
    }
}
