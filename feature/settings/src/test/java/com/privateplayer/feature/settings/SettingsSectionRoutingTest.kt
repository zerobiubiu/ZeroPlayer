package com.privateplayer.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 入口按用途分组；收藏的旧链接仍能到达负责该选项的页面。 */
class SettingsSectionRoutingTest {

    @Test
    fun `观看文件应用各自只包含相关入口`() {
        assertEquals(
            listOf(listOf("playback", "subtitle", "gesture"), listOf("import", "storage"), listOf("appearance", "about")),
            SettingsSection.groups.map { (_, sections) -> sections.map { it.id } },
        )
    }

    @Test
    fun `旧路由重定向到新分组`() {
        assertEquals(SettingsSection.APPEARANCE, SettingsSection.fromId("display"))
        assertEquals(SettingsSection.IMPORT, SettingsSection.fromId("import"))
        assertEquals(SettingsSection.STORAGE, SettingsSection.fromId("storage"))
        assertEquals(SettingsSection.SUBTITLE, SettingsSection.fromId("subtitle"))
    }

    @Test
    fun `新路由与未知路由解析正确`() {
        assertEquals(SettingsSection.PLAYBACK, SettingsSection.fromId("playback"))
        assertEquals(SettingsSection.GESTURE, SettingsSection.fromId("gesture"))
        assertEquals(SettingsSection.APPEARANCE, SettingsSection.fromId("appearance"))
        assertEquals(SettingsSection.STORAGE, SettingsSection.fromId("data"))
        assertEquals(SettingsSection.ABOUT, SettingsSection.fromId("about"))
        assertNull(SettingsSection.fromId("unknown"))
        assertNull(SettingsSection.fromId(null))
    }
}
