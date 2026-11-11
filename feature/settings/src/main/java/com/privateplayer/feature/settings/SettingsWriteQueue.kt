package com.privateplayer.feature.settings

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * 设置写入队列：所有写入按提交顺序串行执行。
 *
 * - 连续修改不会互相覆盖，队列保证最后一次修改最后落盘；
 * - 队列消费使用应用级作用域，离开设置页后仍在完成写入；
 * - 写入失败交给调用方提示，不在内部吞掉异常。
 */
internal class SettingsWriteQueue(
    scope: CoroutineScope,
    private val onError: (Throwable) -> Unit,
) {
    private val queue = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (write in queue) {
                try {
                    write()
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    onError(error)
                }
            }
        }
    }

    /** 提交一次写入；按提交顺序排队执行。 */
    fun submit(write: suspend () -> Unit) {
        queue.trySend(write)
    }
}
