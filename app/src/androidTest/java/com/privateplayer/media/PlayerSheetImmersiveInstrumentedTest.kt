package com.privateplayer.media

import android.os.ParcelFileDescriptor
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.privateplayer.core.design.components.ActionSheet
import com.privateplayer.core.design.components.SheetAction
import com.privateplayer.core.design.components.SheetActionKind
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 播放器面板的沉浸式行为（F-38）。
 *
 * 面板是独立的对话框窗口，Activity 窗口隐藏系统栏对它无效——这正是
 * “打开面板后电池图标压在画面上”的根因。
 *
 * 断言的是用户可见结果：系统状态栏窗口是否真的可见，通过 `dumpsys window`
 * 读取，而不是实现细节。
 */
@RunWith(AndroidJUnit4::class)
class PlayerSheetImmersiveInstrumentedTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    /** 模拟播放器的沉浸式策略：Activity 边到边并隐藏系统栏。 */
    private fun hideActivityBars() {
        val window: Window = compose.activity.window
        compose.runOnIdle {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowCompat.getInsetsController(window, window.decorView).apply {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    private fun showActivityBars() {
        val window: Window = compose.activity.window
        compose.runOnIdle {
            WindowCompat.getInsetsController(window, window.decorView)
                .show(WindowInsetsCompat.Type.systemBars())
            WindowCompat.setDecorFitsSystemWindows(window, true)
        }
    }

    private fun shell(command: String): String {
        val descriptor = InstrumentationRegistry.getInstrumentation()
            .uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor)
            .bufferedReader().use { it.readText() }
    }

    /** 系统状态栏窗口当前是否可见。 */
    private fun statusBarVisible(): Boolean {
        var inStatusBarWindow = false
        shell("dumpsys window windows").lines().forEach { line ->
            if (line.contains("Window #") && line.contains("Window{")) {
                inStatusBarWindow = line.contains(" u0 StatusBar}")
            } else if (inStatusBarWindow && line.trim().startsWith("isVisible=")) {
                return line.trim().removePrefix("isVisible=").trim() == "true"
            }
        }
        return true
    }

    private fun showSheet(immersive: Boolean, visible: MutableState<Boolean>) {
        compose.setContent {
            MaterialTheme {
                if (visible.value) {
                    ActionSheet(
                        title = "播放速度",
                        immersive = immersive,
                        actions = listOf(
                            SheetAction(
                                label = "1.0×",
                                icon = Icons.Outlined.Speed,
                                kind = SheetActionKind.SingleChoice,
                                selected = true,
                                onClick = {},
                            ),
                        ),
                        onDismiss = { visible.value = false },
                    )
                }
            }
        }
    }

    private fun waitForSheet() {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("播放速度").fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()
    }

    @Test
    fun immersiveSheetKeepsStatusBarHiddenWhileActivityIsImmersive() {
        hideActivityBars()
        compose.waitUntil(10_000) { !statusBarVisible() }

        val visible = mutableStateOf(true)
        showSheet(immersive = true, visible = visible)
        waitForSheet()

        // 面板打开后状态栏仍隐藏：电池/时间不会压在画面上
        compose.waitUntil(10_000) { !statusBarVisible() }
        assertFalse("沉浸式面板打开后不应出现状态栏", statusBarVisible())

        // 关闭面板后仍保持沉浸式
        compose.runOnIdle { visible.value = false }
        compose.waitForIdle()
        compose.waitUntil(10_000) { !statusBarVisible() }
        assertFalse("关闭面板后仍应保持沉浸式", statusBarVisible())

        showActivityBars()
    }

    @Test
    fun nonImmersiveSheetDoesNotHideSystemBars() {
        val visible = mutableStateOf(true)
        showSheet(immersive = false, visible = visible)
        waitForSheet()

        // 普通页面的面板保持系统默认行为，不被播放器策略波及
        compose.waitUntil(10_000) { statusBarVisible() }
        assertTrue("非沉浸式面板应保留状态栏", statusBarVisible())

        compose.runOnIdle { visible.value = false }
        compose.waitForIdle()
    }

    @Test
    fun reopeningImmersiveSheetStaysImmersive() {
        hideActivityBars()
        compose.waitUntil(10_000) { !statusBarVisible() }

        val visible = mutableStateOf(true)
        showSheet(immersive = true, visible = visible)
        waitForSheet()
        compose.waitUntil(10_000) { !statusBarVisible() }

        // 关闭再打开：新的面板窗口同样保持沉浸式
        compose.runOnIdle { visible.value = false }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("播放速度").fetchSemanticsNodes().isEmpty()
        }
        compose.runOnIdle { visible.value = true }
        waitForSheet()
        compose.waitUntil(10_000) { !statusBarVisible() }
        assertFalse("重开后的面板仍不应显示状态栏", statusBarVisible())

        compose.runOnIdle { visible.value = false }
        compose.waitForIdle()
        showActivityBars()
    }

    @Test
    fun leavingPlayerRestoresSystemBars() {
        hideActivityBars()
        compose.waitUntil(10_000) { !statusBarVisible() }

        val visible = mutableStateOf(true)
        showSheet(immersive = true, visible = visible)
        waitForSheet()
        compose.waitUntil(10_000) { !statusBarVisible() }

        // 模拟退出播放页：面板销毁 + Activity 恢复系统栏
        compose.runOnIdle { visible.value = false }
        compose.waitForIdle()
        showActivityBars()

        compose.waitUntil(10_000) { statusBarVisible() }
        assertTrue("退出播放页后普通页面应恢复状态栏", statusBarVisible())
    }
}
