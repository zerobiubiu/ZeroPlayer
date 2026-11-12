package com.privateplayer.core.design.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 面板条目的选中语义。
 *
 * F-16 的根因是所有条目都传勾选图标，导致“全部已选中”的错觉。
 * 这里锁定“只有单选项且被选中才显示勾选”的规则。
 */
class SheetActionSelectionTest {

    private fun action(
        kind: SheetActionKind,
        selected: Boolean = false,
    ) = SheetAction(
        label = "条目",
        icon = Icons.Outlined.Check,
        onClick = {},
        kind = kind,
        selected = selected,
    )

    @Test
    fun `单选项仅在选中时显示勾选标记`() {
        assertTrue(action(SheetActionKind.SingleChoice, selected = true).showsSelectionMark)
        assertFalse(action(SheetActionKind.SingleChoice, selected = false).showsSelectionMark)
    }

    @Test
    fun `动作项即使传入 selected 也不显示勾选标记`() {
        assertFalse(action(SheetActionKind.Action, selected = true).showsSelectionMark)
        assertFalse(action(SheetActionKind.Action, selected = false).showsSelectionMark)
    }

    @Test
    fun `导航项不显示勾选标记`() {
        assertFalse(action(SheetActionKind.Navigation, selected = true).showsSelectionMark)
    }

    @Test
    fun `状态项不显示勾选标记`() {
        assertFalse(action(SheetActionKind.Status, selected = true).showsSelectionMark)
    }

    @Test
    fun `默认类型是动作项`() {
        val plain = SheetAction(label = "旋转屏幕", icon = Icons.Outlined.Check, onClick = {})
        assertEquals(SheetActionKind.Action, plain.kind)
        assertFalse(plain.showsSelectionMark)
    }

    @Test
    fun `单选组内最多一个条目显示勾选标记`() {
        val speeds = listOf(0.5f, 1.0f, 1.5f, 2.0f)
        val current = 1.5f
        val actions = speeds.map { speed ->
            action(SheetActionKind.SingleChoice, selected = speed == current)
        }
        assertEquals(1, actions.count { it.showsSelectionMark })
    }

    @Test
    fun `没有匹配当前值时单选组不显示任何勾选标记`() {
        val actions = listOf(0.5f, 1.0f, 2.0f).map { speed ->
            action(SheetActionKind.SingleChoice, selected = speed == 1.25f)
        }
        assertEquals(0, actions.count { it.showsSelectionMark })
    }

    @Test
    fun `混合面板中只有被选中的单选项带勾选标记`() {
        val actions = listOf(
            action(SheetActionKind.Action),
            action(SheetActionKind.Navigation),
            action(SheetActionKind.Status),
            action(SheetActionKind.SingleChoice, selected = false),
            action(SheetActionKind.SingleChoice, selected = true),
            action(SheetActionKind.SingleChoice, selected = false),
        )
        assertEquals(1, actions.count { it.showsSelectionMark })
    }
}
