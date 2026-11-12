package com.privateplayer.media

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 导入失败原因分类（F-12）：原始错误映射为可行动文案，未知保留通用。
 */
class ImportErrorMappingTest {

    @Test
    fun `空间不足归类为 Space`() {
        assertEquals(ImportErrorKind.Space, classifyImportError("手机存储空间不足"))
        assertEquals(ImportErrorKind.Space, classifyImportError("ENOSPC: no space left"))
    }

    @Test
    fun `来源失效归类为 Source`() {
        assertEquals(ImportErrorKind.Source, classifyImportError("无法读取来源，请重新分享或重新选择"))
        assertEquals(ImportErrorKind.Source, classifyImportError("SecurityException: permission denied"))
    }

    @Test
    fun `取消归类为 Canceled`() {
        assertEquals(ImportErrorKind.Canceled, classifyImportError("用户取消"))
        assertEquals(ImportErrorKind.Canceled, classifyImportError("cancelled"))
    }

    @Test
    fun `读写错误归类为 Io`() {
        assertEquals(ImportErrorKind.Io, classifyImportError("文件读写失败"))
        assertEquals(ImportErrorKind.Io, classifyImportError("IOException: write failed"))
    }

    @Test
    fun `未知与空保留通用`() {
        assertEquals(ImportErrorKind.Generic, classifyImportError("某种未知异常"))
        assertEquals(ImportErrorKind.Generic, classifyImportError(null))
        assertEquals(ImportErrorKind.Generic, classifyImportError(""))
    }

    @Test
    fun `每类都有独立文案资源`() {
        val res = ImportErrorKind.entries.map { it.messageRes() }
        assertEquals(res.size, res.toSet().size)
    }
}
