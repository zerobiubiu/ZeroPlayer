package com.privateplayer.feature.player

import android.view.Surface
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.privateplayer.domain.model.VideoAspect
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/** 页面只借用播放服务；离开页面不会暂停或释放共享引擎。 */
@HiltViewModel
class PlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val coordinator: PlaybackCoordinator,
) : ViewModel() {
    private val initialMediaId = savedStateHandle.get<String>(PlayerRouteArgs.ARG_MEDIA_ID)?.toLongOrNull() ?: -1L
    private var requestedMedia = false
    val uiState: StateFlow<PlayerUiState> = coordinator.uiState

    fun onStart() {
        if (coordinator.openFromVisibleUi(initialMediaId, !requestedMedia)) requestedMedia = true
    }

    fun activateSurfaceOwner(owner: Any) = coordinator.activateSurfaceOwner(owner)
    fun attachSurface(owner: Any, surface: Surface) = coordinator.attachSurface(owner, surface)
    fun resizeSurface(owner: Any, surface: Surface, width: Int, height: Int) =
        coordinator.resizeSurface(owner, surface, width, height)
    fun detachSurface(owner: Any, surface: Surface? = null) = coordinator.detachSurface(owner, surface)
    fun playPause() = coordinator.playPause()
    fun seekTo(positionMs: Long) = coordinator.seekTo(positionMs)
    fun seekBy(deltaMs: Long) = coordinator.seekBy(deltaMs)
    fun setSpeed(speed: Float) = coordinator.setSpeed(speed)
    fun setAspect(aspect: VideoAspect) = coordinator.setAspect(aspect)
    fun selectAudioTrack(id: String?) = coordinator.selectAudioTrack(id)
    fun selectSubtitleTrack(id: String?) = coordinator.selectSubtitleTrack(id)
    fun adjustSubtitleDelay(deltaMs: Long) = coordinator.adjustSubtitleDelay(deltaMs)
    fun resetSubtitleDelay() = coordinator.resetSubtitleDelay()
    fun toggleControls() = coordinator.toggleControls()
    fun setControlsVisible(visible: Boolean) = coordinator.setControlsVisible(visible)
    fun toggleLock() = coordinator.toggleLock()
    fun setPlayMode(mode: PlayMode) = coordinator.setPlayMode(mode)
    fun playNext() = coordinator.playNext()
    fun playPrevious() = coordinator.playPrevious()
    /** 队列面板点击跳转：UI 只表达意图，受理与执行由播放会话负责。 */
    fun playQueueItem(index: Int) = coordinator.playQueueItem(index)
    fun beginTemporarySpeed(speed: Float) = coordinator.beginTemporarySpeed(speed)
    fun endTemporarySpeed() = coordinator.endTemporarySpeed()
    fun consumeError() = coordinator.consumeError()

    /** 关闭播放页：结束引擎与会话，避免离开页面后继续出声。 */
    fun closePlayback() = coordinator.closeFromPlayerUi()
}
