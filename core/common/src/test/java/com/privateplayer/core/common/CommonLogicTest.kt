package com.privateplayer.core.common

import com.privateplayer.core.common.files.baseNameOf
import com.privateplayer.core.common.files.extensionOf
import com.privateplayer.core.common.files.sanitizeFileName
import com.privateplayer.core.common.files.uniqueFileName
import com.privateplayer.core.common.files.withSameExtension
import com.privateplayer.core.common.format.formatBytes
import com.privateplayer.core.common.format.formatDuration
import com.privateplayer.core.common.format.formatRemaining
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 纯函数单元测试：文件名策略与格式化。
 *
 * 这些逻辑同时被导入（落盘命名）与 UI（展示）使用，
 * 一旦回归会造成文件覆盖或显示错乱，因此用测试固定行为。
 */
class FileNameRulesTest {

    @Test
    fun `sanitize removes path separators`() {
        assertEquals("a_b_c", sanitizeFileName("a/b\\c"))
        assertEquals("未命名", sanitizeFileName("   "))
        assertEquals("movie", sanitizeFileName("movie."))
    }

    @Test
    fun `extension is lowercased`() {
        assertEquals("mp4", extensionOf("Movie.MP4"))
        assertNull(extensionOf("noext"))
        assertNull(extensionOf(".hidden"))
    }

    @Test
    fun `base name strips only last extension`() {
        assertEquals("a.b", baseNameOf("a.b.mkv"))
        assertEquals("plain", baseNameOf("plain"))
    }

    @Test
    fun `unique name appends index and keeps extension`() {
        val existing = setOf("movie.mp4", "movie (1).mp4")
        assertEquals("movie (2).mp4", uniqueFileName("movie.mp4", existing))
        assertEquals("other.mkv", uniqueFileName("other.mkv", existing))
    }

    @Test
    fun `unique name is case insensitive`() {
        assertEquals("Movie (1).MP4", uniqueFileName("Movie.MP4", setOf("movie.mp4")))
    }

    @Test
    fun `rename keeps original extension`() {
        assertEquals("新名字.mkv", withSameExtension("新名字", "old.mkv"))
        assertEquals("新名字", withSameExtension("新名字", "noext"))
    }
}

class FormattersTest {

    @Test
    fun `bytes uses binary units`() {
        assertEquals("0 B", formatBytes(0))
        assertEquals("512 B", formatBytes(512))
        assertEquals("1023 B", formatBytes(1023))
        assertEquals("1.0 KiB", formatBytes(1024))
        assertEquals("1.5 MiB", formatBytes(1536L * 1024))
        assertEquals("2.0 GiB", formatBytes(2L * 1024 * 1024 * 1024))
        assertEquals("1.0 TiB", formatBytes(1024L * 1024 * 1024 * 1024))
    }

    @Test
    fun `duration formats hours and minutes`() {
        assertEquals("--:--", formatDuration(0))
        assertEquals("00:05", formatDuration(5_000))
        assertEquals("02:03", formatDuration(123_000))
        assertEquals("1:02:03", formatDuration(3_723_000))
    }

    @Test
    fun `relative time buckets`() {
        val now = 1_800_000_000_000L
        assertEquals("刚刚", com.privateplayer.core.common.format.formatRelativeTime(now - 30_000, now))
        assertEquals("5 分钟前", com.privateplayer.core.common.format.formatRelativeTime(now - 5 * 60_000, now))
        assertEquals("3 小时前", com.privateplayer.core.common.format.formatRelativeTime(now - 3 * 3_600_000, now))
        assertEquals("2 天前", com.privateplayer.core.common.format.formatRelativeTime(now - 2 * 86_400_000, now))
    }

    @Test
    fun `relative time falls back to date for old values`() {
        val now = 1_800_000_000_000L
        val old = now - 30L * 86_400_000
        assertEquals(
            com.privateplayer.core.common.format.formatDateTime(old),
            com.privateplayer.core.common.format.formatRelativeTime(old, now),
        )
    }

    @Test
    fun `remaining clamps at zero`() {
        assertEquals("00:30", formatRemaining(30_000, 60_000))
        assertEquals("00:00", formatRemaining(70_000, 60_000))
    }
}
