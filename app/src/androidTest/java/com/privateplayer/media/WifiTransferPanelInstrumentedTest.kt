package com.privateplayer.media

import android.Manifest
import android.app.Activity
import android.app.Instrumentation
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.AccessibilityManager
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.Lifecycle
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.privateplayer.core.design.theme.PrivatePlayerTheme
import com.privateplayer.domain.repository.WebTransferRecord
import com.privateplayer.domain.repository.WebTransferState
import com.privateplayer.domain.repository.WebUploadEndpoint
import com.privateplayer.domain.repository.WebUploadNetwork
import com.privateplayer.domain.repository.WebUploadState
import com.privateplayer.domain.repository.WebUploadStatus
import com.privateplayer.feature.importer.WifiImportSheet
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 验证手机面板，不打开监听套接字，不改变设备授权，所有传输记录仅存在于测试状态流。 */
@RunWith(AndroidJUnit4::class)
class WifiTransferPanelInstrumentedTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    private val wifi = WebUploadEndpoint(WebUploadNetwork.WIFI, "wlan0", "http://127.0.0.1:8765/")
    private val ethernet = WebUploadEndpoint(WebUploadNetwork.ETHERNET, "eth0", "http://127.0.0.2:8765/")
    private val hotspot = WebUploadEndpoint(WebUploadNetwork.HOTSPOT, "ap0", "http://127.0.0.3:8765/")
    private val secondWifi = WebUploadEndpoint(WebUploadNetwork.WIFI, "wlan1", "http://127.0.0.4:8765/")

    @Test
    fun multipleNetworksCopyOnlySelectedUrlAndClearFeedbackOnSameAddressRestart() {
        val endpoints = listOf(wifi, ethernet, hotspot, secondWifi)
        val state = MutableStateFlow(WebUploadState(
            status = WebUploadStatus.RUNNING,
            initialPath = "收件",
            endpoints = endpoints,
        ))
        var startPath: String? = null
        val vm = importViewModelForTest(
            context, webState = state,
            onStart = {
                startPath = it
                state.value = WebUploadState(status = WebUploadStatus.STARTING, initialPath = it)
            },
            onStop = { state.value = WebUploadState() },
        )
        compose.setContent {
            val base = LocalContext.current
            // 只替换本次面板的授权读数，不向真实系统申请权限。
            val allowed = remember(base) { networkPermissionContext(base, granted = { true }) }
            // 留足反馈时间：验证的是逐行反馈和会话重置，而不是模拟器执行速度。
            CompositionLocalProvider(LocalContext provides allowed, LocalAccessibilityManager provides longFeedback) {
                PrivatePlayerTheme(darkTheme = false) {
                    WifiImportSheet(directory = "新目录", onDismiss = {}, viewModel = vm)
                }
            }
        }
        val firstCopy = hasContentDescription("复制Wi-Fi · wlan0地址：${wifi.url}") and hasClickAction()
        val wiredCopy = hasContentDescription("复制以太网地址：${ethernet.url}") and hasClickAction()
        val copied = hasText("已复制") and hasStateDescription("已复制")
        val ready = hasText("复制地址")
        for ((endpoint, label) in listOf(wifi to "Wi-Fi · wlan0", ethernet to "以太网", hotspot to "热点", secondWifi to "Wi-Fi · wlan1")) {
            reveal(hasText(label)).assertIsDisplayed()
            reveal(hasText(endpoint.url)).assertIsDisplayed()
        }
        reveal(hasText("初始目录：/收件")).assertIsDisplayed()
        reveal(hasText("可用空间 1.0 KiB · 端口 8765")).assertIsDisplayed()
        reveal(firstCopy).performClick()
        compose.onNode(firstCopy).assert(copied)
        assertClipboard(wifi.url)
        reveal(wiredCopy).assert(ready).performClick()
        compose.onNode(wiredCopy).assert(copied)
        assertClipboard(ethernet.url)
        reveal(firstCopy).assert(ready)

        reveal(hasText("停止服务") and hasClickAction()).performClick()
        compose.onNodeWithText("服务已停止").assertIsDisplayed()
        compose.onNodeWithText(ethernet.url).assertDoesNotExist()
        compose.onNodeWithText("已复制").assertDoesNotExist()
        compose.onNodeWithText("初始目录：/新目录").assertIsDisplayed()
        compose.onNodeWithText("启动服务").performClick()
        compose.onNodeWithText("正在启动服务").assertIsDisplayed().assert(
            SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite),
        )
        assertEquals("新目录", startPath)
        compose.onNodeWithText(ethernet.url).assertDoesNotExist()
        compose.runOnIdle {
            state.value = state.value.copy(status = WebUploadStatus.RUNNING, endpoints = endpoints)
        }
        reveal(wiredCopy).assert(ready)
        reveal(firstCopy).assert(ready)
        compose.onNodeWithText("已复制").assertDoesNotExist()
    }

    @Test
    fun stoppedSessionRetainsCompletedFailedAndSavingRecordsWithTheirActionsAtLargeFont() {
        val records = listOf(
            WebTransferRecord("done", "完成.mp4", 5, 5, WebTransferState.COMPLETED),
            WebTransferRecord("failed", "失败.mp4", 7, 7, WebTransferState.FAILED, "空间不足", "retry-task"),
            WebTransferRecord("saving", "保存中.mp4", 9, 9, WebTransferState.IMPORTING, taskId = "cancel-task"),
        )
        val state = MutableStateFlow(WebUploadState(status = WebUploadStatus.RUNNING, endpoints = listOf(wifi), transfers = records))
        val imports = object : TestImportRepository() {
            var retried: String? = null
            var canceled: String? = null
            override suspend fun retry(taskId: String) { retried = taskId }
            override suspend fun cancel(taskId: String) { canceled = taskId }
        }
        val vm = importViewModelForTest(context, imports = imports, webState = state)
        val visible = mutableStateOf(true)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 1.6f)) {
                PrivatePlayerTheme(darkTheme = true) {
                    if (visible.value) WifiImportSheet(directory = "档案", onDismiss = { visible.value = false }, viewModel = vm)
                }
            }
        }
        reveal(hasText("停止服务") and hasClickAction()).assertIsDisplayed()
        pressBack()
        compose.waitUntil(10_000) { !visible.value }
        assertEquals(WebUploadStatus.RUNNING, state.value.status)
        // 面板关闭时由服务端停止（如通知操作）；重新进入时读当前状态，不掉记录。
        compose.runOnIdle {
            state.value = state.value.copy(status = WebUploadStatus.STOPPED, endpoints = emptyList())
            visible.value = true
        }
        reveal(hasText("服务已停止")).assertIsDisplayed()
        compose.onNodeWithText(wifi.url).assertDoesNotExist()
        reveal(hasText("本次上传")).assertIsDisplayed()
        reveal(hasText("完成.mp4")).assertIsDisplayed()
        reveal(hasText("已完成 · 5 B")).assertIsDisplayed()
        reveal(hasText("失败.mp4")).assertIsDisplayed()
        reveal(hasText("失败 · 7 B · 空间不足")).assertIsDisplayed()
        reveal(hasText("重试保存") and hasClickAction()).performClick()
        reveal(hasText("保存中.mp4")).assertIsDisplayed()
        reveal(hasText("正在保存 · 9 B")).assertIsDisplayed()
        reveal(hasText("取消保存") and hasClickAction()).performClick()
        compose.runOnIdle {
            assertEquals("retry-task", imports.retried)
            assertEquals("cancel-task", imports.canceled)
        }
    }

    @Test
    fun acceptedUploadCannotRetryMissingStagingButCanRetryMissingFolder() {
        val record = WebTransferRecord("web", "收件.bin", 7, 7, WebTransferState.FAILED,
            "上传暂存文件已丢失，请从网页重新上传", "web")
        val state = MutableStateFlow(WebUploadState(transfers = listOf(record)))
        val retried = mutableListOf<String>()
        val imports = object : TestImportRepository() {
            override suspend fun retry(taskId: String) { retried += taskId }
        }
        val vm = importViewModelForTest(context, imports = imports, webState = state)
        compose.setContent {
            PrivatePlayerTheme(darkTheme = false) {
                WifiImportSheet(directory = "", onDismiss = {}, viewModel = vm)
            }
        }
        reveal(hasText("失败 · 7 B · 上传暂存文件已丢失，请从网页重新上传。")).assertIsDisplayed()
        compose.onNodeWithText("重试保存").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(emptyList<String>(), retried)
            state.value = state.value.copy(transfers = listOf(record.copy(message = "目录不存在或不是目录")))
        }
        reveal(hasText("失败 · 7 B · 目标文件夹不可用，请按原路径恢复后重试。")).assertIsDisplayed()
        reveal(hasText("重试保存") and hasClickAction()).performClick()
        compose.runOnIdle { assertEquals(listOf("web"), retried) }
    }

    @Test
    @SdkSuppress(minSdkVersion = 37)
    fun deniedPermissionSurvivesReopenAndSettingsReturnAllowsExplicitStart() {
        val visible = mutableStateOf(true)
        var granted = false
        var launchedIntent: Intent? = null
        val panelContext = networkPermissionContext(compose.activity) { granted }
        var requests = 0
        val registryOwner = object : ActivityResultRegistryOwner {
            override val activityResultRegistry = object : ActivityResultRegistry() {
                override fun <I, O> onLaunch(
                    requestCode: Int,
                    contract: ActivityResultContract<I, O>,
                    input: I,
                    options: ActivityOptionsCompat?,
                ) {
                    assertEquals(Manifest.permission.ACCESS_LOCAL_NETWORK, input)
                    requests++
                    dispatchResult(requestCode, false)
                }
            }
        }
        var started: String? = null
        val vm = importViewModelForTest(context, onStart = { started = it })
        compose.setContent {
            CompositionLocalProvider(LocalContext provides panelContext, LocalActivityResultRegistryOwner provides registryOwner) {
                PrivatePlayerTheme(darkTheme = false) {
                    if (visible.value) WifiImportSheet(directory = "接收", onDismiss = { visible.value = false }, viewModel = vm)
                }
            }
        }
        compose.onNodeWithText("启动服务").performClick()
        reveal(hasText("去设置") and hasClickAction()).assertIsDisplayed()
        assertEquals(1, requests)
        assertNull(started)
        pressBack()
        compose.waitUntil(10_000) { !visible.value }
        compose.runOnIdle { visible.value = true }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val monitor = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                if (intent.action != Settings.ACTION_APPLICATION_DETAILS_SETTINGS) return null
                launchedIntent = intent
                return Instrumentation.ActivityResult(Activity.RESULT_OK, null)
            }
        }
        instrumentation.addMonitor(monitor)
        try {
            reveal(hasText("去设置") and hasClickAction()).performClick()
            assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, launchedIntent?.action)
            assertEquals("package:${context.packageName}", launchedIntent?.dataString)
        } finally {
            instrumentation.removeMonitor(monitor)
        }

        compose.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        granted = true
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.onNodeWithText("去设置").assertDoesNotExist()
        assertNull(started)
        reveal(hasText("启动服务") and hasClickAction()).performClick()
        assertEquals("接收", started)
        assertEquals(1, requests)
    }

    private fun reveal(matcher: SemanticsMatcher) = compose.onNode(matcher).also {
        compose.onNode(hasScrollAction()).performScrollToNode(matcher)
    }

    private fun assertClipboard(expected: String) {
        compose.runOnIdle {
            val clipboard = compose.activity.getSystemService(ClipboardManager::class.java)
            assertEquals(expected, clipboard.primaryClip?.getItemAt(0)?.text?.toString())
        }
    }

    private fun networkPermissionContext(
        base: Context,
        granted: () -> Boolean,
    ) = object : ContextWrapper(base) {
        override fun checkPermission(permission: String, pid: Int, uid: Int): Int =
            if (permission != Manifest.permission.ACCESS_LOCAL_NETWORK) super.checkPermission(permission, pid, uid)
            else if (granted()) PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED
    }

    private val longFeedback = object : AccessibilityManager {
        override fun calculateRecommendedTimeoutMillis(
            originalTimeoutMillis: Long,
            containsIcons: Boolean,
            containsText: Boolean,
            containsControls: Boolean,
        ): Long = maxOf(originalTimeoutMillis, 60_000L)
    }
}
