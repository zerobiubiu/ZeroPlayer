package com.privateplayer.core.playback.mpv

import android.content.Context
import android.view.Surface
import com.privateplayer.core.playback.EngineMediaInfo
import com.privateplayer.core.playback.MediaTrack
import com.privateplayer.core.playback.PlaybackDiagnostics
import com.privateplayer.core.playback.PlaybackEngine
import com.privateplayer.core.playback.PlaybackEvent
import com.privateplayer.core.playback.PlaybackSnapshot
import com.privateplayer.core.playback.PlaybackSource
import com.privateplayer.core.playback.PlaybackStatus
import com.privateplayer.core.playback.TrackKind
import com.privateplayer.domain.model.SubtitleBackground
import com.privateplayer.domain.model.SubtitlePreferences
import com.privateplayer.domain.model.SubtitleTextColor
import com.privateplayer.domain.model.VideoAspect
import dev.jdtech.mpv.MPVLib
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * libmpv 播放引擎。只接受导入到应用私有目录的媒体与字幕。
 * JNI 回调线程只入队；所有播放器访问和状态变更由同一把锁保护。
 */
class MpvPlaybackEngine(context: Context) : PlaybackEngine {
    private val appContext = context.applicationContext
    /** 串行化创建及销毁，销毁时不持有供 JNI 回调消费使用的状态锁。 */
    private val lifecycleLock = Any()
    private val lock = Any()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val nativeMessages = Channel<NativeMessage>(Channel.UNLIMITED)
    private val _snapshot = MutableStateFlow(PlaybackSnapshot())
    private val _audioTracks = MutableStateFlow<List<MediaTrack>>(emptyList())
    private val _subtitleTracks = MutableStateFlow<List<MediaTrack>>(emptyList())
    private val _events = MutableSharedFlow<PlaybackEvent>(extraBufferCapacity = 32)

    override val snapshot: StateFlow<PlaybackSnapshot> = _snapshot.asStateFlow()
    override val audioTracks: StateFlow<List<MediaTrack>> = _audioTracks.asStateFlow()
    override val subtitleTracks: StateFlow<List<MediaTrack>> = _subtitleTracks.asStateFlow()
    override val events: Flow<PlaybackEvent> = _events.asSharedFlow()
    override val backendLabel: String = "libmpv"

    private var player: MPVLib? = null
    /** destroy 失败时保留引用，仅允许再次尝试释放，不允许重新使用。 */
    private var pendingDestroy: MPVLib? = null
    private var released = false
    /** 回调线程读取，避免上一轮已入队的事件覆盖新的播放请求。 */
    @Volatile private var generation = 0L
    private var surface: Surface? = null
    private var surfaceAttached = false
    /** 已绑定原生引用且渲染参数设置成功，区别于部分失败的绑定。 */
    private var surfaceReady = false
    private var surfaceWidth = 0
    private var surfaceHeight = 0
    private var source: LocalSource? = null
    private var fileStarted = false
    private var fileLoaded = false
    private var playbackRestarted = false
    private var desiredPaused = true
    private var requestedSpeed = 1.0f
    private var requestedVolume = 1.0f
    private var requestedAspect = VideoAspect.FIT
    private var requestedSubtitleDelayMs = 0L
    private var requestedSubtitleStyle = SubtitlePreferences()

    private val observer = object : MPVLib.EventObserver {
        override fun eventProperty(property: String) {
            nativeMessages.trySend(NativeMessage.Property(property, generation))
        }

        override fun eventProperty(property: String, value: Long) {
            nativeMessages.trySend(NativeMessage.Property(property, generation))
        }

        override fun eventProperty(property: String, value: Double) {
            nativeMessages.trySend(NativeMessage.Property(property, generation))
        }

        override fun eventProperty(property: String, value: Boolean) {
            nativeMessages.trySend(NativeMessage.Property(property, generation))
        }

        override fun eventProperty(property: String, value: String) {
            nativeMessages.trySend(NativeMessage.Property(property, generation))
        }

        override fun event(eventId: Int) {
            nativeMessages.trySend(NativeMessage.Event(eventId, generation))
        }

        override fun eventEndFile(reason: Int, errorCode: Int, errorText: String?) {
            nativeMessages.trySend(NativeMessage.EndFile(reason, errorCode, errorText, generation))
        }
    }

    init {
        scope.launch {
            for (message in nativeMessages) {
                synchronized(lock) {
                    val current = player
                    if (!released && current != null && message.generation == generation) {
                        try {
                            handleNativeMessage(current, message)
                        } catch (error: Exception) {
                            failLocked("处理 libmpv 事件失败：${error.detail()}")
                        } catch (error: LinkageError) {
                            failLocked("处理 libmpv 事件失败：${error.detail()}")
                        }
                    }
                }
            }
        }
    }

    override suspend fun initialize() {
        synchronized(lifecycleLock) {
            var created: MPVLib? = null
            try {
                synchronized(lock) {
                    check(!released) { "播放引擎已释放" }
                    if (player != null) return
                    generation++
                    val instance = MPVLib.create(appContext) ?: error("无法创建 libmpv 实例")
                    created = instance
                    instance.addObserver(observer)
                    configure(instance)
                    instance.init()
                    observe(instance)
                    instance.setPropertyDouble("speed", requestedSpeed.toDouble())
                    instance.setPropertyDouble("volume", requestedVolume.toDouble() * 100.0)
                    instance.setPropertyDouble("sub-delay", requestedSubtitleDelayMs / 1000.0)
                    applySubtitleStyle(instance, requestedSubtitleStyle)
                    applyAspect(instance, requestedAspect)
                    surface?.let { attachLocked(instance, it) }
                    player = instance
                    _snapshot.value = _snapshot.value.copy(
                        status = PlaybackStatus.IDLE,
                        aspect = requestedAspect,
                        errorMessage = null,
                    )
                }
            } catch (error: Throwable) {
                if (error !is Exception && error !is LinkageError) throw error
                // destroy 会等待 JNI 事件线程退出，不能在持有状态锁时调用。
                created?.removeObserver(observer)
                try {
                    created?.destroy()
                } catch (_: Exception) {
                    // 保留初始化时的原始错误。
                } catch (_: LinkageError) {
                    // 保留初始化时的原始错误。
                }
                val message = "libmpv 初始化失败：${error.detail()}"
                synchronized(lock) {
                    surfaceAttached = false
                    surfaceReady = false
                    if (!released) failLocked(message)
                }
                throw IllegalStateException(message, error)
            }
        }
    }

    override fun attachSurface(surface: Surface?) {
        synchronized(lock) {
            if (released) return
            if (this.surface === surface &&
                (player == null || (surface == null && !surfaceAttached) || (surface != null && surfaceReady))
            ) return
            try {
                val instance = player
                if (instance != null && surfaceAttached) detachLocked(instance)
                this.surface = surface
                if (surface != null && instance != null) attachLocked(instance, surface)
            } catch (error: Throwable) {
                if (error !is Exception && error !is LinkageError) throw error
                val message = "Surface 切换失败：${error.detail()}"
                failLocked(message)
                throw IllegalStateException(message, error)
            }
        }
    }

    override fun resizeSurface(width: Int, height: Int) {
        synchronized(lock) {
            if (released || width <= 0 || height <= 0) return
            surfaceWidth = width
            surfaceHeight = height
            val instance = player ?: return
            if (surfaceAttached) {
                try {
                    instance.setPropertyString("android-surface-size", "${width}x$height")
                } catch (error: Throwable) {
                    if (error !is Exception && error !is LinkageError) throw error
                    val message = "更新 Surface 尺寸失败：${error.detail()}"
                    failLocked(message)
                    throw IllegalStateException(message, error)
                }
            }
        }
    }

    override fun diagnostics(): PlaybackDiagnostics = synchronized(lock) {
        val instance = player ?: return@synchronized PlaybackDiagnostics()
        try {
            PlaybackDiagnostics(
                engineVersion = instance.getPropertyString("mpv-version"),
                hwdecCurrent = instance.getPropertyString("hwdec-current"),
                videoDecoder = instance.getPropertyString("current-tracks/video/decoder"),
                videoCodec = instance.getPropertyString("current-tracks/video/codec"),
                audioCodec = instance.getPropertyString("current-tracks/audio/codec"),
            )
        } catch (error: Exception) {
            failLocked("读取 libmpv 诊断失败：${error.detail()}")
            PlaybackDiagnostics()
        } catch (error: LinkageError) {
            failLocked("读取 libmpv 诊断失败：${error.detail()}")
            PlaybackDiagnostics()
        }
    }

    override suspend fun load(source: PlaybackSource, startPositionMs: Long, autoPlay: Boolean) {
        try {
            val local = LocalSource(
                path = privateFile(source.absolutePath, File(appContext.filesDir, "media"), "媒体"),
                subtitles = source.subtitleFiles.map {
                    privateFile(it, File(appContext.filesDir, "subtitles"), "字幕", File(appContext.filesDir, "media"))
                },
                startPositionMs = startPositionMs.coerceAtLeast(0L),
            )
            synchronized(lock) {
                check(!released) { "播放引擎已释放" }
                val instance = checkNotNull(player) { "播放引擎尚未初始化" }
                generation++
                this.source = local
                fileStarted = false
                fileLoaded = false
                playbackRestarted = false
                desiredPaused = !autoPlay
                _audioTracks.value = emptyList()
                _subtitleTracks.value = emptyList()
                _snapshot.value = _snapshot.value.copy(
                    status = PlaybackStatus.LOADING,
                    mediaId = source.mediaId,
                    playWhenReady = autoPlay,
                    positionMs = 0L,
                    durationMs = 0L,
                    bufferedMs = 0L,
                    errorMessage = null,
                )
                // 等 FILE_LOADED 后再跳转、添加字幕并恢复用户所请求的暂停状态。
                instance.setPropertyBoolean("pause", true)
                instance.command(arrayOf("loadfile", local.path, "replace"))
            }
        } catch (error: Exception) {
            val message = "加载媒体失败：${error.detail()}"
            synchronized(lock) {
                if (!released) {
                    _snapshot.value = _snapshot.value.copy(mediaId = source.mediaId)
                    stopAfterLoadFailureLocked()
                    failLocked(message)
                }
            }
            throw IllegalStateException(message, error)
        } catch (error: LinkageError) {
            val message = "加载媒体失败：${error.detail()}"
            synchronized(lock) {
                if (!released) {
                    _snapshot.value = _snapshot.value.copy(mediaId = source.mediaId)
                    stopAfterLoadFailureLocked()
                    failLocked(message)
                }
            }
            throw IllegalStateException(message, error)
        }
    }

    override fun play() {
        synchronized(lock) {
            if (released) return
            desiredPaused = false
            val instance = player ?: return
            val local = source ?: return
            _snapshot.value = _snapshot.value.copy(playWhenReady = true)
            nativeActionLocked("播放") {
                if (_snapshot.value.status == PlaybackStatus.ENDED) {
                    playbackRestarted = false
                    _snapshot.value = _snapshot.value.copy(status = PlaybackStatus.LOADING, errorMessage = null)
                    if (fileLoaded) {
                        instance.command(arrayOf("seek", "0", "absolute+exact"))
                    } else {
                        // EOF 后如果原生端已经关闭文件，必须重新装载而不是对已关闭的文件 seek。
                        generation++
                        fileStarted = false
                        instance.setPropertyBoolean("pause", true)
                        instance.command(arrayOf("loadfile", local.path, "replace"))
                    }
                }
                if (fileLoaded) instance.setPropertyBoolean("pause", false)
            }
        }
    }

    override fun pause() {
        synchronized(lock) {
            if (released) return
            desiredPaused = true
            val instance = player ?: return
            if (source != null) {
                _snapshot.value = _snapshot.value.copy(playWhenReady = false)
                nativeActionLocked("暂停") { instance.setPropertyBoolean("pause", true) }
            }
        }
    }

    override suspend fun stop() {
        synchronized(lock) {
            if (released) return
            generation++
            source = null
            fileStarted = false
            fileLoaded = false
            playbackRestarted = false
            desiredPaused = true
            _audioTracks.value = emptyList()
            _subtitleTracks.value = emptyList()
            _snapshot.value = _snapshot.value.copy(
                status = PlaybackStatus.IDLE,
                mediaId = null,
                positionMs = 0L,
                durationMs = 0L,
                bufferedMs = 0L,
                playWhenReady = false,
                errorMessage = null,
            )
            player?.let { instance -> nativeActionLocked("停止") { instance.command(arrayOf("stop")) } }
        }
    }

    override fun seekTo(positionMs: Long) {
        synchronized(lock) {
            if (released) return
            val instance = player ?: return
            val duration = _snapshot.value.durationMs
            val target = if (duration > 0L) positionMs.coerceIn(0L, duration) else positionMs.coerceAtLeast(0L)
            if (!fileLoaded) {
                if (_snapshot.value.status == PlaybackStatus.ENDED) {
                    source = source?.copy(startPositionMs = target)
                }
                return
            }
            nativeActionLocked("跳转") {
                if (_snapshot.value.status == PlaybackStatus.ENDED) {
                    playbackRestarted = false
                    _snapshot.value = _snapshot.value.copy(
                        status = if (desiredPaused) PlaybackStatus.PAUSED else PlaybackStatus.LOADING,
                    )
                }
                instance.command(arrayOf("seek", (target / 1000.0).toString(), "absolute+exact"))
            }
        }
    }

    override fun setSpeed(speed: Float) {
        synchronized(lock) {
            if (released || !speed.isFinite()) return
            requestedSpeed = speed.coerceIn(0.25f, 4.0f)
            player?.let { instance ->
                nativeActionLocked("设置倍速") { instance.setPropertyDouble("speed", requestedSpeed.toDouble()) }
            }
        }
    }

    override fun setVolume(volume: Float) {
        synchronized(lock) {
            if (released || !volume.isFinite()) return
            requestedVolume = volume.coerceIn(0f, 1f)
            player?.let { instance ->
                nativeActionLocked("设置音量") { instance.setPropertyDouble("volume", requestedVolume.toDouble() * 100.0) }
            }
        }
    }

    override fun selectAudioTrack(trackId: String?) = selectTrack(TrackKind.AUDIO, trackId)

    override fun selectSubtitleTrack(trackId: String?) = selectTrack(TrackKind.SUBTITLE, trackId)

    override fun setSubtitleDelay(delayMs: Long) {
        synchronized(lock) {
            if (released) return
            requestedSubtitleDelayMs = delayMs
            player?.let { instance ->
                nativeActionLocked("设置字幕延迟") { instance.setPropertyDouble("sub-delay", delayMs / 1000.0) }
            }
        }
    }

    override fun setSubtitleStyle(preferences: SubtitlePreferences) {
        synchronized(lock) {
            if (released) return
            requestedSubtitleStyle = preferences
            player?.let { instance ->
                nativeActionLocked("设置字幕样式") { applySubtitleStyle(instance, preferences) }
            }
        }
    }

    override fun setAspect(aspect: VideoAspect) {
        synchronized(lock) {
            if (released) return
            requestedAspect = aspect
            player?.let { instance ->
                nativeActionLocked("设置画面比例") {
                    applyAspect(instance, aspect)
                    _snapshot.value = _snapshot.value.copy(aspect = aspect)
                }
            }
        }
    }

    override suspend fun mediaInfo(): EngineMediaInfo? = synchronized(lock) {
        val instance = player ?: return@synchronized null
        if (!fileLoaded) return@synchronized null
        try {
            EngineMediaInfo(
                durationMs = instance.getPropertyDouble("duration").milliseconds() ?: 0L,
                width = instance.getPropertyInt("video-params/w") ?: 0,
                height = instance.getPropertyInt("video-params/h") ?: 0,
                videoCodec = instance.getPropertyString("current-tracks/video/codec"),
                audioCodec = instance.getPropertyString("current-tracks/audio/codec"),
                container = instance.getPropertyString("file-format"),
                bitrate = null,
            )
        } catch (error: Exception) {
            failLocked("读取媒体信息失败：${error.detail()}")
            null
        } catch (error: LinkageError) {
            failLocked("读取媒体信息失败：${error.detail()}")
            null
        }
    }

    override suspend fun release() {
        synchronized(lifecycleLock) {
            val instance = synchronized(lock) {
                if (released) {
                    pendingDestroy
                } else {
                    released = true
                    val current = player
                    if (current != null && surfaceAttached) {
                        try {
                            detachLocked(current)
                        } catch (_: Exception) {
                            // 后续 destroy 仍会释放原生 Surface 引用。
                        } catch (_: LinkageError) {
                            // 后续 destroy 仍会释放原生 Surface 引用。
                        }
                    }
                    pendingDestroy = current
                    player = null
                    source = null
                    surface = null
                    surfaceAttached = false
                    surfaceReady = false
                    fileLoaded = false
                    _audioTracks.value = emptyList()
                    _subtitleTracks.value = emptyList()
                    _snapshot.value = PlaybackSnapshot()
                    current
                }
            }
            nativeMessages.close()
            scope.cancel()
            // 观察者内部有独立锁，destroy 还会 join 事件线程；都必须在状态锁外进行。
            instance?.removeObserver(observer)
            instance?.destroy()
            synchronized(lock) { pendingDestroy = null }
        }
    }

    private fun configure(instance: MPVLib) {
        val options = listOf(
            "config" to "no",
            "idle" to "yes",
            "keep-open" to "yes",
            "pause" to "yes",
            "vo" to "gpu",
            "gpu-context" to "android",
            "opengl-es" to "yes",
            "hwdec" to "mediacodec,mediacodec-copy",
            "hwdec-codecs" to "h264,hevc,mpeg4,mpeg2video,vp8,vp9,av1",
            "ao" to "audiotrack,opensles",
            // 常驻变速滤镜，避免每次从 1× 切换时反复重建音频链路。
            "af" to "scaletempo2",
            "access-references" to "no",
            "autoload-files" to "no",
            // 字幕保持在视频画面内，不占用屏幕上下黑边。
            "sub-use-margins" to "no",
            // 按实际视频而非整个窗口缩放，避免竖屏黑边令普通字幕被放大并挤出画面。
            "sub-scale-with-window" to "no",
            // 普通文本字幕可使用用户样式，ASS 脚本保持原有排版。
            "sub-ass-override" to "no",
        )
        options.forEach { (name, value) ->
            val result = instance.setOptionString(name, value)
            check(result >= 0) { "无法设置 libmpv 选项 $name=$value（错误码 $result）" }
        }
    }

    private fun observe(instance: MPVLib) {
        val format = MPVLib.MpvFormat
        listOf(
            "time-pos" to format.MPV_FORMAT_DOUBLE,
            "duration" to format.MPV_FORMAT_DOUBLE,
            "demuxer-cache-time" to format.MPV_FORMAT_DOUBLE,
            "pause" to format.MPV_FORMAT_FLAG,
            "eof-reached" to format.MPV_FORMAT_FLAG,
            "speed" to format.MPV_FORMAT_DOUBLE,
            "volume" to format.MPV_FORMAT_DOUBLE,
            "track-list" to format.MPV_FORMAT_NONE,
            "aid" to format.MPV_FORMAT_STRING,
            "sid" to format.MPV_FORMAT_STRING,
        ).forEach { (name, type) -> instance.observeProperty(name, type) }
    }

    private fun attachLocked(instance: MPVLib, next: Surface) {
        surfaceReady = false
        check(next.isValid) { "Surface 已失效" }
        instance.attachSurface(next)
        surfaceAttached = true
        if (surfaceWidth > 0 && surfaceHeight > 0) {
            instance.setPropertyString("android-surface-size", "${surfaceWidth}x$surfaceHeight")
        }
        instance.setPropertyString("force-window", "yes")
        instance.setPropertyString("vo", "gpu")
        surfaceReady = true
    }

    private fun detachLocked(instance: MPVLib) {
        surfaceReady = false
        // 先停用视频输出，再释放 JNI 持有的 Surface 引用。
        instance.setPropertyString("vo", "null")
        instance.setPropertyString("force-window", "no")
        instance.detachSurface()
        surfaceAttached = false
    }

    private fun handleNativeMessage(instance: MPVLib, message: NativeMessage) {
        when (message) {
            is NativeMessage.EndFile -> onEndFileLocked(instance, message)
            is NativeMessage.Event -> when (message.id) {
                MPVLib.MpvEvent.MPV_EVENT_START_FILE -> if (source != null) fileStarted = true
                MPVLib.MpvEvent.MPV_EVENT_FILE_LOADED -> onFileLoadedLocked(instance)
                MPVLib.MpvEvent.MPV_EVENT_PLAYBACK_RESTART -> if (fileLoaded) {
                    playbackRestarted = true
                    refreshSnapshotLocked(instance)
                }
                MPVLib.MpvEvent.MPV_EVENT_VIDEO_RECONFIG,
                MPVLib.MpvEvent.MPV_EVENT_AUDIO_RECONFIG -> if (fileLoaded) refreshTracksLocked(instance)
                MPVLib.MpvEvent.MPV_EVENT_SHUTDOWN -> failLocked("libmpv 已关闭")
                MPVLib.MpvEvent.MPV_EVENT_QUEUE_OVERFLOW -> failLocked("libmpv 事件队列溢出")
            }
            is NativeMessage.Property -> when (message.name) {
                "track-list", "aid", "sid" -> if (fileLoaded) refreshTracksLocked(instance)
                "time-pos", "duration", "demuxer-cache-time", "pause", "eof-reached", "speed", "volume" -> {
                    refreshSnapshotLocked(instance)
                }
            }
        }
    }

    private fun onEndFileLocked(instance: MPVLib, event: NativeMessage.EndFile) {
        if (source == null) return
        // loadfile 替换旧媒体时，旧文件的 STOP 不是新文件的结束。
        if (event.reason == END_FILE_STOP && _snapshot.value.status == PlaybackStatus.LOADING) {
            fileStarted = false
            return
        }
        // 旧媒体的 EOF 可能在切换后才到达；异步 ERROR 则必须报告，不能停在 LOADING。
        if (event.reason == END_FILE_EOF && !fileStarted) return
        fileStarted = false
        fileLoaded = false
        playbackRestarted = false
        _audioTracks.value = emptyList()
        _subtitleTracks.value = emptyList()
        when (event.reason) {
            END_FILE_EOF -> {
                val current = _snapshot.value
                if (current.status == PlaybackStatus.ERROR) return
                source = source?.copy(startPositionMs = 0L)
                desiredPaused = true
                _snapshot.value = current.copy(status = PlaybackStatus.ENDED, errorMessage = null, playWhenReady = false)
                if (current.status != PlaybackStatus.ENDED) {
                    _events.tryEmit(PlaybackEvent.Ended(current.positionMs))
                }
            }
            END_FILE_STOP, END_FILE_QUIT -> {
                source = null
                _snapshot.value = _snapshot.value.copy(
                    status = PlaybackStatus.IDLE,
                    mediaId = null,
                    positionMs = 0L,
                    durationMs = 0L,
                    bufferedMs = 0L,
                    playWhenReady = false,
                    errorMessage = null,
                )
            }
            END_FILE_ERROR -> {
                val detail = event.errorText?.takeIf { it.isNotBlank() }
                val description = if (detail == null) "错误码 ${event.errorCode}" else "$detail（错误码 ${event.errorCode}）"
                failLocked("媒体播放失败：$description")
            }
            END_FILE_REDIRECT -> {
                // 当前只允许应用私有文件，不继续播放重定向或播放列表条目。
                source = null
                failLocked("媒体被重定向；当前引擎不支持播放列表或外部引用")
                instance.command(arrayOf("stop"))
            }
            else -> failLocked("媒体结束原因未知（mpv reason=${event.reason}，error=${event.errorCode}）")
        }
    }

    private fun onFileLoadedLocked(instance: MPVLib) {
        val local = source ?: return
        // 替换文件期间，迟到的 FILE_LOADED 不能套用到新的播放源。
        if (instance.getPropertyString("path") != local.path) return
        fileLoaded = true
        if (local.startPositionMs > 0L) {
            instance.command(arrayOf("seek", (local.startPositionMs / 1000.0).toString(), "absolute+exact"))
        }
        local.subtitles.forEach { instance.command(arrayOf("sub-add", it, "auto")) }
        instance.setPropertyBoolean("pause", desiredPaused)
        refreshTracksLocked(instance)
        refreshSnapshotLocked(instance)
    }

    private fun refreshSnapshotLocked(instance: MPVLib) {
        var current = _snapshot.value
        val speed = instance.getPropertyDouble("speed")?.toFloat()?.takeIf { it.isFinite() }
        val volume = instance.getPropertyDouble("volume")?.toFloat()?.takeIf { it.isFinite() }
        if (speed != null || volume != null) {
            current = current.copy(speed = speed ?: current.speed, volume = volume?.div(100f) ?: current.volume)
        }
        if (!fileLoaded || current.status == PlaybackStatus.ERROR) {
            _snapshot.value = current
            return
        }
        val position = instance.getPropertyDouble("time-pos").milliseconds() ?: current.positionMs
        val duration = instance.getPropertyDouble("duration").milliseconds() ?: current.durationMs
        val buffered = instance.getPropertyDouble("demuxer-cache-time").milliseconds() ?: 0L
        val eof = instance.getPropertyBoolean("eof-reached") == true
        val paused = instance.getPropertyBoolean("pause")
        val nextStatus = when {
            eof -> PlaybackStatus.ENDED
            paused == true -> PlaybackStatus.PAUSED
            paused == false && playbackRestarted -> PlaybackStatus.PLAYING
            else -> PlaybackStatus.LOADING
        }
        if (eof) desiredPaused = true
        val endedNow = eof && current.status != PlaybackStatus.ENDED
        current = current.copy(
            status = nextStatus, positionMs = position, durationMs = duration,
            bufferedMs = buffered, playWhenReady = !desiredPaused,
        )
        _snapshot.value = current
        if (endedNow) {
            source = source?.copy(startPositionMs = 0L)
            _events.tryEmit(PlaybackEvent.Ended(position))
        }
    }

    private fun refreshTracksLocked(instance: MPVLib) {
        val previousAudio = _audioTracks.value.firstOrNull { it.isDefault }?.id
        val previousSubtitle = _subtitleTracks.value.firstOrNull { it.isDefault }?.id
        val audio = mutableListOf<MediaTrack>()
        val subtitles = mutableListOf<MediaTrack>()
        val count = instance.getPropertyInt("track-list/count") ?: 0
        for (index in 0 until count) {
            val prefix = "track-list/$index"
            val type = instance.getPropertyString("$prefix/type") ?: continue
            if (type != "audio" && type != "sub") continue
            val id = instance.getPropertyInt("$prefix/id")?.toString() ?: continue
            val language = instance.getPropertyString("$prefix/lang")?.takeIf { it.isNotBlank() }
            val title = instance.getPropertyString("$prefix/title")?.takeIf { it.isNotBlank() }
            val external = instance.getPropertyString("$prefix/external-filename")?.let { File(it).name }
            val label = title ?: external ?: language ?: if (type == "audio") "音轨 $id" else "字幕 $id"
            val track = MediaTrack(
                id = id,
                label = label,
                language = language,
                codec = instance.getPropertyString("$prefix/codec"),
                isDefault = instance.getPropertyBoolean("$prefix/selected") == true,
                externalFileName = external,
            )
            if (type == "audio") audio.add(track) else subtitles.add(track)
        }
        _audioTracks.value = audio
        _subtitleTracks.value = subtitles
        val selectedAudio = audio.firstOrNull { it.isDefault }?.id
        val selectedSubtitle = subtitles.firstOrNull { it.isDefault }?.id
        if (previousAudio != selectedAudio) _events.tryEmit(PlaybackEvent.TrackChanged(TrackKind.AUDIO, selectedAudio))
        if (previousSubtitle != selectedSubtitle) _events.tryEmit(PlaybackEvent.TrackChanged(TrackKind.SUBTITLE, selectedSubtitle))
    }

    private fun selectTrack(kind: TrackKind, trackId: String?) {
        synchronized(lock) {
            if (released || !fileLoaded) return
            val instance = player ?: return
            val available = if (kind == TrackKind.AUDIO) _audioTracks.value else _subtitleTracks.value
            if (trackId != null && available.none { it.id == trackId }) {
                _events.tryEmit(PlaybackEvent.Failed("轨道不存在：$trackId"))
                return
            }
            val property = if (kind == TrackKind.AUDIO) "aid" else "sid"
            nativeActionLocked("切换轨道") { instance.setPropertyString(property, trackId ?: "no") }
        }
    }

    private fun applySubtitleStyle(instance: MPVLib, preferences: SubtitlePreferences) {
        val color = when (preferences.textColor) {
            SubtitleTextColor.WHITE -> "#FFFFFF"
            SubtitleTextColor.YELLOW -> "#FFFF00"
            SubtitleTextColor.CYAN -> "#00FFFF"
        }
        val borderStyle = if (preferences.background == SubtitleBackground.NONE) "outline-and-shadow" else "background-box"
        val backgroundColor = when (preferences.background) {
            SubtitleBackground.NONE -> "#00000000"
            SubtitleBackground.SEMI_TRANSPARENT -> "#80000000"
            SubtitleBackground.SOLID -> "#FF000000"
        }
        instance.setPropertyDouble("sub-scale", preferences.fontSizeScale.toDouble())
        instance.setPropertyString("sub-color", color)
        instance.setPropertyString("sub-border-style", borderStyle)
        instance.setPropertyString("sub-back-color", backgroundColor)
    }

    private fun applyAspect(instance: MPVLib, aspect: VideoAspect) {
        val keepAspect = aspect != VideoAspect.FILL
        val crop = aspect == VideoAspect.CROP
        val ratio = when (aspect) {
            VideoAspect.RATIO_16_9 -> "16:9"
            VideoAspect.RATIO_4_3 -> "4:3"
            else -> "no"
        }
        instance.setPropertyBoolean("keepaspect", keepAspect)
        instance.setPropertyDouble("panscan", if (crop) 1.0 else 0.0)
        instance.setPropertyString("video-aspect-override", ratio)
    }

    private fun privateFile(path: String, root: File, label: String, alternateRoot: File? = null): String {
        val file = File(path)
        require(file.isAbsolute) { "$label 必须是应用私有目录中的绝对文件路径" }
        val canonical = file.canonicalFile
        val rootPath = root.canonicalFile.path + File.separator
        val alternatePath = alternateRoot?.canonicalFile?.path?.plus(File.separator)
        require(canonical.path.startsWith(rootPath) || (alternatePath != null && canonical.path.startsWith(alternatePath))) {
            "$label 不在应用私有目录中"
        }
        require(canonical.isFile && canonical.canRead()) { "$label 文件不存在或不可读：${canonical.name}" }
        return canonical.path
    }

    private fun stopAfterLoadFailureLocked() {
        generation++
        source = null
        fileStarted = false
        fileLoaded = false
        playbackRestarted = false
        _audioTracks.value = emptyList()
        _subtitleTracks.value = emptyList()
        try {
            player?.command(arrayOf("stop"))
        } catch (_: Exception) {
            // 保留导致加载失败的原始错误。
        } catch (_: LinkageError) {
            // 保留导致加载失败的原始错误。
        }
    }

    private inline fun nativeActionLocked(action: String, block: () -> Unit) {
        try {
            block()
        } catch (error: Exception) {
            failLocked("$action 失败：${error.detail()}")
        } catch (error: LinkageError) {
            failLocked("$action 失败：${error.detail()}")
        }
    }

    private fun failLocked(message: String) {
        desiredPaused = true
        _snapshot.value = _snapshot.value.copy(status = PlaybackStatus.ERROR, errorMessage = message, playWhenReady = false)
        _events.tryEmit(PlaybackEvent.Failed(message))
    }

    private fun Double?.milliseconds(): Long? = this
        ?.takeIf { it.isFinite() && it >= 0.0 }
        ?.let { (it * 1000.0).toLong() }

    private fun Throwable.detail(): String = message?.takeIf { it.isNotBlank() } ?: javaClass.simpleName

    private data class LocalSource(
        val path: String,
        val subtitles: List<String>,
        val startPositionMs: Long,
    )

    private sealed interface NativeMessage {
        val generation: Long

        data class Property(val name: String, override val generation: Long) : NativeMessage
        data class Event(val id: Int, override val generation: Long) : NativeMessage
        data class EndFile(
            val reason: Int,
            val errorCode: Int,
            val errorText: String?,
            override val generation: Long,
        ) : NativeMessage
    }

    private companion object {
        const val END_FILE_EOF = 0
        const val END_FILE_STOP = 2
        const val END_FILE_QUIT = 3
        const val END_FILE_ERROR = 4
        const val END_FILE_REDIRECT = 5
    }
}
