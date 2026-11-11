package com.privateplayer.feature.player

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import com.privateplayer.core.playback.PlaybackEngine
import com.privateplayer.core.playback.PlaybackSource

/** 所有入口共用的焦点策略：短暂失焦暂停，恢复焦点时仅恢复此前正在播放的内容。 */
internal class FocusPlaybackEngine(
    context: Context,
    private val delegate: PlaybackEngine,
    private val awaitPreviousRelease: suspend () -> Unit,
    private val onError: (String) -> Unit,
    private val prepareForeground: () -> Boolean,
    private val onFocusAbandoned: () -> Unit,
) : PlaybackEngine by delegate {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val lock = Any()
    private var hasFocus = false
    private var wantsPlayback = false
    private var resumeOnGain = false
    @Volatile private var closing = false
    private var released = false
    private val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                .build(),
        )
        .setWillPauseWhenDucked(true)
        .setOnAudioFocusChangeListener({ change -> onFocusChange(change) }, Handler(Looper.getMainLooper()))
        .build()

    private fun requestFocus(): Boolean {
        if (hasFocus) return true
        if (!prepareForeground()) return false
        val granted = try {
            audioManager?.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } catch (error: RuntimeException) {
            onFocusAbandoned()
            onError("申请音频焦点失败：${error.message ?: error.javaClass.simpleName}")
            return false
        }
        hasFocus = granted
        if (!granted) {
            onFocusAbandoned()
            onError("系统拒绝音频焦点，请返回播放器后重试")
        }
        return granted
    }

    /** 不等待 JNI，立即禁止销毁中的旧实例继续开始播放。 */
    fun beginClosing() {
        closing = true
    }

    override suspend fun initialize() {
        if (closing) throw kotlinx.coroutines.CancellationException("播放服务已结束")
        awaitPreviousRelease()
        if (closing) throw kotlinx.coroutines.CancellationException("播放服务已结束")
        delegate.initialize()
    }

    override suspend fun load(source: PlaybackSource, startPositionMs: Long, autoPlay: Boolean) {
        synchronized(lock) {
            if (released || closing) return
            wantsPlayback = autoPlay
            resumeOnGain = false
            if (autoPlay && !requestFocus()) wantsPlayback = false
        }
        // 即使加载期间失焦或收到暂停指令，加载也不能自行开始播放。
        delegate.load(source, startPositionMs, false)
        synchronized(lock) {
            if (!released && !closing && wantsPlayback && hasFocus) delegate.play()
        }
    }

    override fun play() {
        synchronized(lock) {
            if (released || closing) return
            wantsPlayback = true
            resumeOnGain = false
            if (requestFocus() && !closing) delegate.play() else wantsPlayback = false
        }
    }


    fun setSpeedFromUi(speed: Float) = delegate.setSpeed(speed)

    override fun pause() {
        synchronized(lock) {
            if (released) return
            wantsPlayback = false
            resumeOnGain = false
            onFocusAbandoned()
            delegate.pause()
            abandonFocus()
        }
    }

    override suspend fun stop() {
        synchronized(lock) {
            wantsPlayback = false
            resumeOnGain = false
            onFocusAbandoned()
            abandonFocus()
        }
        delegate.stop()
    }

    override suspend fun release() {
        synchronized(lock) {
            if (released) return
            released = true
            wantsPlayback = false
            resumeOnGain = false
            onFocusAbandoned()
            abandonFocus()
        }
        delegate.release()
    }

    private fun abandonFocus() {
        audioManager?.abandonAudioFocusRequest(request)
        hasFocus = false
    }

    private fun onFocusChange(change: Int) {
        synchronized(lock) {
            if (released || closing) return
            when (change) {
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                    hasFocus = false
                    resumeOnGain = wantsPlayback
                    onFocusAbandoned()
                    delegate.pause()
                }
                AudioManager.AUDIOFOCUS_LOSS -> {
                    hasFocus = false
                    wantsPlayback = false
                    resumeOnGain = false
                    onFocusAbandoned()
                    delegate.pause()
                    abandonFocus()
                }
                AudioManager.AUDIOFOCUS_GAIN -> {
                    hasFocus = true
                    if (resumeOnGain && wantsPlayback) {
                        resumeOnGain = false
                        if (prepareForeground() && !closing) {
                            delegate.play()
                        } else {
                            wantsPlayback = false
                            abandonFocus()
                        }
                    }
                }
            }
        }
    }
}
