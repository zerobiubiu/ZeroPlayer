package com.privateplayer.media

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.crossfade
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import com.privateplayer.core.common.di.ApplicationScope
import com.privateplayer.domain.repository.ImportRepository
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import okio.Path.Companion.toOkioPath

/**
 * 应用入口。
 *
 * 启动职责：
 * - Hilt 依赖注入；
 * - WorkManager 使用 HiltWorkerFactory，让导入任务可注入仓库；
 * - 启动时对账导入任务，补挂被系统清理或进程终止后遗留的任务；
 * - 配置 Coil 图片加载（缩略图来自本地缓存目录）。
 */
@HiltAndroidApp
class PrivatePlayerApplication : Application(), Configuration.Provider, SingletonImageLoader.Factory {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var importRepository: ImportRepository

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            runCatching { importRepository.reconcilePendingTasks() }
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.2)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache").toOkioPath())
                    .maxSizeBytes(128L * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            .build()
}
