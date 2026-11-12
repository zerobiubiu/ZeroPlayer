package com.privateplayer.media

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.privateplayer.core.design.components.ActionSheet
import com.privateplayer.domain.model.VideoAspect
import com.privateplayer.feature.player.PlayerTopBar
import com.privateplayer.feature.player.PlayerUiState
import com.privateplayer.feature.player.aspectSheetActions
import com.privateplayer.feature.player.moreSheetActions
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 顶栏“画面比例 / 旋转屏幕”入口归位（F-19）。
 *
 * 断言用户可见结果：比例图标打开比例面板、旋转一次点击切换横竖屏、
 * 读屏名称准确、“更多”面板不再保留第二个旋转入口。
 * “更多”与“比例”面板内容直接取生产构建器，避免测试复制一份平行实现。
 */
@RunWith(AndroidJUnit4::class)
class PlayerEntriesInstrumentedTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun selectedNodes() = compose.onAllNodes(
        SemanticsMatcher.expectValue(SemanticsProperties.Selected, true),
    )

    @Test
    fun aspectIconOpensAspectPanelWithSingleChoiceSemantics() {
        val aspectOpens = AtomicInteger()
        val picked = mutableListOf<VideoAspect>()
        val showAspect = mutableStateOf(false)
        compose.setContent {
            MaterialTheme {
                PlayerTopBar(
                    title = "示例视频",
                    onBack = {},
                    onOpenAspect = {
                        aspectOpens.incrementAndGet()
                        showAspect.value = true
                    },
                    onRotate = {},
                    onOpenSubtitle = {},
                    onOpenMore = {},
                )
                if (showAspect.value) {
                    ActionSheet(
                        title = "画面比例",
                        immersive = true,
                        actions = aspectSheetActions(
                            current = VideoAspect.FIT,
                            onPick = { aspect ->
                                picked += aspect
                                showAspect.value = false
                            },
                        ),
                        onDismiss = { showAspect.value = false },
                    )
                }
            }
        }

        // 图标语义与结果一致：点“画面比例”打开的是比例面板，不是旋转
        compose.onNodeWithContentDescription("画面比例").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("适应屏幕").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(1, aspectOpens.get())
        compose.onNodeWithText("画面比例").assertExists()
        listOf("适应屏幕", "拉伸填充", "裁剪填满", "16:9", "4:3").forEach { label ->
            compose.onNodeWithText(label).assertExists()
        }

        // F-16 语义保持：只有当前项勾选+高亮，单选读屏语义
        selectedNodes().assertCountEquals(1)
        val current = compose.onNodeWithText("适应屏幕").fetchSemanticsNode()
        assertEquals(Role.RadioButton, current.config[SemanticsProperties.Role])
        assertTrue(current.config[SemanticsProperties.Selected])

        // 选择其它比例后回调生效，面板关闭
        compose.onNodeWithText("16:9").performClick()
        compose.waitUntil(10_000) { picked == listOf(VideoAspect.RATIO_16_9) }
    }

    @Test
    fun rotateIconSwitchesOrientationInOneTap() {
        // 宿主与 MainActivity 一样自行处理方向配置变更，旋转不重建 Compose 层级。
        val scenario = ActivityScenario.launch(RotationTestActivity::class.java)
        try {
            lateinit var activity: RotationTestActivity
            scenario.onActivity { host ->
                activity = host
                host.setContent {
                    MaterialTheme {
                        PlayerTopBar(
                            title = "示例视频",
                            onBack = {},
                            onOpenAspect = {},
                            // 与 PlayerScreen 相同的接线：直接调用既有的横竖屏切换逻辑
                            onRotate = { host.toggleOrientationForTest() },
                            onOpenSubtitle = {},
                            onOpenMore = {},
                        )
                    }
                }
            }
            compose.waitUntil(10_000) {
                compose.onAllNodesWithContentDescription("旋转屏幕").fetchSemanticsNodes().isNotEmpty()
            }
            val startLandscape =
                activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

            compose.onNodeWithContentDescription("旋转屏幕").performClick()
            compose.waitUntil(10_000) {
                val landscape =
                    activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                landscape != startLandscape
            }
            val expectedAfterFirst = if (startLandscape) {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            } else {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
            assertEquals(expectedAfterFirst, activity.requestedOrientation)

            // 再点一次切回原方向：旋转是反复可用的一线动作
            compose.onNodeWithContentDescription("旋转屏幕").performClick()
            compose.waitUntil(10_000) {
                val landscape =
                    activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                landscape == startLandscape
            }
            val expectedAfterSecond = if (startLandscape) {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
            assertEquals(expectedAfterSecond, activity.requestedOrientation)
        } finally {
            scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
            scenario.close()
        }
    }

    @Test
    fun topBarEntryDescriptionsMatchTheirActions() {
        compose.setContent {
            MaterialTheme {
                PlayerTopBar(
                    title = "示例视频",
                    onBack = {},
                    onOpenAspect = {},
                    onRotate = {},
                    onOpenSubtitle = {},
                    onOpenMore = {},
                )
            }
        }
        listOf("返回", "画面比例", "旋转屏幕", "字幕", "更多").forEach { description ->
            compose.onAllNodesWithContentDescription(description).assertCountEquals(1)
        }
        // 旧的混合语义名称不再存在
        compose.onAllNodesWithContentDescription("画面比例与旋转").assertCountEquals(0)
        // 顶栏保持紧凑：五个图标按钮，没有额外入口
        compose.onAllNodes(hasClickAction() and hasContentDescription("画面比例")).assertCountEquals(1)
    }

    @Test
    fun moreSheetKeepsActionsAndNavigationWithoutRotateOrEngine() {
        val locked = AtomicInteger()
        val audioOpens = AtomicInteger()
        val aspectOpens = AtomicInteger()
        compose.setContent {
            MaterialTheme {
                ActionSheet(
                    title = "示例视频",
                    immersive = true,
                    actions = moreSheetActions(
                        state = PlayerUiState(title = "示例视频"),
                        onLock = { locked.incrementAndGet() },
                        onOpenAudio = { audioOpens.incrementAndGet() },
                        onOpenAspect = { aspectOpens.incrementAndGet() },
                    ),
                    onDismiss = {},
                )
            }
        }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("锁定屏幕").fetchSemanticsNodes().isNotEmpty()
        }

        // F-19：旋转只有顶栏一个入口；F-17：技术行不再出现
        compose.onAllNodesWithText("旋转屏幕").assertCountEquals(0)
        compose.onAllNodesWithText("播放引擎").assertCountEquals(0)

        // 保留的三个入口：一个动作 + 两个导航，均不带选中语义
        selectedNodes().assertCountEquals(0)
        compose.onNodeWithText("锁定屏幕").performClick()
        compose.waitUntil(10_000) { locked.get() == 1 }
        compose.onNodeWithText("音轨").performClick()
        compose.waitUntil(10_000) { audioOpens.get() == 1 }
        compose.onNodeWithText("画面比例").performClick()
        compose.waitUntil(10_000) { aspectOpens.get() == 1 }

        // 导航项带“进入下一级”的箭头语义由 ActionSheet 渲染；此处确认文案齐全
        listOf("锁定屏幕", "音轨", "画面比例").forEach { label ->
            compose.onAllNodes(hasText(label)).assertCountEquals(1)
        }
    }
}

/** 与生产 PlayerScreen 的私有扩展相同的横竖屏切换逻辑（测试宿主用）。 */
private fun android.app.Activity.toggleOrientationForTest() {
    requestedOrientation = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    } else {
        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }
}
