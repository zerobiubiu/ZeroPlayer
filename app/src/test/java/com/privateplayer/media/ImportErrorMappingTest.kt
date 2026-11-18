package com.privateplayer.media

import com.privateplayer.domain.R
import com.privateplayer.domain.model.ImportErrorKind
import com.privateplayer.domain.model.classifyImportError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 导入失败原因分类（F-12）：原始错误映射为可行动文案，未知保留通用。
 */
class ImportErrorMappingTest {

    @Test
    fun `空间不足归类为 Space`() {
        assertEquals(ImportErrorKind.Space, classifyImportError("手机存储空间不足", false))
        assertEquals(ImportErrorKind.Space, classifyImportError("ENOSPC: no space left", true))
    }

    @Test
    fun `来源失效归类为 Source`() {
        assertEquals(ImportErrorKind.Source, classifyImportError("无法读取来源，请重新分享或重新选择", false))
        assertEquals(ImportErrorKind.Source, classifyImportError("SecurityException: permission denied", false))
    }

    @Test
    fun `取消归类为 Canceled`() {
        assertEquals(ImportErrorKind.Canceled, classifyImportError("用户取消", false))
        assertEquals(ImportErrorKind.Canceled, classifyImportError("cancelled", true))
    }

    @Test
    fun `读写错误归类为 Io`() {
        assertEquals(ImportErrorKind.Io, classifyImportError("文件读写失败", false))
        assertEquals(ImportErrorKind.Io, classifyImportError("IOException: write failed", true))
    }

    @Test
    fun `未知与空保留通用`() {
        assertEquals(ImportErrorKind.Generic, classifyImportError("某种未知异常", false))
        assertEquals(ImportErrorKind.Generic, classifyImportError(null, true))
        assertEquals(ImportErrorKind.Generic, classifyImportError("", false))
    }

    @Test
    fun `旧网页暂存缺失只能重新上传`() {
        val raw = "上传暂存文件已丢失，请从网页重新上传"
        val kind = classifyImportError(raw, true)
        assertEquals(ImportErrorKind.WebStagingMissing, kind)
        assertEquals(R.string.import_error_web_staging_missing, kind.messageRes)
        assertFalse(kind.canRetry)
        assertNull(kind.diagnostic(raw))
        assertEquals(ImportErrorKind.Generic, classifyImportError(raw, false))
    }

    @Test
    fun `复制前和提交时的目录失败均可恢复原路径后重试`() {
        for (raw in listOf("目录不存在或不是目录", "目标目录已不存在，暂存已保留，请恢复目录后重试")) {
            for (web in listOf(false, true)) {
                val kind = classifyImportError(raw, web)
                assertEquals(ImportErrorKind.TargetDirectoryMissing, kind)
                assertEquals(R.string.import_error_target_directory_missing, kind.messageRes)
                assertTrue(kind.canRetry)
                assertNull(kind.diagnostic(raw))
            }
        }
    }

    @Test
    fun `丢失不存在或暂存不能泛化成已知故障`() {
        for (raw in listOf("导入暂存不存在", "正式目标已不存在或身份变化，唯一暂存已保留", "提交日志已丢失",
            "目录不存在或不是目录：其他原因", "目标位置已有同名文件，暂存已保留，请处理重名后重试")) {
            val kind = classifyImportError(raw, true)
            assertEquals(ImportErrorKind.Generic, kind)
            assertTrue(kind.canRetry)
            assertEquals(raw, kind.diagnostic(raw))
        }
        val canceled = classifyImportError("已取消处理，暂存文件仍保留，可重试", false)
        assertEquals(ImportErrorKind.Canceled, canceled)
        assertTrue(canceled.canRetry)
    }

    @Test
    fun `诊断不显示私有路径授权地址和口令`() {
        for (raw in listOf("读写失败：/data/user/0/test", "content://test/secret-file", "token=example")) {
            assertNull(classifyImportError(raw, true).diagnostic(raw))
        }
    }

    @Test
    fun `每类都有独立文案资源`() {
        val res = ImportErrorKind.entries.map { it.messageRes }
        assertEquals(res.size, res.toSet().size)
    }
}
