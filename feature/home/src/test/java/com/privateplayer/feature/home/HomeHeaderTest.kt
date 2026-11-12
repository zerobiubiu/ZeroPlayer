package com.privateplayer.feature.home

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 首页头部去重与面包屑（F-01 / F-24）：标题为当前文件夹名，面包屑只含祖先。
 */
class HomeHeaderTest {

    @Test
    fun `根目录标题为根标签且无面包屑`() {
        assertEquals("文件", homeTitle("", "文件"))
        assertEquals(listOf("文件" to ""), breadcrumbSegments("", "文件"))
    }

    @Test
    fun `一级目录标题为文件夹名且面包屑只含根`() {
        assertEquals("旅行", homeTitle("旅行", "文件"))
        assertEquals(listOf("文件" to ""), breadcrumbSegments("旅行", "文件"))
    }

    @Test
    fun `多级目录标题为末段且面包屑含根与祖先不含当前`() {
        assertEquals("2026", homeTitle("旅行/2026", "文件"))
        assertEquals(
            listOf("文件" to "", "旅行" to "旅行"),
            breadcrumbSegments("旅行/2026", "文件"),
        )
        assertEquals(
            listOf("文件" to "", "旅行" to "旅行", "2026" to "旅行/2026"),
            breadcrumbSegments("旅行/2026/夏", "文件"),
        )
    }

    @Test
    fun `标题与面包屑末段不重复`() {
        val path = "旅行/2026/夏"
        val title = homeTitle(path, "文件")
        val segments = breadcrumbSegments(path, "文件")
        // 面包屑不含当前文件夹名（标题），避免“文件/文件”或末段重复。
        assertEquals(false, segments.any { it.first == title })
    }
}
