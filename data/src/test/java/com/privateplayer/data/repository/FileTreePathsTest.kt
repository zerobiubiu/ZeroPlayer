package com.privateplayer.data.repository

import com.privateplayer.core.storage.moveNoOverwrite
import com.privateplayer.core.storage.resolveFileInMediaRoot
import java.io.File
import java.io.IOException
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNoException
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileTreePathsTest {
    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun `路径保持真实名称且根目录用空字符串`() {
        val root = temporary.newFolder("media")
        val folder = File(root, "电影 %")
        assertTrue(folder.mkdir())
        val file = File(folder, "第 1 集.mkv")
        assertTrue(file.createNewFile())

        assertEquals(root.canonicalFile, resolveFileInMediaRoot(root, ""))
        assertEquals(file.canonicalFile, resolveFileInMediaRoot(root, "电影 %/第 1 集.mkv"))
        assertEquals(File(folder, "未来.srt").canonicalFile, resolveFileInMediaRoot(root, "电影 %/未来.srt"))
    }

    @Test
    fun `拒绝绝对路径点路径空分量和反斜杠`() {
        val root = temporary.newFolder("media")
        listOf("/etc/passwd", "C:/outside", "../outside", "a/../outside", "./video", "a//b", "a/", "a\\b", "x\u0000y")
            .forEach { path ->
                assertThrows("应拒绝：$path", IllegalArgumentException::class.java) {
                    resolveFileInMediaRoot(root, path)
                }
            }
    }

    @Test
    fun `拒绝符号链接目录文件和根目录`() {
        val root = temporary.newFolder("media")
        val outside = temporary.newFolder("outside")
        val outsideFile = File(outside, "秘密.mp4")
        assertTrue(outsideFile.createNewFile())
        val directoryLink = File(root, "目录链接")
        val fileLink = File(root, "文件链接.mp4")
        val rootLink = File(root.parentFile, "媒体链接")
        try {
            Files.createSymbolicLink(directoryLink.toPath(), outside.toPath())
            Files.createSymbolicLink(fileLink.toPath(), outsideFile.toPath())
            Files.createSymbolicLink(rootLink.toPath(), root.toPath())
        } catch (error: Exception) {
            assumeNoException("当前文件系统不支持创建符号链接", error)
        }
        assertThrows(IllegalArgumentException::class.java) { resolveFileInMediaRoot(root, "目录链接/秘密.mp4") }
        assertThrows(IllegalArgumentException::class.java) { resolveFileInMediaRoot(root, "文件链接.mp4") }
        assertThrows(IllegalArgumentException::class.java) { resolveFileInMediaRoot(rootLink, "") }
        assertTrue(outsideFile.exists())
    }

    @Test
    fun `移动文件不覆盖同名目标`() {
        val source = temporary.newFile("源.mp4").apply { writeText("源文件") }
        val target = temporary.newFile("目标.mp4").apply { writeText("已有文件") }
        assertThrows(IOException::class.java) { moveNoOverwrite(source, target) }
        assertEquals("源文件", source.readText())
        assertEquals("已有文件", target.readText())
    }

    @Test
    fun `移动非空目录保留子目录和文件`() {
        val source = temporary.newFolder("源目录")
        val nested = File(source, "子目录").apply { assertTrue(mkdir()) }
        File(nested, "文件.txt").writeText("内容")
        val target = File(source.parentFile, "目标目录")
        moveNoOverwrite(source, target)
        assertFalse(source.exists())
        assertEquals("内容", File(target, "子目录/文件.txt").readText())
    }

    @Test
    fun `重命名普通文件保持真实身份和内容`() {
        val source = temporary.newFile("原始.part").apply { writeText("完整内容") }
        val identity = Files.readAttributes(source.toPath(), java.nio.file.attribute.BasicFileAttributes::class.java).fileKey()
        val target = File(source.parentFile, "新文件.bin")
        moveNoOverwrite(source, target)
        assertFalse(source.exists())
        assertEquals("完整内容", target.readText())
        assertEquals(identity, Files.readAttributes(target.toPath(), java.nio.file.attribute.BasicFileAttributes::class.java).fileKey())
    }

    @Test
    fun `清理非空目录不跟随其中的符号链接`() {
        val root = temporary.newFolder("清理目录")
        val outside = temporary.newFolder("树外目录")
        val outsideFile = File(outside, "保留.txt").apply { writeText("保留") }
        try {
            Files.createSymbolicLink(File(root, "树外链接").toPath(), outside.toPath())
        } catch (error: Exception) {
            assumeNoException("当前文件系统不支持创建符号链接", error)
        }
        deleteTree(root.toPath())
        assertFalse(root.exists())
        assertEquals("保留", outsideFile.readText())
    }

    @Test
    fun `字幕匹配同基名和语言后缀但不匹配其他影片`() {
        assertTrue(isMatchingSubtitle("电影.MKV", "电影.srt"))
        assertTrue(isMatchingSubtitle("电影.mkv", "电影.zh-Hans-CN.ASS"))
        assertTrue(isMatchingSubtitle("电影.mkv", "电影_en.ssa"))
        assertTrue(isMatchingSubtitle("电影.mkv", "电影.简体.vtt"))
        assertFalse(isMatchingSubtitle("电影.mkv", "电影续集.srt"))
        assertFalse(isMatchingSubtitle("电影.mkv", "电影.trailer.srt"))
        assertFalse(isMatchingSubtitle("电影.mkv", "电影.zh.txt"))
    }

    @Test
    fun `旧视频磁盘名与显示名可分别匹配字幕`() {
        val physical = "01234567-89ab-cdef-0123-456789abcdef.mp4"
        val display = "原始名称.mp4"
        assertTrue(isMatchingSubtitle(physical, "01234567-89ab-cdef-0123-456789abcdef.en.srt"))
        assertTrue(isMatchingSubtitle(display, "原始名称.zh-CN.srt"))
        assertFalse(isMatchingSubtitle(physical, "原始名称.zh-CN.srt"))
    }
}
