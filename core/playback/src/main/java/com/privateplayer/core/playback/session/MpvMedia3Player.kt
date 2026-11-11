package com.privateplayer.core.playback.session

import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture
import com.privateplayer.core.playback.PlaybackEngine
import com.privateplayer.core.playback.PlaybackSnapshot
import com.privateplayer.core.playback.PlaybackStatus
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * 将现有引擎映射为 Media3 Player；构造、Player 调用和释放均应在主线程执行。
 * [executeForMedia] 必须在与切源共用的协程同步门内核对媒体 ID 并立即执行 action。
 * 释放此适配器不会释放传入的引擎。
 */
@OptIn(UnstableApi::class)
class MpvMedia3Player(
    private val engine: PlaybackEngine,
    private val media: StateFlow<SessionMedia?>,
    executeForMedia: suspend (expectedMediaId: Long, action: () -> Unit) -> Boolean,
    private val registerCommand: (command: Int) -> Long,
    private val isCommandCurrent: (command: Int, ticket: Long) -> Boolean,
    private val registerSpeedRequest: (speed: Float) -> Long,
    private val applySpeedRequest: (ticket: Long, speed: Float) -> Unit,
    private val discardSpeedRequest: (ticket: Long) -> Unit,
) : SimpleBasePlayer(Looper.getMainLooper()) {

    private val released = AtomicBoolean(false)
    private val observerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val commandQueue = SerialEngineCommandQueue(engine.snapshot, media, executeForMedia)

    // 同一条目的快照会频繁更新进度，不必每次复制封面或重建媒体信息。
    private var cachedMedia: SessionMedia? = null
    private var cachedDurationMs: Long? = null
    private var cachedSeekable = false
    private var cachedItem: MediaItemData? = null
    private var currentError: PlaybackException? = null

    init {
        // Player 的状态与监听器只在主 Looper 更新。
        observerScope.launch {
            combine(engine.snapshot, media) { _, _ -> Unit }.collect {
                if (!released.get()) invalidateState()
            }
        }
    }

    override fun getState(): State {
        val snapshot = engine.snapshot.value
        val mapped = mapSessionPlayback(snapshot, media.value)
        val error = if (mapped.media != null && snapshot.status == PlaybackStatus.ERROR) {
            // 原始错误可能包含私有路径；外部控制器只能看到错误状态与通用说明。
            currentError ?: PlaybackException(
                "播放引擎发生错误", null, PlaybackException.ERROR_CODE_UNSPECIFIED,
            ).also { currentError = it }
        } else {
            currentError = null
            null
        }
        val item = mapped.media?.let { mediaItemData(it, mapped.durationMs, mapped.canSeek) }
        if (item == null) {
            cachedMedia = null
            cachedItem = null
        }
        return State.Builder()
            .setAvailableCommands(
                when {
                    mapped.canSeek -> SEEK_COMMANDS
                    mapped.canControl -> CONTROL_COMMANDS
                    else -> READ_COMMANDS
                },
            )
            .setPlaylist(if (item == null) emptyList() else listOf(item))
            .setPlaybackState(mapped.playbackState)
            .setPlayWhenReady(mapped.playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlayerError(error)
            .setIsLoading(mapped.isLoading)
            .setPlaybackParameters(PlaybackParameters(mapped.speed))
            .setVolume(mapped.volume)
            // 使用原始快照而非按墙钟推算，避免把暂停、缓冲或卡顿误报成进度。
            .setContentPositionMs(PositionSupplier.getConstant(mapped.positionMs))
            .setContentBufferedPositionMs(PositionSupplier.getConstant(mapped.bufferedPositionMs))
            .setTotalBufferedDurationMs(PositionSupplier.getConstant(mapped.bufferedDurationMs))
            .build()
    }

    override fun getPlaceholderState(suggestedPlaceholderState: State): State {
        // SimpleBasePlayer 默认会乐观地显示命令目标；等待 JNI 时仍只显示引擎事实。
        return getState()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        val mediaId = currentCommandMediaId() ?: return Futures.immediateVoidFuture()
        val ticket = registerCommand(Player.COMMAND_PLAY_PAUSE)
        return enqueue(mediaId) {
            executeIfCurrentCommand(Player.COMMAND_PLAY_PAUSE, ticket, isCommandCurrent) {
                if (playWhenReady) engine.play() else engine.pause()
            }
        }
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        if (mediaItemIndex != 0 || seekCommand != Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM) {
            return Futures.immediateVoidFuture()
        }
        val mediaId = currentCommandMediaId() ?: return Futures.immediateVoidFuture()
        val target = if (positionMs == C.TIME_UNSET) 0L else positionMs.coerceAtLeast(0L)
        val ticket = registerCommand(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
        return enqueue(mediaId, requiresSeekable = true) {
            executeIfCurrentCommand(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM, ticket, isCommandCurrent) {
                engine.seekTo(target)
            }
        }
    }

    override fun handleSetPlaybackParameters(playbackParameters: PlaybackParameters): ListenableFuture<*> {
        // 引擎只提供倍速，不提供独立音调；拒绝不能完整实现的音调变更。
        if (!playbackParameters.speed.isFinite() || playbackParameters.speed !in 0.25f..4f || playbackParameters.pitch != 1f) {
            return Futures.immediateFailedFuture<Void>(IllegalArgumentException("仅支持 0.25 至 4 倍速，不能单独调整音调"))
        }
        val mediaId = currentCommandMediaId() ?: return Futures.immediateVoidFuture()
        val speed = playbackParameters.speed
        val ticket = registerSpeedRequest(speed)
        return enqueue(mediaId, onDiscard = { discardSpeedRequest(ticket) }) {
            applySpeedRequest(ticket, speed)
        }
    }

    override fun handleSetVolume(volume: Float, volumeOperationType: Int): ListenableFuture<*> {
        if (!volume.isFinite() || volume !in 0f..1f) return Futures.immediateVoidFuture()
        val mediaId = currentCommandMediaId() ?: return Futures.immediateVoidFuture()
        return enqueue(mediaId) { engine.setVolume(volume) }
    }

    override fun handleRelease(): ListenableFuture<*> {
        if (released.compareAndSet(false, true)) {
            observerScope.cancel()
            commandQueue.close()
        }
        return Futures.immediateVoidFuture()
    }

    private fun currentCommandMediaId(): Long? {
        if (released.get()) return null
        val mediaId = currentMediaItem?.mediaId?.toLongOrNull() ?: return null
        return mediaId.takeIf { media.value?.mediaId == it && engine.snapshot.value.mediaId == it }
    }

    private fun enqueue(
        mediaId: Long,
        requiresSeekable: Boolean = false,
        onDiscard: (() -> Unit)? = null,
        action: () -> Unit,
    ): ListenableFuture<*> =
        commandQueue.enqueue(mediaId, requiresSeekable, onDiscard, action)

    private fun mediaItemData(media: SessionMedia, durationMs: Long?, seekable: Boolean): MediaItemData {
        val old = cachedItem
        if (old != null && cachedMedia === media && cachedDurationMs == durationMs && cachedSeekable == seekable) {
            return old
        }
        val metadata = MediaMetadata.Builder()
            .setTitle(media.title)
            .setArtist(media.artist)
            .setDurationMs(durationMs)
            .setArtworkData(media.artworkData, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
            .build()
        val item = MediaItem.Builder()
            .setMediaId(media.mediaId.toString())
            .setMediaMetadata(metadata)
            .build()
        val result = MediaItemData.Builder(media.mediaId.toString())
            .setMediaItem(item)
            .setMediaMetadata(metadata)
            .setIsSeekable(seekable)
            .setDurationUs(durationMs?.times(1_000L) ?: C.TIME_UNSET)
            .build()
        cachedMedia = media
        cachedDurationMs = durationMs
        cachedSeekable = seekable
        cachedItem = result
        return result
    }

    private companion object {
        val READ_COMMANDS: Player.Commands = Player.Commands.Builder()
            .addAll(
                Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                Player.COMMAND_GET_TIMELINE,
                Player.COMMAND_GET_METADATA,
                Player.COMMAND_GET_VOLUME,
                Player.COMMAND_RELEASE,
            )
            .build()
        val CONTROL_COMMANDS: Player.Commands = Player.Commands.Builder()
            .addAll(READ_COMMANDS)
            .addAll(Player.COMMAND_PLAY_PAUSE, Player.COMMAND_SET_SPEED_AND_PITCH, Player.COMMAND_SET_VOLUME)
            .build()
        val SEEK_COMMANDS: Player.Commands = Player.Commands.Builder()
            .addAll(CONTROL_COMMANDS)
            .add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
            .build()
    }
}

internal inline fun executeIfCurrentCommand(
    command: Int,
    ticket: Long,
    isCommandCurrent: (Int, Long) -> Boolean,
    action: () -> Unit,
) {
    if (isCommandCurrent(command, ticket)) action()
}

/** 顺序处理外部命令；关闭后丢弃尚未开始执行的命令。 */
internal class SerialEngineCommandQueue(
    private val snapshots: StateFlow<PlaybackSnapshot>,
    private val media: StateFlow<SessionMedia?>,
    private val executeForMedia: suspend (expectedMediaId: Long, action: () -> Unit) -> Boolean,
) {
    private class Command(
        val mediaId: Long,
        val requiresSeekable: Boolean,
        val completion: SettableFuture<Void>,
        val action: () -> Unit,
        onDiscard: (() -> Unit)?,
    ) {
        private val discardAction = onDiscard?.let { AtomicReference<(() -> Unit)?>(it) }

        fun discard() {
            discardAction?.getAndSet(null)?.invoke()
        }
    }

    private val closed = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val commands = Channel<Command>(Channel.UNLIMITED, onUndeliveredElement = {
        it.completion.cancel(false)
    })

    init {
        scope.launch {
            for (command in commands) {
                try {
                    var executed = false
                    val accepted = if (!command.completion.isCancelled && !closed.get() &&
                        media.value?.mediaId == command.mediaId
                    ) {
                        val snapshot = snapshots.value
                        if (snapshot.mediaId == command.mediaId && snapshot.status.canControl() &&
                            (!command.requiresSeekable || snapshot.status.canSeek())
                        ) {
                            // 切源与最终校验必须共用协调器的同步门，不能只靠快照的两次读取。
                            executeForMedia(command.mediaId) {
                                val current = snapshots.value
                                if (!command.completion.isCancelled && !closed.get() &&
                                    current.mediaId == command.mediaId && media.value?.mediaId == command.mediaId &&
                                    current.status.canControl() && (!command.requiresSeekable || current.status.canSeek())
                                ) {
                                    command.action()
                                    executed = true
                                }
                            }
                        } else {
                            false
                        }
                    } else {
                        false
                    }
                    if (!accepted || !executed) command.discard()
                    command.completion.set(null)
                } catch (cancelled: CancellationException) {
                    command.completion.cancel(false)
                    command.discard()
                    throw cancelled
                } catch (error: Exception) {
                    try {
                        command.discard()
                    } finally {
                        command.completion.setException(error)
                    }
                } catch (error: LinkageError) {
                    try {
                        command.discard()
                    } finally {
                        command.completion.setException(error)
                    }
                }
            }
        }
    }

    fun enqueue(
        expectedMediaId: Long? = media.value?.mediaId,
        requiresSeekable: Boolean = false,
        onDiscard: (() -> Unit)? = null,
        action: () -> Unit,
    ): ListenableFuture<*> {
        if (closed.get() || expectedMediaId == null || media.value?.mediaId != expectedMediaId ||
            snapshots.value.mediaId != expectedMediaId
        ) {
            onDiscard?.invoke()
            return Futures.immediateVoidFuture()
        }
        val future = SettableFuture.create<Void>()
        val command = Command(expectedMediaId, requiresSeekable, future, action, onDiscard)
        if (onDiscard != null) {
            future.addListener({ if (future.isCancelled) command.discard() }, MoreExecutors.directExecutor())
        }
        if (commands.trySend(command).isFailure) future.cancel(false)
        return future
    }

    fun close() {
        if (closed.compareAndSet(false, true)) {
            commands.cancel()
            scope.cancel()
        }
    }
}

/** 纯快照映射：0 时长表示未知，缓存终点是 mpv 的绝对时间戳。 */
internal data class SessionPlaybackState(
    val media: SessionMedia?,
    val playbackState: Int,
    val playWhenReady: Boolean,
    val isLoading: Boolean,
    val canControl: Boolean,
    val canSeek: Boolean,
    val durationMs: Long?,
    val positionMs: Long,
    val bufferedPositionMs: Long,
    val bufferedDurationMs: Long,
    val speed: Float,
    val volume: Float,
)

internal fun mapSessionPlayback(snapshot: PlaybackSnapshot, media: SessionMedia?): SessionPlaybackState {
    val matchedMedia = media?.takeIf { it.mediaId == snapshot.mediaId }
    val hasMedia = matchedMedia != null
    val state = if (!hasMedia) {
        Player.STATE_IDLE
    } else {
        when (snapshot.status) {
            PlaybackStatus.IDLE, PlaybackStatus.ERROR -> Player.STATE_IDLE
            PlaybackStatus.LOADING -> Player.STATE_BUFFERING
            PlaybackStatus.PLAYING -> Player.STATE_READY
            // 恢复意图先于 mpv 的暂停属性更新，不能提前宣称实际正在播放。
            PlaybackStatus.PAUSED -> if (snapshot.playWhenReady) Player.STATE_BUFFERING else Player.STATE_READY
            PlaybackStatus.ENDED -> Player.STATE_ENDED
        }
    }
    val duration = if (matchedMedia == null) {
        null
    } else if (snapshot.durationMs > 0L && snapshot.durationMs <= Long.MAX_VALUE / 1_000L) {
        snapshot.durationMs
    } else {
        matchedMedia.durationMs.takeIf { it > 0L && it <= Long.MAX_VALUE / 1_000L }
    }
    val position = if (hasMedia) snapshot.positionMs.coerceAtLeast(0L) else 0L
    val buffered = if (hasMedia) snapshot.bufferedMs.coerceAtLeast(0L) else 0L
    return SessionPlaybackState(
        media = matchedMedia,
        playbackState = state,
        playWhenReady = hasMedia && snapshot.playWhenReady,
        isLoading = hasMedia && snapshot.status == PlaybackStatus.LOADING,
        canControl = hasMedia && snapshot.status.canControl(),
        canSeek = hasMedia && snapshot.status.canSeek(),
        durationMs = duration,
        positionMs = position,
        bufferedPositionMs = buffered,
        bufferedDurationMs = (buffered - position).coerceAtLeast(0L),
        speed = snapshot.speed.takeIf { it.isFinite() && it > 0f } ?: 1f,
        volume = snapshot.volume.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 1f,
    )
}

private fun PlaybackStatus.canControl(): Boolean = when (this) {
    PlaybackStatus.LOADING, PlaybackStatus.PLAYING, PlaybackStatus.PAUSED, PlaybackStatus.ENDED -> true
    PlaybackStatus.IDLE, PlaybackStatus.ERROR -> false
}

private fun PlaybackStatus.canSeek(): Boolean = when (this) {
    PlaybackStatus.PLAYING, PlaybackStatus.PAUSED -> true
    // EOF 后引擎只暂存下次播放位置，不能立即完成 seek。
    PlaybackStatus.IDLE, PlaybackStatus.LOADING, PlaybackStatus.ENDED, PlaybackStatus.ERROR -> false
}
