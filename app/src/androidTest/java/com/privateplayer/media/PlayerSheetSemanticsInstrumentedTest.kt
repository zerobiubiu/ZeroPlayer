package com.privateplayer.media

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.privateplayer.core.design.components.ActionSheet
import com.privateplayer.core.design.components.SheetAction
import com.privateplayer.core.design.components.SheetActionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 播放器面板的选中语义与开关行为（F-16 / F-17 / F-38）。
 *
 * 直接渲染共享的 [ActionSheet]，断言的是用户可见结果与读屏语义，
 * 而不是实现细节。
 */
@RunWith(AndroidJUnit4::class)
class PlayerSheetSemanticsInstrumentedTest {

    @get:Rule
    val compose = createComposeRule()

    /** 勾选图标在面板里只作为装饰，用 contentDescription 无法定位，这里按语义节点统计。 */
    private fun selectedNodes() = compose.onAllNodes(
        SemanticsMatcher.expectValue(SemanticsProperties.Selected, true),
    )

    @Test
    fun singleChoiceSheetMarksOnlyCurrentOption() {
        val speeds = listOf("0.5×", "1.0×", "1.5×", "2.0×")
        compose.setContent {
            MaterialTheme {
                ActionSheet(
                    title = "播放速度",
                    actions = speeds.map { speed ->
                        SheetAction(
                            label = speed,
                            icon = Icons.Outlined.Speed,
                            kind = SheetActionKind.SingleChoice,
                            selected = speed == "1.5×",
                            onClick = {},
                        )
                    },
                    onDismiss = {},
                )
            }
        }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("播放速度").fetchSemanticsNodes().isNotEmpty()
        }

        // 只有一个条目处于选中态，其余都不是
        selectedNodes().assertCountEquals(1)
        compose.onNodeWithText("1.5×").assertIsDisplayed()

        // 选中项带单选语义，读屏可播报“已选中”
        val selectedNode = compose.onNodeWithText("1.5×").fetchSemanticsNode()
        assertEquals(Role.RadioButton, selectedNode.config[SemanticsProperties.Role])
        assertTrue(selectedNode.config[SemanticsProperties.Selected])

        // 未选中项同样带单选语义，但状态为未选中
        val unselected = compose.onNodeWithText("0.5×").fetchSemanticsNode()
        assertEquals(Role.RadioButton, unselected.config[SemanticsProperties.Role])
        assertEquals(false, unselected.config[SemanticsProperties.Selected])
    }

    @Test
    fun actionAndNavigationRowsCarryNoSelectionState() {
        compose.setContent {
            MaterialTheme {
                ActionSheet(
                    title = "更多",
                    actions = listOf(
                        SheetAction(
                            label = "锁定屏幕",
                            icon = Icons.Outlined.Lock,
                            kind = SheetActionKind.Action,
                            onClick = {},
                        ),
                        SheetAction(
                            label = "音轨",
                            icon = Icons.Outlined.VolumeUp,
                            kind = SheetActionKind.Navigation,
                            supportingText = "音轨 1",
                            onClick = {},
                        ),
                        SheetAction(
                            label = "画面比例",
                            icon = Icons.Outlined.AspectRatio,
                            kind = SheetActionKind.Navigation,
                            supportingText = "适应屏幕",
                            onClick = {},
                        ),
                    ),
                    onDismiss = {},
                )
            }
        }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("更多").fetchSemanticsNodes().isNotEmpty()
        }

        // 动作项与导航项都不带选中态，不再暗示“可勾选”
        selectedNodes().assertCountEquals(0)
        listOf("锁定屏幕", "音轨", "画面比例").forEach { label ->
            val node = compose.onNodeWithText(label).fetchSemanticsNode()
            assertTrue(
                "$label 不应带选中语义",
                !node.config.contains(SemanticsProperties.Selected),
            )
        }
    }

    @Test
    fun statusRowIsNotClickable() {
        compose.setContent {
            MaterialTheme {
                ActionSheet(
                    title = "音轨",
                    actions = listOf(
                        SheetAction(
                            label = "没有可选音轨",
                            icon = Icons.Outlined.VolumeUp,
                            kind = SheetActionKind.Status,
                            enabled = false,
                            onClick = {},
                        ),
                    ),
                    onDismiss = {},
                )
            }
        }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("没有可选音轨").fetchSemanticsNodes().isNotEmpty()
        }

        // 状态项只陈述事实，不注册点击语义
        compose.onAllNodes(hasText("没有可选音轨") and hasClickAction()).assertCountEquals(0)
        selectedNodes().assertCountEquals(0)
    }

    @Test
    fun moreSheetHasNoPlaybackEngineRow() {
        compose.setContent {
            MaterialTheme {
                ActionSheet(
                    title = "示例视频",
                    actions = listOf(
                        SheetAction("锁定屏幕", Icons.Outlined.Lock, {}, kind = SheetActionKind.Action),
                        SheetAction("音轨", Icons.Outlined.VolumeUp, {}, kind = SheetActionKind.Navigation),
                        SheetAction("画面比例", Icons.Outlined.AspectRatio, {}, kind = SheetActionKind.Navigation),
                        SheetAction("旋转屏幕", Icons.Outlined.AspectRatio, {}, kind = SheetActionKind.Action),
                    ),
                    onDismiss = {},
                )
            }
        }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("锁定屏幕").fetchSemanticsNodes().isNotEmpty()
        }

        // F-17：技术诊断不再占据面板；引擎信息在设置·关于中查看
        compose.onAllNodesWithText("播放引擎", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("libmpv", substring = true).assertCountEquals(0)
    }

    @Test
    fun selectingOptionInvokesCallbackAndSheetCanBeReopened() {
        val picked = mutableListOf<String>()
        val visible = mutableStateOf(true)
        compose.setContent {
            MaterialTheme {
                if (visible.value) {
                    ActionSheet(
                        title = "播放速度",
                        actions = listOf("1.0×", "2.0×").map { speed ->
                            SheetAction(
                                label = speed,
                                icon = Icons.Outlined.Speed,
                                kind = SheetActionKind.SingleChoice,
                                selected = speed == "1.0×",
                                onClick = {
                                    picked += speed
                                    visible.value = false
                                },
                            )
                        },
                        onDismiss = { visible.value = false },
                    )
                }
            }
        }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("2.0×").fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithText("2.0×").performClick()
        compose.waitUntil(10_000) { picked == listOf("2.0×") }
        compose.waitForIdle()

        // 关闭后重新打开，选中态跟随新的当前值
        compose.runOnIdle { visible.value = true }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("播放速度").fetchSemanticsNodes().isNotEmpty()
        }
        selectedNodes().assertCountEquals(1)
    }

    @Test
    fun switchingBetweenSheetsKeepsExactlyOneSelectedOption() {
        val sheet = mutableStateOf("speed")
        compose.setContent {
            MaterialTheme {
                when (sheet.value) {
                    "speed" -> ActionSheet(
                        title = "播放速度",
                        actions = listOf("1.0×", "2.0×").map { speed ->
                            SheetAction(
                                label = speed,
                                icon = Icons.Outlined.Speed,
                                kind = SheetActionKind.SingleChoice,
                                selected = speed == "1.0×",
                                onClick = {},
                            )
                        },
                        onDismiss = {},
                    )

                    else -> ActionSheet(
                        title = "画面比例",
                        actions = listOf("适应屏幕", "16:9").map { label ->
                            SheetAction(
                                label = label,
                                icon = Icons.Outlined.AspectRatio,
                                kind = SheetActionKind.SingleChoice,
                                selected = label == "16:9",
                                onClick = {},
                            )
                        },
                        onDismiss = {},
                    )
                }
            }
        }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("播放速度").fetchSemanticsNodes().isNotEmpty()
        }
        selectedNodes().assertCountEquals(1)

        compose.runOnIdle { sheet.value = "aspect" }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("画面比例").fetchSemanticsNodes().isNotEmpty()
        }
        // 面板间切换后仍然只有一个选中项，不会残留上一个面板的勾选
        selectedNodes().assertCountEquals(1)
        compose.onAllNodesWithText("播放速度").assertCountEquals(0)
    }
}
