package com.privateplayer.data.importer

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 导入任务的 WorkManager 调度接口。 */
interface ImportWorkScheduler {
    fun schedule(taskId: String)

    fun cancel(taskId: String)

    suspend fun isActive(taskId: String): Boolean
}

/**
 * WorkManager 实现。
 *
 * 每个导入任务对应一个唯一命名的一次性任务（`import_<taskId>`），
 * 唯一工作名即幂等键：重复调度不会产生并行复制，进程重启后由 WorkManager 自动恢复。
 */
@Singleton
class WorkManagerImportScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : ImportWorkScheduler {

    override fun schedule(taskId: String) {
        val request = OneTimeWorkRequestBuilder<ImportWorker>()
            .setInputData(workDataOf(ImportWorker.KEY_TASK_ID to taskId))
            .setConstraints(
                Constraints.Builder()
                    // 电量过低时暂停复制，避免在关键场景下把设备耗到关机。
                    .setRequiresBatteryNotLow(true)
                    .build(),
            )
            .addTag(ImportWorker.TAG)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            workName(taskId),
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    override fun cancel(taskId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(taskId))
    }

    override suspend fun isActive(taskId: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val infos = WorkManager.getInstance(context).getWorkInfosForUniqueWork(workName(taskId)).get()
            infos.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.BLOCKED }
        }.getOrDefault(false)
    }

    private fun workName(taskId: String): String = "import_$taskId"
}
