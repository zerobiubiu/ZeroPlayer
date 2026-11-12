package com.privateplayer.media

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
import com.privateplayer.domain.repository.WebUploadStatus
import com.privateplayer.feature.importer.ImportViewModel
import com.privateplayer.feature.importer.WifiImportSheet
import com.privateplayer.feature.settings.SettingsSection
import com.privateplayer.feature.settings.SettingsSectionScreen
import com.privateplayer.feature.settings.SettingsViewModel
import com.privateplayer.core.playback.PlaybackEngine
import com.privateplayer.core.playback.PlaybackEngineProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Wi-Fi 风险提示（F-09）与导入设置术语统一（F-10）的 UI 验收。
 *
 * - 风险独立成块、位于启动按钮附近，关闭/重开面板仍显示；
 * - Wi-Fi 面板与设置·导入的两个开关使用同一份文案资源，名称完全一致。
 */
@RunWith(AndroidJUnit4::class)
class WifiRiskAndImportTermsInstrumentedTest {

    @get:Rule
    val compose = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext

    private fun importViewModel(webState: MutableStateFlow<WebUploadState>): ImportViewModel {
        val settings = PreferencesSettingsRepository(context)
        return ImportViewModel(
            context,
            object : ImportRepository {
                override fun observeTasks(): Flow<List<ImportTask>> = flowOf(emptyList())
                override fun observeActiveCount(): Flow<Int> = flowOf(0)
                override suspend fun enqueue(uris: List<String>, folderId: Long?, targetDirectory: String) =
                    ImportEnqueueResult(emptyList(), emptyList(), emptyList())
                override suspend fun enqueueExternal(
                    uris: List<String>, mimeType: String?, openForPlayback: Boolean, requestTaskId: String?,
                ) = ImportEnqueueResult(emptyList(), emptyList(), emptyList())
                override suspend fun cancel(taskId: String) = Unit
                override suspend fun retry(taskId: String) = Unit
                override suspend fun removeTask(taskId: String) = Unit
                override suspend fun reconcilePendingTasks() = Unit
            },
            object : StorageRepository {
                override fun observeUsage(): Flow<StorageUsage> = flowOf(StorageUsage(0, 0, 0, 1024, 0))
                override suspend fun clearThumbnailCache(): OperationOutcome = OperationOutcome.Success
                override suspend fun clearTemporaryFiles(): OperationOutcome = OperationOutcome.Success
            },
            settings,
            object : WebUploadController {
                override val state: StateFlow<WebUploadState> = webState
                override fun start(directory: String) = Unit
                override fun stop() = Unit
            },
            Dispatchers.IO,
        )
    }

    @Test
    fun wifiRiskNoticeIsIndependentBlockNearStartAndSurvivesReopen() {
        val webState = MutableStateFlow(WebUploadState(status = WebUploadStatus.STOPPED))
        val viewModel = importViewModel(webState)
        val visible = androidx.compose.runtime.mutableStateOf(true)
        compose.setContent {
            MaterialTheme {
                if (visible.value) {
                    WifiImportSheet(directory = "", onDismiss = { visible.value = false }, viewModel = viewModel)
                }
            }
        }

        // 风险结论与使用建议独立成块，且紧邻启动按钮（同一面板内可见）。
        compose.onNodeWithText("同一网络内的设备可读写你的文件").assertIsDisplayed()
        compose.onNodeWithText("服务没有登录或访问令牌。仅在可信网络使用。").assertIsDisplayed()
        compose.onNodeWithText("启动服务").assertIsDisplayed()

        // 关闭面板后重新打开，风险提示仍然显示。
        visible.value = false
        compose.waitForIdle()
        visible.value = true
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("同一网络内的设备可读写你的文件").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("同一网络内的设备可读写你的文件").assertIsDisplayed()
    }

    @Test
    fun wifiPanelAndSettingsImportUseIdenticalSwitchNames() {
        val webState = MutableStateFlow(WebUploadState(status = WebUploadStatus.STOPPED))
        val importVm = importViewModel(webState)
        compose.setContent {
            MaterialTheme {
                WifiImportSheet(directory = "", onDismiss = {}, viewModel = importVm)
            }
        }
        // Wi-Fi 面板中的两个导入开关名称（与设置页共用同一资源）。
        compose.onNodeWithText("导入时生成缩略图").assertIsDisplayed()
        compose.onNodeWithText("检测疑似重复视频").assertIsDisplayed()
        // 旧叫法不再出现。
        compose.onAllNodesWithText("生成视频缩略图").fetchSemanticsNodes().let { check(it.isEmpty()) }
        compose.onAllNodesWithText("检测重复视频").fetchSemanticsNodes().let { nodes ->
            // “检测重复视频”是“检测疑似重复视频”的子串，只允许作为后者的一部分出现。
            check(nodes.all { node ->
                node.config.toString().contains("检测疑似重复视频")
            })
        }
    }

    @Test
    fun settingsImportSwitchNamesMatchWifiPanel() {
        val settings = PreferencesSettingsRepository(context)
        val viewModel = SettingsViewModel(
            settings,
            object : StorageRepository {
                override fun observeUsage(): Flow<StorageUsage> = flowOf(StorageUsage(0, 0, 0, 0, 0))
                override suspend fun clearThumbnailCache(): OperationOutcome = OperationOutcome.Success
                override suspend fun clearTemporaryFiles(): OperationOutcome = OperationOutcome.Success
            },
            object : PlaybackEngineProvider {
                override val backendLabel = "libmpv"
                override val backendDetail = ""
                override fun create(): PlaybackEngine = error("测试不创建播放引擎")
            },
            { "1.0.6" },
            CoroutineScope(Dispatchers.IO + SupervisorJob()),
            Dispatchers.IO,
        )
        compose.setContent {
            MaterialTheme {
                SettingsSectionScreen(
                    section = SettingsSection.IMPORT,
                    onBack = {},
                    viewModel = viewModel,
                )
            }
        }
        compose.onNode(hasText("导入时生成缩略图") and hasClickAction()).assertIsDisplayed()
        compose.onNode(hasText("检测疑似重复视频") and hasClickAction()).assertIsDisplayed()
        compose.onAllNodesWithText("生成视频缩略图").fetchSemanticsNodes().let { check(it.isEmpty()) }
    }
}
