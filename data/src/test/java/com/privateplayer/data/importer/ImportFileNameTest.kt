package com.privateplayer.data.importer

import com.privateplayer.data.repository.ImportRepositoryImpl
import com.privateplayer.data.repository.boundedImportName
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportFileNameTest {
    @Test
    fun `超长多字节文件名限制字节数且保留扩展名`() {
        val name = boundedImportName("影".repeat(120) + ".mp4")
        assertTrue(name.toByteArray(Charsets.UTF_8).size <= 220)
        assertTrue(name.endsWith(".mp4"))
        assertFalse(name.contains('/'))
    }

    @Test
    fun `无扩展名视频按来源类型识别其他文件原样保存`() {
        assertTrue(ImportRepositoryImpl.isVideo("没有后缀", "video/mp4"))
        assertTrue(ImportRepositoryImpl.isVideo("movie.MKV", null))
        assertFalse(ImportRepositoryImpl.isVideo("subtitle.srt", "application/octet-stream"))
        assertFalse(ImportRepositoryImpl.isVideo("subtitle.srt", "video/*"))
    }
}
