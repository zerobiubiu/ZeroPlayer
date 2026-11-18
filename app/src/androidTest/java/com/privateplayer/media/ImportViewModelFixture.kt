package com.privateplayer.media

import android.content.Context
import com.privateplayer.core.datastore.PreferencesSettingsRepository
import com.privateplayer.domain.model.ImportEnqueueResult
import com.privateplayer.domain.model.ImportTask
import com.privateplayer.domain.model.OperationOutcome
import com.privateplayer.domain.model.StorageUsage
import com.privateplayer.domain.repository.ImportRepository
import com.privateplayer.domain.repository.SettingsRepository
import com.privateplayer.domain.repository.StorageRepository
import com.privateplayer.domain.repository.WebUploadController
import com.privateplayer.domain.repository.WebUploadState
import com.privateplayer.feature.importer.ImportViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf

/** 只替换每个用例允许的写操作；意外的写入直接使测试失败。 */
internal open class TestImportRepository : ImportRepository {
    override fun observeTasks(): Flow<List<ImportTask>> = flowOf(emptyList())
    override fun observeActiveCount(): Flow<Int> = flowOf(0)
    override suspend fun enqueue(uris: List<String>, folderId: Long?, targetDirectory: String): ImportEnqueueResult =
        throw AssertionError("意外创建导入任务")
    override suspend fun enqueueExternal(
        uris: List<String>, mimeType: String?, openForPlayback: Boolean, requestTaskId: String?,
    ): ImportEnqueueResult = throw AssertionError("意外创建外部导入任务")
    override suspend fun cancel(taskId: String): Unit = throw AssertionError("意外取消 $taskId")
    override suspend fun retry(taskId: String): Unit = throw AssertionError("意外重试 $taskId")
    override suspend fun removeTask(taskId: String): Unit = throw AssertionError("意外移除 $taskId")
    override suspend fun discardLocalTask(taskId: String): Unit = throw AssertionError("意外清理 $taskId")
    override suspend fun reconcilePendingTasks() = Unit
}

internal fun importViewModelForTest(
    context: Context,
    imports: ImportRepository = TestImportRepository(),
    webState: StateFlow<WebUploadState> = MutableStateFlow(WebUploadState()),
    settings: SettingsRepository = PreferencesSettingsRepository(context),
    onStart: (String) -> Unit = { throw AssertionError("意外启动网页服务：$it") },
    onStop: () -> Unit = { throw AssertionError("意外停止网页服务") },
): ImportViewModel = ImportViewModel(
    context,
    imports,
    object : StorageRepository {
        override fun observeUsage(): Flow<StorageUsage> = flowOf(StorageUsage(0, 0, 0, 1024, 0))
        override suspend fun clearThumbnailCache(): OperationOutcome = OperationOutcome.Success
        override suspend fun clearTemporaryFiles(): OperationOutcome = OperationOutcome.Success
    },
    settings,
    object : WebUploadController {
        override val state: StateFlow<WebUploadState> = webState
        override fun start(directory: String) = onStart(directory)
        override fun stop() = onStop()
    },
    Dispatchers.IO,
)
