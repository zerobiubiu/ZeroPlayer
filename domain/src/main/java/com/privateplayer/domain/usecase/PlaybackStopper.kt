package com.privateplayer.domain.usecase

/**
 * 播放安全停止器：文件操作前结束可能引用目标文件的播放会话。
 *
 * 领域层只声明能力，不感知播放实现；由应用层把播放协调器接到这里。
 * 停止会结束当前会话与队列（队列随会话销毁），不改变“关闭停服、重开新会话”的生命周期契约。
 */
interface PlaybackStopper {
    /** 目标路径（相对私有文件树根）命中当前播放媒体或活动队列时结束会话；未命中或无会话时什么都不做。 */
    suspend fun stopIfAffected(path: String)
}

/** 无播放会话时的默认实现：什么都不做。 */
object NoOpPlaybackStopper : PlaybackStopper {
    override suspend fun stopIfAffected(path: String) = Unit
}
