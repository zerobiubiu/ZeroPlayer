package com.privateplayer.core.playback

import android.content.Context
import com.privateplayer.core.playback.mpv.MpvNativeProbe
import com.privateplayer.core.playback.mpv.MpvPlaybackEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/** 引擎提供者。播放器与验证页面均通过同一工厂创建真实 libmpv 实例。 */
interface PlaybackEngineProvider {
    val backendLabel: String

    /** 后端说明，用于播放器角标与设置页展示。 */
    val backendDetail: String

    fun create(): PlaybackEngine
}

/** 正式后端固定为 libmpv；库不可用或初始化失败时如实报错，不回退预览引擎。 */
@Singleton
class DefaultPlaybackEngineProvider @Inject constructor(
    private val mpvProbe: MpvNativeProbe,
    @ApplicationContext private val context: Context,
) : PlaybackEngineProvider {

    private val probeResult: MpvNativeProbe.Result by lazy { mpvProbe.probe() }

    override val backendLabel: String = "libmpv"

    override val backendDetail: String
        get() = if (probeResult.available) {
            "libmpv 与 FFmpeg 静态编入 APK 随带的单个 libplayer.so；硬解待实机验证"
        } else {
            "${probeResult.detail}；预期由单个 JNI 库静态携带 libmpv 和 FFmpeg，不回退其他后端；硬解待实机验证"
        }

    override fun create(): PlaybackEngine = MpvPlaybackEngine(context)
}

@Module
@InstallIn(SingletonComponent::class)
object PlaybackModule {

    @Provides
    @Singleton
    fun providePlaybackEngineProvider(
        provider: DefaultPlaybackEngineProvider,
    ): PlaybackEngineProvider = provider
}
