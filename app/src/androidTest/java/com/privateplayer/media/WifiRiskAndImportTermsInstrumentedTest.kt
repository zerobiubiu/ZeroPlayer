package com.privateplayer.media

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.privateplayer.core.datastore.PreferencesSettingsRepository
import com.privateplayer.core.design.theme.PrivatePlayerTheme
import com.privateplayer.core.playback.PlaybackEngine
import com.privateplayer.core.playback.PlaybackEngineProvider
import com.privateplayer.domain.model.OperationOutcome
import com.privateplayer.domain.model.StorageUsage
import com.privateplayer.domain.repository.StorageRepository
import com.privateplayer.domain.repository.WebUploadState
import com.privateplayer.domain.repository.WebUploadStatus
import com.privateplayer.feature.importer.WifiImportSheet
import com.privateplayer.feature.settings.SettingsSection
import com.privateplayer.feature.settings.SettingsSectionScreen
import com.privateplayer.feature.settings.SettingsViewModel
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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

    @Test
    fun wifiRiskNoticeIsIndependentBlockNearStartAndSurvivesReopen() {
        val webState = MutableStateFlow(WebUploadState(status = WebUploadStatus.STOPPED))
        val viewModel = importViewModelForTest(context, webState = webState)
        val visible = mutableStateOf(true)
        compose.setContent {
            PrivatePlayerTheme(darkTheme = false) {
                if (visible.value) {
                    WifiImportSheet(directory = "", onDismiss = { visible.value = false }, viewModel = viewModel)
                }
            }
        }

        // 标题和说明同属一个读屏节点；启停控件与风险块不混为一个操作。
        val risk = hasText("同一局域网内，能连接的设备可读写、删除文件") and
            hasText("HTTP 明文传输，没有登录或访问令牌。仅在可信网络使用。")
        compose.onNode(risk).assertIsDisplayed()
        compose.onNode(hasText("启动服务") and hasClickAction()).assertIsDisplayed()

        pressBack()
        compose.waitUntil(10_000) { !visible.value }
        compose.runOnIdle { visible.value = true }
        compose.onNode(risk).assertIsDisplayed()
        compose.runOnIdle { webState.value = WebUploadState(status = WebUploadStatus.STARTING) }
        compose.onNodeWithText("正在启动服务").assertIsDisplayed()
        compose.onNode(hasText("停止服务") and hasClickAction()).assertIsDisplayed()
        compose.onNode(risk).assertIsDisplayed()
    }

    @Test
    fun wifiPanelAndSettingsImportUseIdenticalSwitchNames() {
        val webState = MutableStateFlow(WebUploadState(status = WebUploadStatus.STOPPED))
        val importVm = importViewModelForTest(context, webState = webState)
        compose.setContent {
            PrivatePlayerTheme(darkTheme = false) {
                WifiImportSheet(directory = "", onDismiss = {}, viewModel = importVm)
            }
        }
        // Wi-Fi 面板中的两个导入开关名称（与设置页共用同一资源）。
        panelSwitch("导入时生成缩略图").assertIsDisplayed()
        panelSwitch("检测疑似重复视频").assertIsDisplayed()
        compose.onAllNodesWithText("生成视频缩略图").assertCountEquals(0)
        compose.onAllNodesWithText("检测重复视频").assertCountEquals(0)
    }

    @Test
    fun settingsImportSwitchNamesMatchWifiPanel() {
        val job = SupervisorJob()
        val scope = CoroutineScope(Dispatchers.IO + job)
        try {
            val viewModel = settingsViewModel(PreferencesSettingsRepository(context), scope)
            compose.setContent {
                PrivatePlayerTheme(darkTheme = false) {
                    SettingsSectionScreen(section = SettingsSection.IMPORT, onBack = {}, viewModel = viewModel)
                }
            }
            compose.onNode(hasText("导入时生成缩略图") and hasClickAction()).performScrollTo().assertIsDisplayed()
            compose.onNode(hasText("检测疑似重复视频") and hasClickAction()).performScrollTo().assertIsDisplayed()
            compose.onAllNodesWithText("生成视频缩略图").assertCountEquals(0)
        } finally {
            runBlocking { job.cancelAndJoin() }
        }
    }

    @Test
    fun wifiAndSettingsSwitchesRoundTripThroughIsolatedPreferences() = runBlocking {
        val directory = Files.createTempDirectory(context.cacheDir.toPath(), "wifi-settings-").toFile()
        val job = SupervisorJob()
        val scope = CoroutineScope(Dispatchers.IO + job)
        try {
            val store = PreferenceDataStoreFactory.create(scope = scope) { File(directory, "import.preferences_pb") }
            val settings = PreferencesSettingsRepository(store)
            val importVm = importViewModelForTest(context, settings = settings)
            val settingsVm = settingsViewModel(settings, scope)
            val showPanel = mutableStateOf(true)
            compose.setContent {
                PrivatePlayerTheme(darkTheme = false) {
                    if (showPanel.value) {
                        WifiImportSheet(directory = "", onDismiss = { showPanel.value = false }, viewModel = importVm)
                    } else {
                        SettingsSectionScreen(section = SettingsSection.IMPORT, onBack = {}, viewModel = settingsVm)
                    }
                }
            }
            val thumbnails = "导入时生成缩略图"
            val duplicates = "检测疑似重复视频"
            panelSwitch(thumbnails).assertIsOn().performClick()
            panelSwitch(duplicates).assertIsOn().performClick()
            compose.waitUntil(10_000) {
                with(importVm.importPreferences.value) { !generateThumbnails && !duplicateDetection }
            }
            pressBack()
            compose.waitUntil(10_000) {
                with(settingsVm.uiState.value.settings.import) { !generateThumbnails && !duplicateDetection }
            }
            for (title in listOf(thumbnails, duplicates)) {
                compose.onNode(hasText(title) and hasClickAction()).performScrollTo().assertIsOff().performClick()
            }
            compose.waitUntil(10_000) {
                with(settingsVm.uiState.value.settings.import) { generateThumbnails && duplicateDetection }
            }
            compose.runOnIdle { showPanel.value = true }
            compose.waitUntil(10_000) {
                with(importVm.importPreferences.value) { generateThumbnails && duplicateDetection }
            }
            panelSwitch(thumbnails).assertIsOn()
            panelSwitch(duplicates).assertIsOn()
            assertEquals(settings.observeSettings().first().import, importVm.importPreferences.value)
        } finally {
            job.cancelAndJoin()
            directory.deleteRecursively()
        }
    }

    private fun panelSwitch(title: String) = (hasText(title) and hasClickAction()).let { matcher ->
        compose.onNode(hasScrollAction()).performScrollToNode(matcher)
        compose.onNode(matcher)
    }

    private fun settingsViewModel(settings: PreferencesSettingsRepository, scope: CoroutineScope) =
        SettingsViewModel(
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
            scope,
            Dispatchers.IO,
        )
}
