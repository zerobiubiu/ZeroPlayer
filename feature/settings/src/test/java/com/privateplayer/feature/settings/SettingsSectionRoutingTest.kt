package com.privateplayer.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 设置一级按任务分组（F-18）：五个入口，无单控件页面；旧路由兼容。
 */
class SettingsSectionRoutingTest {

    @Test
    fun `一级入口为五个任务分组`() {
        assertEquals(
            listOf("playback", "gesture", "appearance", "data", "about"),
            SettingsSection.entries.map { it.id },
        )
    }

    @Test
    fun `旧路由重定向到新分组`() {
        assertEquals(SettingsSection.APPEARANCE, SettingsSection.fromId("display"))
        assertEquals(SettingsSection.DATA, SettingsSection.fromId("import"))
        assertEquals(SettingsSection.DATA, SettingsSection.fromId("storage"))
        assertEquals(SettingsSection.PLAYBACK, SettingsSection.fromId("subtitle"))
    }

    @Test
    fun `新路由与未知路由解析正确`() {
        assertEquals(SettingsSection.PLAYBACK, SettingsSection.fromId("playback"))
        assertEquals(SettingsSection.GESTURE, SettingsSection.fromId("gesture"))
        assertEquals(SettingsSection.APPEARANCE, SettingsSection.fromId("appearance"))
        assertEquals(SettingsSection.DATA, SettingsSection.fromId("data"))
        assertEquals(SettingsSection.ABOUT, SettingsSection.fromId("about"))
        assertNull(SettingsSection.fromId("unknown"))
        assertNull(SettingsSection.fromId(null))
    }
}
