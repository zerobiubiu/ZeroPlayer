package com.privateplayer.core.playback

import com.privateplayer.domain.model.SubtitlePreferences
import com.privateplayer.domain.model.VideoAspect

/** 播放状态机。任何时刻只有一个状态。 */
enum class PlaybackStatus {
    IDLE,
    LOADING,
    PLAYING,
    PAUSED,
    ENDED,
    ERROR,
}

/** 播放引擎快照。UI 只读该状态，不直接触碰引擎内部。 */
data class PlaybackSnapshot(
    val status: PlaybackStatus = PlaybackStatus.IDLE,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedMs: Long = 0L,
    val speed: Float = 1.0f,
    val volume: Float = 1.0f,
    val aspect: VideoAspect = VideoAspect.FIT,
    val errorMessage: String? = null,
    /** 用户请求继续播放；加载或缓冲时可为 true，而实际状态仍未进入 PLAYING。 */
    val playWhenReady: Boolean = false,
    /** 当前引擎实际装载的内部媒体 ID；未装载时为 null。 */
    val mediaId: Long? = null,
) {
    val isPlaying: Boolean get() = status == PlaybackStatus.PLAYING
    val progress: Float
        get() = if (durationMs <= 0L) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
}

/** 播放引擎对外暴露的轨道信息（音轨 / 字幕轨）。 */
data class MediaTrack(
    val id: String,
    val label: String,
    val language: String?,
    val codec: String?,
    /** 当前选中的轨道；保留字段名称以兼容既有播放器。 */
    val isDefault: Boolean,
    /** 原生轨道实际对应的外挂文件名；内嵌轨道为 null，不暴露私有绝对路径。 */
    val externalFileName: String? = null,
)

/** 引擎解析出的真实媒体信息；与导入时的探测快照可能不同。 */
data class EngineMediaInfo(
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val videoCodec: String?,
    val audioCodec: String?,
    val container: String?,
    val bitrate: Long?,
)

/** 从实际引擎读取的诊断；无法获取的字段为 null。 */
data class PlaybackDiagnostics(
    val engineVersion: String? = null,
    val hwdecCurrent: String? = null,
    val videoDecoder: String? = null,
    val videoCodec: String? = null,
    val audioCodec: String? = null,
)

/** 播放事件，用于“自动下一集”、错误提示等一次性动作。 */
sealed interface PlaybackEvent {
    data class Ended(val positionMs: Long) : PlaybackEvent

    data class Failed(val message: String) : PlaybackEvent

    data class TrackChanged(val kind: TrackKind, val trackId: String?) : PlaybackEvent
}

enum class TrackKind { AUDIO, SUBTITLE }

/** 播放源。第一阶段只支持应用私有存储中的本地文件。 */
data class PlaybackSource(
    val mediaId: Long,
    val title: String,
    /** 私有存储中的绝对路径（由 Storage 层解析后传入）。 */
    val absolutePath: String,
    val durationHintMs: Long,
    /** 已导入到私有存储的外挂字幕。 */
    val subtitleFiles: List<String> = emptyList(),
)

/**
 * 播放引擎统一接口。
 *
 * 设计约束：
 * - 只暴露与具体引擎无关的概念（状态、轨道、倍速、比例）；
 * - 所有方法在主线程调用安全；
 * - [release] 之后实例不可复用；
 * - 真实解码能力由实现负责，UI 不得直接依赖 MPV。
 */
interface PlaybackEngine {

    val snapshot: kotlinx.coroutines.flow.StateFlow<PlaybackSnapshot>

    val audioTracks: kotlinx.coroutines.flow.StateFlow<List<MediaTrack>>

    val subtitleTracks: kotlinx.coroutines.flow.StateFlow<List<MediaTrack>>

    val events: kotlinx.coroutines.flow.Flow<PlaybackEvent>

    /** 引擎名，用于设置页与播放器标识当前后端。 */
    val backendLabel: String

    suspend fun initialize()

    /** 绑定/解绑渲染表面；libmpv 通过 `ANativeWindow` 渲染时由实现转换。 */
    fun attachSurface(surface: android.view.Surface?)

    /** 通知引擎 Surface 的像素尺寸。 */
    fun resizeSurface(width: Int, height: Int)

    /** 只返回实际引擎能够提供的诊断信息。 */
    fun diagnostics(): PlaybackDiagnostics

    suspend fun load(source: PlaybackSource, startPositionMs: Long, autoPlay: Boolean)

    fun play()

    fun pause()

    suspend fun stop()

    fun seekTo(positionMs: Long)

    fun setSpeed(speed: Float)

    fun setVolume(volume: Float)

    fun selectAudioTrack(trackId: String?)

    fun selectSubtitleTrack(trackId: String?)

    /** 字幕延迟，正数表示字幕延后。 */
    fun setSubtitleDelay(delayMs: Long)

    /** 应用字幕样式；ASS 原始排版不强制覆盖。 */
    fun setSubtitleStyle(preferences: SubtitlePreferences)

    fun setAspect(aspect: VideoAspect)

    suspend fun mediaInfo(): EngineMediaInfo?

    suspend fun release()
}
