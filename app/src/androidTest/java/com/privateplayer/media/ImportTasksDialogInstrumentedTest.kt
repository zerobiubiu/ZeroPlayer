package com.privateplayer.media

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.privateplayer.domain.model.ImportTask
import com.privateplayer.domain.model.ImportTaskState
import com.privateplayer.feature.importer.ImportViewModel
import com.privateplayer.feature.importer.R as ImporterR
import java.io.IOException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImportTasksDialogInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    private val tasks = MutableStateFlow(emptyList<ImportTask>())
    private val actions = mutableListOf<String>()
    private val forgotten = mutableListOf<String>()
    private var discardError: IOException? = null
    private lateinit var model: ImportViewModel

    private val repository = object : TestImportRepository() {
        override fun observeTasks() = tasks
        override suspend fun cancel(taskId: String) { actions += "cancel:$taskId" }
        override suspend fun retry(taskId: String) { actions += "retry:$taskId" }
        override suspend fun removeTask(taskId: String) {
            actions += "remove:$taskId"
            tasks.value = tasks.value.filterNot { it.id == taskId }
        }
        override suspend fun discardLocalTask(taskId: String) {
            actions += "discard:$taskId"
            discardError?.let { throw it }
            tasks.value = tasks.value.filterNot { it.id == taskId }
        }
    }

    @After
    fun releaseModel() {
        if (::model.isInitialized) model.viewModelScope.cancel()
    }

    @Test
    fun dismissingLocalDiscardDoesNothingAndConfirmingDispatchesOnlyDiscard() {
        val failed = task("local-failed", ImportTaskState.FAILED)
        show(failed)
        compose.onNodeWithText("重试").assertIsDisplayed()
        compose.onNodeWithText("移除记录").assertDoesNotExist()
        compose.onNodeWithText("放弃并清理").performClick()
        compose.onNodeWithText("放弃这次导入？").assertIsDisplayed()
        compose.onNode(
            hasText(failed.displayName, substring = true) and hasText("暂存", substring = true) and
                hasText("外部源文件不变", substring = true),
        ).assertIsDisplayed()
        compose.runOnIdle { assertTrue(actions.isEmpty()) }

        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithText("清理暂存").assertDoesNotExist()
        compose.onNodeWithText(failed.displayName).assertIsDisplayed()
        compose.runOnIdle {
            assertTrue(actions.isEmpty())
            assertTrue(forgotten.isEmpty())
        }

        compose.onNodeWithText("放弃并清理").performClick()
        compose.onNodeWithText("清理暂存").performClick()
        awaitText("暂无导入任务")
        compose.runOnIdle {
            assertEquals(listOf("discard:${failed.id}"), actions)
            assertEquals(listOf(failed.id), forgotten)
        }
    }

    @Test
    fun webStagingIsNotRemovableAndOrdinaryTaskActionsStaySeparate() {
        val running = task("local-running", ImportTaskState.RUNNING)
        show(running)
        compose.onNodeWithText("放弃并清理").assertDoesNotExist()
        compose.onNodeWithText("取消").performClick()

        val failed = task("web-failed", ImportTaskState.FAILED, web = true)
        replaceWith(failed)
        compose.onNodeWithText("放弃并清理").assertDoesNotExist()
        compose.onNodeWithText("移除记录").assertDoesNotExist()
        compose.onNodeWithText("重试").performClick()

        replaceWith(task("web-canceled", ImportTaskState.CANCELED, web = true))
        compose.onNodeWithText("放弃并清理").assertDoesNotExist()
        compose.onNodeWithText("移除记录").assertDoesNotExist()

        val completed = task("web-completed", ImportTaskState.COMPLETED, web = true)
        replaceWith(completed)
        compose.onNodeWithText("放弃并清理").assertDoesNotExist()
        compose.onNodeWithText("移除记录").performClick()
        awaitText("暂无导入任务")
        compose.runOnIdle {
            assertEquals(listOf("cancel:${running.id}", "retry:${failed.id}", "remove:${completed.id}"), actions)
        }
    }

    @Test
    fun missingWebStagingAndMissingFolderShowDifferentRecoveryWithoutDuplicateDetails() {
        val missing = task("web-missing", ImportTaskState.FAILED, web = true)
            .copy(errorMessage = "上传暂存文件已丢失，请从网页重新上传")
        show(missing)
        compose.onNodeWithText("上传暂存文件已丢失，请从网页重新上传。").assertIsDisplayed()
        compose.onNodeWithText("重试").assertDoesNotExist()
        compose.onNodeWithText("详情").assertDoesNotExist()
        compose.onNodeWithText("放弃并清理").assertDoesNotExist()
        compose.runOnIdle { assertTrue(actions.isEmpty()) }

        val folder = task("local-folder", ImportTaskState.FAILED)
            .copy(errorMessage = "目标目录已不存在，暂存已保留，请恢复目录后重试")
        replaceWith(folder)
        compose.onNodeWithText("目标文件夹不可用，请按原路径恢复后重试。").assertIsDisplayed()
        compose.onNodeWithText("详情").assertDoesNotExist()
        compose.onNodeWithText("放弃并清理").assertIsDisplayed()
        compose.onNodeWithText("重试").performClick()
        compose.runOnIdle { assertEquals(listOf("retry:${folder.id}"), actions) }

        val early = folder.copy(displayName = "目录预检.bin", errorMessage = "目录不存在或不是目录")
        replaceWith(early)
        compose.onNodeWithText("目标文件夹不可用，请按原路径恢复后重试。").assertIsDisplayed()
        compose.onNodeWithText("暂存已保留", substring = true).assertDoesNotExist()
        compose.onNodeWithText("重试").assertIsDisplayed()

        val diagnostic = "目标位置已有同名文件，暂存已保留，请处理重名后重试"
        replaceWith(folder.copy(displayName = "同名目标.bin", errorMessage = diagnostic))
        compose.onNodeWithText(diagnostic).assertDoesNotExist()
        compose.onNodeWithText("详情").performClick()
        compose.onNodeWithText(diagnostic).assertIsDisplayed()
    }

    @Test
    fun failedCleanupOfCanceledLocalTaskReportsReasonWithoutForgettingIt() {
        val canceled = task("local-canceled", ImportTaskState.CANCELED)
        val reason = "暂存文件被占用"
        discardError = IOException(reason)
        show(canceled)
        compose.onNodeWithText("移除记录").assertDoesNotExist()
        compose.onNodeWithText("放弃并清理").performClick()
        compose.onNodeWithText("清理暂存").performClick()
        compose.onNodeWithText(canceled.displayName).assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(listOf("discard:${canceled.id}"), actions)
            assertTrue(forgotten.isEmpty())
            assertEquals(context.getString(ImporterR.string.import_discard_failed, reason), model.taskActionError.value)
            model.dismissTaskActionError()
            assertNull(model.taskActionError.value)
        }
    }

    private fun show(initial: ImportTask) {
        tasks.value = listOf(initial)
        model = importViewModelForTest(context, imports = repository)
        compose.setContent {
            val state by model.uiState.collectAsStateWithLifecycle()
            MaterialTheme {
                ImportTasksDialog(
                    state = state, notices = emptyList(), onDismiss = { throw AssertionError("意外关闭任务列表") },
                    onCancel = model::cancel, onRetry = model::retry,
                    onRemove = { model.remove(it) { id -> forgotten += id } },
                    onDiscardLocal = { model.discardLocalTask(it) { id -> forgotten += id } },
                )
            }
        }
        awaitText(initial.displayName)
    }

    private fun replaceWith(task: ImportTask) {
        tasks.value = listOf(task)
        awaitText(task.displayName)
    }

    private fun awaitText(text: String) = compose.waitUntil(5_000) {
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }

    private fun task(id: String, state: ImportTaskState, web: Boolean = false) = ImportTask(
        id = id, displayName = "$id.mp4", mimeType = "video/mp4", sizeBytes = 10,
        state = state, bytesCopied = 2, errorMessage = null, mediaId = null,
        createdAt = 0, updatedAt = 0, isWebUpload = web,
    )
}
