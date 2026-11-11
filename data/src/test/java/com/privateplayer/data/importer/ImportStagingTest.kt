package com.privateplayer.data.importer

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ImportStagingTest {
    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun `只有完整标记与文件长度一致才允许脱离外部来源恢复`() {
        val file = File(temporary.root, "source.part").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        assertNull(readyImportSize(file))
        markImportReady(file, 3L)
        assertEquals(3L, readyImportSize(file))
        file.appendBytes(byteArrayOf(4))
        assertNull(readyImportSize(file))
    }

    @Test
    fun `空文件可完成暂存且移除标记后不能误认已完成`() {
        val file = File(temporary.root, "empty.part").apply { createNewFile() }
        markImportReady(file, 0L)
        assertEquals(0L, readyImportSize(file))
        removeImportReady(file)
        assertNull(readyImportSize(file))
    }

    @Test
    fun `损坏标记或暂存缺失不得视作完成`() {
        val file = File(temporary.root, "source.part").apply { writeText("test") }
        importReadyFile(file).writeText("不完整")
        assertNull(readyImportSize(file))
        markImportReady(file, 4L)
        file.delete()
        assertNull(readyImportSize(file))
    }

    @Test
    fun `重复写完整标记只留下最终标记且异常长度不会破坏旧标记`() {
        val file = File(temporary.root, "source.part").apply { writeText("data") }
        markImportReady(file, 4L)
        markImportReady(file, 4L)
        assertEquals(4L, readyImportSize(file))
        assertThrows(IllegalArgumentException::class.java) { markImportReady(file, 3L) }
        assertThrows(IllegalArgumentException::class.java) { markImportReady(file, -1L) }
        assertEquals(4L, readyImportSize(file))
        assertTrue(temporary.root.listFiles()!!.map { it.name }.toSet() == setOf("source.part", "source.part.ready"))
    }
}
