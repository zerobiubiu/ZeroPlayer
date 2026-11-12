package com.privateplayer.core.design.components

import com.privateplayer.domain.repository.FileTreeEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 文件操作菜单分组（F-22）：信息 → 传输 → 管理 → 危险，删除始终独立在最后。
 * 首页与详情页共用同一构建器，这里锁定分组顺序与子集规则。
 */
class FileActionMenuGroupingTest {

    private fun video() = FileTreeEntry(
        name = "影片.mp4", path = "影片.mp4", isDirectory = false, sizeBytes = 1, modifiedAt = 0,
        mediaId = 1L, isVideo = true,
    )

    private fun plainFile() = FileTreeEntry(
        name = "说明.txt", path = "说明.txt", isDirectory = false, sizeBytes = 1, modifiedAt = 0,
    )

    private fun directory() = FileTreeEntry(
        name = "目录", path = "目录", isDirectory = true, sizeBytes = 0, modifiedAt = 0,
    )

    private val allActions = FileAction.entries.toSet()

    @Test
    fun `视频完整菜单按信息传输管理危险分组且删除独立最后`() {
        val groups = fileActionMenuGroups(video(), allActions)
        assertEquals(
            listOf(
                listOf(FileAction.Details, FileAction.FileInfo),
                listOf(FileAction.Export, FileAction.Share),
                listOf(FileAction.Rename, FileAction.Move),
                listOf(FileAction.Delete),
            ),
            groups.map { it.actions },
        )
    }

    @Test
    fun `首页视频行子集不含文件信息`() {
        val available = allActions - FileAction.FileInfo
        val groups = fileActionMenuGroups(video(), available)
        assertEquals(listOf(FileAction.Details), groups.first().actions)
        assertEquals(listOf(FileAction.Delete), groups.last().actions)
        assertFalse(groups.any { FileAction.FileInfo in it.actions })
    }

    @Test
    fun `详情页子集不含视频详情`() {
        val available = setOf(FileAction.Export, FileAction.Share, FileAction.Rename, FileAction.Move, FileAction.Delete)
        val groups = fileActionMenuGroups(video(), available)
        assertEquals(
            listOf(
                listOf(FileAction.Export, FileAction.Share),
                listOf(FileAction.Rename, FileAction.Move),
                listOf(FileAction.Delete),
            ),
            groups.map { it.actions },
        )
    }

    @Test
    fun `非视频文件只有文件信息传输管理危险`() {
        val groups = fileActionMenuGroups(plainFile(), allActions)
        assertEquals(
            listOf(
                listOf(FileAction.FileInfo),
                listOf(FileAction.Export, FileAction.Share),
                listOf(FileAction.Rename, FileAction.Move),
                listOf(FileAction.Delete),
            ),
            groups.map { it.actions },
        )
    }

    @Test
    fun `目录没有信息与传输组`() {
        val groups = fileActionMenuGroups(directory(), allActions)
        assertEquals(
            listOf(
                listOf(FileAction.Rename, FileAction.Move),
                listOf(FileAction.Delete),
            ),
            groups.map { it.actions },
        )
    }

    @Test
    fun `空可用集产生空菜单`() {
        assertTrue(fileActionMenuGroups(video(), emptySet()).isEmpty())
    }

    @Test
    fun `删除永远在最后一组且单独成组`() {
        for (entry in listOf(video(), plainFile(), directory())) {
            val groups = fileActionMenuGroups(entry, allActions)
            assertEquals(listOf(FileAction.Delete), groups.last().actions)
        }
    }
}

/** 名称校验（F-21 重命名复用）：按原因分类，文案由资源提供，不出现“空字符”术语。 */
class NameValidationTest {

    @Test
    fun `合法名称通过校验`() {
        assertNull(nameErrorReason("旅行.mp4"))
        assertNull(nameErrorReason("新 名称"))
        assertTrue(canConfirmName("旅行.mp4", "旧文件.mp4"))
    }

    @Test
    fun `空名称与点路径被拒绝`() {
        assertEquals(NameErrorReason.Blank, nameErrorReason(""))
        assertEquals(NameErrorReason.Blank, nameErrorReason("   "))
        assertEquals(NameErrorReason.DotPath, nameErrorReason("."))
        assertEquals(NameErrorReason.DotPath, nameErrorReason(".."))
    }

    @Test
    fun `非法字符归为同一原因`() {
        assertEquals(NameErrorReason.InvalidCharacter, nameErrorReason("a/b"))
        assertEquals(NameErrorReason.InvalidCharacter, nameErrorReason("a\\b"))
        assertEquals(NameErrorReason.InvalidCharacter, nameErrorReason("a\u0000b"))
        assertNull(nameErrorReason("正常 名称.mp4"))
    }

    @Test
    fun `与原名相同不允许确认`() {
        assertFalse(canConfirmName("同名.mp4", "同名.mp4"))
        assertTrue(canConfirmName("同名.mp4", null))
        assertFalse(canConfirmName("..", null))
    }
}
